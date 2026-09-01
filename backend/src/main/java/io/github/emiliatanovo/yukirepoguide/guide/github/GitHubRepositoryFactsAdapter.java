package io.github.emiliatanovo.yukirepoguide.guide.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.emiliatanovo.yukirepoguide.guide.application.GitHubSourceException;
import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryFactsSource;
import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryReleaseSource;
import io.github.emiliatanovo.yukirepoguide.guide.application.ReadmeContentUnsupportedException;
import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryReadmeSource;
import io.github.emiliatanovo.yukirepoguide.guide.application.ReleaseHistoryUnsupportedException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryFacts;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryLanguageBytes;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRelease;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleaseAsset;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleases;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubRepositoryFactsAdapter
		implements RepositoryFactsSource, RepositoryReadmeSource, RepositoryReleaseSource {

	private static final String API_BASE_URL = "https://api.github.com";
	private static final long MAX_README_BYTES = 1024L * 1024L;
	private static final int MAX_ENCODED_README_CHARACTERS = 1_500_000;
	private static final Pattern REPOSITORY_REDIRECT_PATH =
			Pattern.compile("^/repos/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$");
	private static final Pattern NEXT_LINK = Pattern.compile(
			"<([^>]+)>;\\s*rel=(?:\\\"next\\\"|next)(?=\\s*(?:;|,|$))");
	private static final int RELEASES_PER_PAGE = 100;
	private static final int MAX_RELEASE_PAGES = 10;
	private final RestClient restClient;
	private final GitHubRateLimitGate rateLimitGate;
	private final Clock clock;

	GitHubRepositoryFactsAdapter(
			RestClient.Builder builder,
			GitHubProperties properties,
			GitHubRateLimitGate rateLimitGate,
			Clock clock,
			ClientHttpRequestFactory requestFactory) {
		if (requestFactory != null) {
			builder.requestFactory(requestFactory);
		}
		this.restClient = builder
				.baseUrl(API_BASE_URL)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
				.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.defaultHeader("X-GitHub-Api-Version", "2026-03-10")
				.defaultHeader(HttpHeaders.USER_AGENT, "Yuki-RepoGuide/0")
				.build();
		this.rateLimitGate = rateLimitGate;
		this.clock = clock;
	}

	@Override
	public RepositoryFacts fetchMetadata(RepositoryRef repositoryRef) {
		GitHubRepositoryResponse response = fetchMetadataResponse(repositoryRef, true);
		if (response == null) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		if (response.owner() == null
				|| response.owner().login() == null
				|| response.owner().login().isBlank()
				|| response.name() == null
				|| response.name().isBlank()
				|| response.createdAt() == null
				|| response.stars() == null
				|| response.stars() < 0) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		return new RepositoryFacts(
				new RepositoryRef(response.owner().login(), response.name()),
				response.description(),
				response.stars(),
				response.createdAt(),
				response.pushedAt(),
				safeExternalUrl(response.homepage()));
	}

	@Override
	public Optional<RepositoryReadme> fetchReadme(RepositoryRef repositoryRef) {
		GitHubReadmeResponse response;
		try {
			response = execute(() -> restClient.get()
					.uri("/repos/{owner}/{repository}/readme",
							repositoryRef.owner(), repositoryRef.name())
					.retrieve()
					.onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
							(request, upstreamResponse) -> {
								throw new ReadmeNotFoundException();
							})
					.onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
						throw classify(upstreamResponse.getStatusCode(), upstreamResponse.getHeaders());
					})
					.body(GitHubReadmeResponse.class));
		}
		catch (ReadmeNotFoundException exception) {
			return Optional.empty();
		}

		if (response == null
				|| response.path() == null
				|| response.path().isBlank()
				|| response.sha() == null
				|| response.sha().isBlank()
				|| safeGitHubHtmlUrl(response.htmlUrl()) == null) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		return Optional.of(new RepositoryReadme(
				response.path(),
				response.sha(),
				response.htmlUrl(),
				decodeReadme(response)));
	}

	@Override
	public RepositoryReleases fetchReleases(RepositoryRef repositoryRef) {
		List<RepositoryRelease> releases = new ArrayList<>();
		for (int page = 1; page <= MAX_RELEASE_PAGES; page++) {
			int requestedPage = page;
			ResponseEntity<List<GitHubReleaseResponse>> response = execute(() -> restClient.get()
					.uri("/repos/{owner}/{repository}/releases?per_page={perPage}&page={page}",
							repositoryRef.owner(),
							repositoryRef.name(),
							RELEASES_PER_PAGE,
							requestedPage)
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
						throw classify(upstreamResponse.getStatusCode(), upstreamResponse.getHeaders());
					})
					.toEntity(new ParameterizedTypeReference<>() {
					}));
			List<GitHubReleaseResponse> pageItems = response.getBody();
			if (pageItems == null || pageItems.size() > RELEASES_PER_PAGE) {
				throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
			}
			pageItems.stream()
					.map(this::repositoryRelease)
					.forEach(releases::add);
			if (!hasNextPage(response.getHeaders(), repositoryRef, page)) {
				return new RepositoryReleases(releases);
			}
		}
		throw new ReleaseHistoryUnsupportedException();
	}

	private boolean hasNextPage(
			HttpHeaders headers,
			RepositoryRef repositoryRef,
			int currentPage) {
		for (String header : headers.getOrEmpty(HttpHeaders.LINK)) {
			Matcher matcher = NEXT_LINK.matcher(header);
			if (matcher.find()) {
				URI next = safeNextReleasePage(matcher.group(1), repositoryRef, currentPage + 1);
				return next != null;
			}
		}
		return false;
	}

	private URI safeNextReleasePage(
			String rawUrl,
			RepositoryRef repositoryRef,
			int expectedPage) {
		try {
			URI uri = URI.create(rawUrl);
			String expectedPath = "/repos/" + repositoryRef.owner() + "/"
					+ repositoryRef.name() + "/releases";
			if (!"https".equalsIgnoreCase(uri.getScheme())
					|| !"api.github.com".equalsIgnoreCase(uri.getHost())
					|| uri.getPort() != -1
					|| uri.getUserInfo() != null
					|| !expectedPath.equals(uri.getPath())
					|| !queryContains(uri.getRawQuery(), "per_page", String.valueOf(RELEASES_PER_PAGE))
					|| !queryContains(uri.getRawQuery(), "page", String.valueOf(expectedPage))) {
				throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
			}
			return uri;
		}
		catch (IllegalArgumentException exception) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
	}

	private boolean queryContains(String rawQuery, String expectedName, String expectedValue) {
		if (rawQuery == null) {
			return false;
		}
		return List.of(rawQuery.split("&")).stream()
				.map(parameter -> parameter.split("=", 2))
				.anyMatch(parameter -> parameter.length == 2
						&& expectedName.equals(parameter[0])
						&& expectedValue.equals(parameter[1]));
	}

	private RepositoryRelease repositoryRelease(GitHubReleaseResponse response) {
		if (response == null
				|| response.id() == null
				|| response.id() <= 0
				|| response.draft() == null
				|| response.prerelease() == null) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		if (response.draft()) {
			return new RepositoryRelease(
					response.id(), response.name(), response.tagName(), response.htmlUrl(),
					response.publishedAt(), true, response.prerelease(), 0, 0, List.of());
		}
		String releaseUrl = safeGitHubHtmlUrl(response.htmlUrl());
		if (response.tagName() == null
				|| response.tagName().isBlank()
				|| releaseUrl == null
				|| response.publishedAt() == null
				|| response.assets() == null) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		List<RepositoryReleaseAsset> validAssets = response.assets().stream()
				.map(this::repositoryReleaseAsset)
				.filter(Optional::isPresent)
				.map(Optional::orElseThrow)
				.toList();
		return new RepositoryRelease(
				response.id(),
				response.name(),
				response.tagName(),
				releaseUrl,
				response.publishedAt(),
				false,
				response.prerelease(),
				response.assets().size(),
				response.assets().size() - validAssets.size(),
				validAssets);
	}

	private Optional<RepositoryReleaseAsset> repositoryReleaseAsset(
			GitHubReleaseAssetResponse response) {
		if (response == null
				|| response.id() == null
				|| response.id() <= 0
				|| response.name() == null
				|| response.name().isBlank()
				|| response.size() == null
				|| response.size() < 0
				|| !"uploaded".equals(response.state())) {
			return Optional.empty();
		}
		String downloadUrl = safeGitHubHtmlUrl(response.browserDownloadUrl());
		return downloadUrl == null
				? Optional.empty()
				: Optional.of(new RepositoryReleaseAsset(
						response.id(), response.name(), response.size(), downloadUrl));
	}

	private String decodeReadme(GitHubReadmeResponse response) {
		if (response.size() == null
				|| response.size() < 0
				|| response.size() > MAX_README_BYTES
				|| !"base64".equals(response.encoding())
				|| response.content() == null
				|| response.content().length() > MAX_ENCODED_README_CHARACTERS) {
			throw new ReadmeContentUnsupportedException();
		}
		try {
			String compactContent = response.content().replaceAll("\\s+", "");
			byte[] decoded = Base64.getDecoder().decode(compactContent);
			if (decoded.length != response.size() || decoded.length > MAX_README_BYTES) {
				throw new ReadmeContentUnsupportedException();
			}
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(decoded))
					.toString();
		}
		catch (IllegalArgumentException | CharacterCodingException exception) {
			throw new ReadmeContentUnsupportedException(
					"README content cannot be decoded as base64 UTF-8", exception);
		}
	}

	private String safeExternalUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return null;
		}
		try {
			URI uri = URI.create(rawUrl);
			boolean supportedScheme = "https".equalsIgnoreCase(uri.getScheme())
					|| "http".equalsIgnoreCase(uri.getScheme());
			return supportedScheme && uri.getHost() != null && uri.getUserInfo() == null
					? uri.toString()
					: null;
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private String safeGitHubHtmlUrl(String rawUrl) {
		String safeUrl = safeExternalUrl(rawUrl);
		if (safeUrl == null) {
			return null;
		}
		URI uri = URI.create(safeUrl);
		return "https".equalsIgnoreCase(uri.getScheme())
				&& "github.com".equalsIgnoreCase(uri.getHost())
				&& uri.getPort() == -1
				? safeUrl
				: null;
	}

	private GitHubRepositoryResponse fetchMetadataResponse(
			RepositoryRef repositoryRef,
			boolean allowRedirect) {
		try {
			return execute(() -> restClient.get()
				.uri("/repos/{owner}/{repository}", repositoryRef.owner(), repositoryRef.name())
				.retrieve()
				.onStatus(HttpStatusCode::is3xxRedirection, (request, upstreamResponse) -> {
					throw new GitHubRedirectException(upstreamResponse.getHeaders().getLocation());
				})
				.onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
					throw classify(upstreamResponse.getStatusCode(), upstreamResponse.getHeaders());
				})
				.body(GitHubRepositoryResponse.class));
		}
		catch (GitHubRedirectException redirect) {
			if (!allowRedirect) {
				throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
			}
			return fetchMetadataResponse(validatedRedirectRef(redirect.location()), false);
		}
	}

	@Override
	public RepositoryLanguageBytes fetchLanguages(RepositoryRef repositoryRef) {
		Map<String, Long> languages = execute(() -> restClient.get()
				.uri("/repos/{owner}/{repository}/languages",
						repositoryRef.owner(), repositoryRef.name())
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
					throw classify(upstreamResponse.getStatusCode(), upstreamResponse.getHeaders());
				})
				.body(new ParameterizedTypeReference<>() {
				}));
		if (languages == null) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		boolean malformed = languages.entrySet().stream().anyMatch(entry ->
				entry.getKey() == null
						|| entry.getKey().isBlank()
						|| entry.getValue() == null
						|| entry.getValue() < 0);
		if (malformed) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		return new RepositoryLanguageBytes(languages);
	}

	private <T> T execute(Supplier<T> request) {
		checkRateLimitGate();
		try {
			return request.get();
		}
		catch (GitHubSourceException exception) {
			throw exception;
		}
		catch (ResourceAccessException exception) {
			GuideErrorCode code = causedByTimeout(exception)
					? GuideErrorCode.GITHUB_TIMEOUT
					: GuideErrorCode.GITHUB_UPSTREAM_FAILURE;
			throw new GitHubSourceException(code);
		}
		catch (RestClientException exception) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
	}

	private boolean causedByTimeout(Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof SocketTimeoutException
					|| current instanceof HttpTimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private RepositoryRef validatedRedirectRef(URI location) {
		if (location == null) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		URI resolved = URI.create(API_BASE_URL).resolve(location);
		if (!"https".equalsIgnoreCase(resolved.getScheme())
				|| !"api.github.com".equalsIgnoreCase(resolved.getHost())
				|| resolved.getPort() != -1
				|| resolved.getUserInfo() != null
				|| resolved.getRawQuery() != null
				|| resolved.getRawFragment() != null
				|| resolved.getRawPath().contains("%")) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		Matcher path = REPOSITORY_REDIRECT_PATH.matcher(resolved.getRawPath());
		if (!path.matches()) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		if (isDotSegment(path.group(1)) || isDotSegment(path.group(2))) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
		return new RepositoryRef(path.group(1), path.group(2));
	}

	private boolean isDotSegment(String value) {
		return ".".equals(value) || "..".equals(value);
	}

	private void checkRateLimitGate() {
		Long retryAfterSeconds = rateLimitGate.retryAfterSeconds(clock);
		if (retryAfterSeconds != null) {
			throw new GitHubSourceException(
					GuideErrorCode.GITHUB_RATE_LIMITED, retryAfterSeconds);
		}
	}

	private GitHubSourceException classify(HttpStatusCode status, HttpHeaders headers) {
		Long reliableRetryAfter = reliableRetryAfterSeconds(headers);
		if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
			return reliableRetryAfter == null
					? new GitHubSourceException(GuideErrorCode.GITHUB_RATE_LIMITED)
					: rateLimited(reliableRetryAfter);
		}
		if (status.value() == HttpStatus.FORBIDDEN.value() && reliableRetryAfter != null) {
			return rateLimited(reliableRetryAfter);
		}
		if (status.value() == HttpStatus.NOT_FOUND.value()) {
			return new GitHubSourceException(GuideErrorCode.REPOSITORY_NOT_ACCESSIBLE);
		}
		if (status.value() == HttpStatus.UNAUTHORIZED.value()
				|| status.value() == HttpStatus.FORBIDDEN.value()) {
			return new GitHubSourceException(GuideErrorCode.GITHUB_SERVICE_UNAVAILABLE);
		}
		return new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
	}

	private GitHubSourceException rateLimited(long retryAfterSeconds) {
		rateLimitGate.blockFor(Duration.ofSeconds(retryAfterSeconds), clock);
		Long remaining = rateLimitGate.retryAfterSeconds(clock);
		return new GitHubSourceException(GuideErrorCode.GITHUB_RATE_LIMITED, remaining);
	}

	private Long reliableRetryAfterSeconds(HttpHeaders headers) {
		String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
		Long parsedRetryAfter = parsePositiveLong(retryAfter);
		if (parsedRetryAfter != null) {
			return parsedRetryAfter;
		}
		Long httpDateRetryAfter = secondsUntilHttpDate(retryAfter);
		if (httpDateRetryAfter != null) {
			return httpDateRetryAfter;
		}

		if (!"0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
			return null;
		}
		Long resetEpochSeconds = parsePositiveLong(headers.getFirst("X-RateLimit-Reset"));
		if (resetEpochSeconds == null) {
			return null;
		}
		return secondsUntil(Instant.ofEpochSecond(resetEpochSeconds));
	}

	private Long secondsUntilHttpDate(String value) {
		if (value == null) {
			return null;
		}
		try {
			return secondsUntil(ZonedDateTime.parse(
					value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
		}
		catch (DateTimeParseException exception) {
			return null;
		}
	}

	private Long secondsUntil(Instant deadline) {
		long remainingMillis = Duration.between(clock.instant(), deadline).toMillis();
		return remainingMillis > 0 ? (remainingMillis + 999) / 1_000 : null;
	}

	private Long parsePositiveLong(String value) {
		if (value == null) {
			return null;
		}
		try {
			long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : null;
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private record GitHubRepositoryResponse(
			String name,
			GitHubOwner owner,
			String description,
			String homepage,
			@JsonProperty("stargazers_count") Long stars,
			@JsonProperty("created_at") Instant createdAt,
			@JsonProperty("pushed_at") Instant pushedAt) {
	}

	private record GitHubReadmeResponse(
			String path,
			String sha,
			@JsonProperty("html_url") String htmlUrl,
			Long size,
			String encoding,
			String content) {
	}

	private record GitHubReleaseResponse(
			Long id,
			String name,
			@JsonProperty("tag_name") String tagName,
			@JsonProperty("html_url") String htmlUrl,
			Boolean draft,
			Boolean prerelease,
			@JsonProperty("published_at") Instant publishedAt,
			List<GitHubReleaseAssetResponse> assets) {
	}

	private record GitHubReleaseAssetResponse(
			Long id,
			String name,
			String state,
			Long size,
			@JsonProperty("browser_download_url") String browserDownloadUrl) {
	}

	private record GitHubOwner(String login) {
	}

	private static final class GitHubRedirectException extends RuntimeException {

		private final URI location;

		private GitHubRedirectException(URI location) {
			this.location = location;
		}

		private URI location() {
			return location;
		}
	}

	private static final class ReadmeNotFoundException extends RuntimeException {
	}
}
