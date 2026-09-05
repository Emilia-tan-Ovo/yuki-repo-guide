package io.github.emiliatanovo.yukirepoguide.guide.github;

import io.github.emiliatanovo.yukirepoguide.guide.application.GitHubSourceException;
import io.github.emiliatanovo.yukirepoguide.guide.application.ReadmeContentUnsupportedException;
import io.github.emiliatanovo.yukirepoguide.guide.application.ReleaseHistoryUnsupportedException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class GitHubRepositoryFactsAdapterTest {

	@Test
	void fetchesCompleteReleasePagesAndExcludesUnsafeAssets() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo(
				"https://api.github.com/repos/octo/example/releases?per_page=100&page=1"))
				.andRespond(withSuccess("""
						[
						  {
						    "id": 20,
						    "name": "Version 2",
						    "tag_name": "v2.0",
						    "html_url": "https://github.com/octo/example/releases/tag/v2.0",
						    "draft": false,
						    "prerelease": false,
						    "published_at": "2026-06-01T00:00:00Z",
						    "assets": [
						      {
						        "id": 201,
						        "name": "example.zip",
						        "state": "uploaded",
						        "size": 1024,
						        "browser_download_url": "https://github.com/octo/example/releases/download/v2.0/example.zip"
						      },
						      {
						        "id": 202,
						        "name": "unfinished.zip",
						        "state": "starter",
						        "size": 10,
						        "browser_download_url": "https://github.com/octo/example/releases/download/v2.0/unfinished.zip"
						      },
						      {
						        "id": 203,
						        "name": "http.zip",
						        "state": "uploaded",
						        "size": 10,
						        "browser_download_url": "http://github.com/octo/example/releases/download/v2.0/http.zip"
						      },
						      {
						        "id": 204,
						        "name": "other-host.zip",
						        "state": "uploaded",
						        "size": 10,
						        "browser_download_url": "https://downloads.example.com/other-host.zip"
						      },
						      {
						        "id": 205,
						        "name": "user-info.zip",
						        "state": "uploaded",
						        "size": 10,
						        "browser_download_url": "https://user@github.com/octo/example/releases/download/v2.0/user-info.zip"
						      },
						      {
						        "id": 206,
						        "name": "custom-port.zip",
						        "state": "uploaded",
						        "size": 10,
						        "browser_download_url": "https://github.com:8443/octo/example/releases/download/v2.0/custom-port.zip"
						      }
						    ]
						  }
						]
						""", MediaType.APPLICATION_JSON)
						.header(HttpHeaders.LINK,
								"<https://api.github.com/repos/octo/example/releases?per_page=100&page=2>; rel=\"next\""));
		server.expect(requestTo(
				"https://api.github.com/repos/octo/example/releases?per_page=100&page=2"))
				.andRespond(withSuccess("""
						[
						  {
						    "id": 21,
						    "name": null,
						    "tag_name": "v3.0-beta",
						    "html_url": "https://github.com/octo/example/releases/tag/v3.0-beta",
						    "draft": false,
						    "prerelease": true,
						    "published_at": "2026-07-01T00:00:00Z",
						    "assets": []
						  }
						]
						""", MediaType.APPLICATION_JSON));

		var result = adapter.fetchReleases(new RepositoryRef("octo", "example"));

		assertThat(result.items()).hasSize(2);
		assertThat(result.items().getFirst().reportedAssetCount()).isEqualTo(6);
		assertThat(result.items().getFirst().excludedAssetCount()).isEqualTo(5);
		assertThat(result.items().getFirst().assets()).singleElement()
				.satisfies(asset -> {
					assertThat(asset.name()).isEqualTo("example.zip");
					assertThat(asset.sizeBytes()).isEqualTo(1024);
				});
		assertThat(result.items().getLast().prerelease()).isTrue();
		server.verify();
	}

	@Test
	void rejectsReleaseHistoryBeyondOneThousandItemsWithoutFollowingTheNextUrl() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		for (int page = 1; page <= 10; page++) {
			server.expect(requestTo(
					"https://api.github.com/repos/octo/example/releases?per_page=100&page=" + page))
					.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
							.header(HttpHeaders.LINK,
									"<https://api.github.com/repos/octo/example/releases?per_page=100&page="
											+ (page + 1) + ">; rel=\"next\""));
		}

		ReleaseHistoryUnsupportedException failure = catchThrowableOfType(
				ReleaseHistoryUnsupportedException.class,
				() -> adapter.fetchReleases(new RepositoryRef("octo", "example")));

		assertThat(failure).isNotNull();
		server.verify();
	}

	@Test
	void rejectsAnUntrustedReleasePaginationLinkWithoutFollowingIt() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo(
				"https://api.github.com/repos/octo/example/releases?per_page=100&page=1"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
						.header(HttpHeaders.LINK,
								"<https://evil.example.com/releases?page=2>; rel=\"next\""));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchReleases(new RepositoryRef("octo", "example")));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		server.verify();
	}

	@Test
	void doesNotTreatAnExtendedRelationNameAsTheNextPage() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo(
				"https://api.github.com/repos/octo/example/releases?per_page=100&page=1"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
						.header(HttpHeaders.LINK,
								"<https://api.github.com/repos/octo/example/releases?per_page=100&page=2>; rel=\"next-page\""));

		var result = adapter.fetchReleases(new RepositoryRef("octo", "example"));

		assertThat(result.items()).isEmpty();
		server.verify();
	}

	@Test
	void rejectsAMalformedPublishedReleaseAsAnUpstreamFailure() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo(
				"https://api.github.com/repos/octo/example/releases?per_page=100&page=1"))
				.andRespond(withSuccess("""
						[{
						  "id": 1,
						  "tag_name": "v1.0.0",
						  "html_url": "https://github.com/octo/example/releases/tag/v1.0.0",
						  "draft": false,
						  "prerelease": false,
						  "published_at": null,
						  "assets": []
						}]
						""", MediaType.APPLICATION_JSON));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchReleases(new RepositoryRef("octo", "example")));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		server.verify();
	}

	@Test
	void fetchesAndDecodesReadmeWithoutFollowingLinksInItsContent() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		String markdown = "## Demo\n[Try it online](https://demo.example.com)\n";
		String encoded = Base64.getEncoder().encodeToString(
				markdown.getBytes(StandardCharsets.UTF_8));
		server.expect(requestTo("https://api.github.com/repos/octo/example/readme"))
				.andRespond(withSuccess("""
						{
						  "path": "README.md",
						  "sha": "abc123",
						  "html_url": "https://github.com/octo/example/blob/main/README.md",
						  "size": %d,
						  "encoding": "base64",
						  "content": "%s"
						}
						""".formatted(markdown.getBytes(StandardCharsets.UTF_8).length, encoded),
						MediaType.APPLICATION_JSON));

		var result = adapter.fetchReadme(new RepositoryRef("octo", "example"));

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().content()).isEqualTo(markdown);
		assertThat(result.orElseThrow().path()).isEqualTo("README.md");
		server.verify();
	}

	@Test
	void mapsReadmeNotFoundToAbsenceAfterMetadataWasConfirmed() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/example/readme"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		var result = adapter.fetchReadme(new RepositoryRef("octo", "example"));

		assertThat(result).isEmpty();
		server.verify();
	}

	@Test
	void rejectsReadmeThatExceedsTheDecodedOneMebibyteLimit() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/example/readme"))
				.andRespond(withSuccess("""
						{
						  "path": "README.md",
						  "sha": "abc123",
						  "html_url": "https://github.com/octo/example/blob/main/README.md",
						  "size": 1048577,
						  "encoding": "base64",
						  "content": "YQ=="
						}
						""", MediaType.APPLICATION_JSON));

		ReadmeContentUnsupportedException failure = catchThrowableOfType(
				ReadmeContentUnsupportedException.class,
				() -> adapter.fetchReadme(new RepositoryRef("octo", "example")));

		assertThat(failure).isNotNull();
		server.verify();
	}

	@Test
	void rejectsReadmeThatIsNotValidUtf8() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		String encoded = Base64.getEncoder().encodeToString(new byte[] {(byte) 0xC3, 0x28});
		server.expect(requestTo("https://api.github.com/repos/octo/example/readme"))
				.andRespond(withSuccess("""
						{
						  "path": "README.md",
						  "sha": "abc123",
						  "html_url": "https://github.com/octo/example/blob/main/README.md",
						  "size": 2,
						  "encoding": "base64",
						  "content": "%s"
						}
						""".formatted(encoded), MediaType.APPLICATION_JSON));

		ReadmeContentUnsupportedException failure = catchThrowableOfType(
				ReadmeContentUnsupportedException.class,
				() -> adapter.fetchReadme(new RepositoryRef("octo", "example")));

		assertThat(failure).isNotNull();
		server.verify();
	}

	@Test
	void treatsMalformedMetadataAsAnUpstreamFailure() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andRespond(withSuccess("""
						{
						  "name": "example",
						  "owner": null,
						  "stargazers_count": 1,
						  "created_at": "2024-01-10T08:30:00Z"
						}
						""", MediaType.APPLICATION_JSON));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "example")));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		server.verify();
	}

	@Test
	void rejectsMetadataWhenStarsAreMissing() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andRespond(withSuccess("""
						{
						  "name": "example",
						  "owner": {"login": "octo"},
						  "created_at": "2024-01-10T08:30:00Z"
						}
						""", MediaType.APPLICATION_JSON));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "example")));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		server.verify();
	}

	@ParameterizedTest
	@MethodSource("upstreamStatusMappings")
	void mapsUpstreamStatusWithoutExposingTheResponse(
			HttpStatus status,
			GuideErrorCode expectedCode) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andRespond(withStatus(status).body("sensitive upstream body"));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "example")));

		assertThat(failure.code()).isEqualTo(expectedCode);
		assertThat(failure.getMessage()).doesNotContain("sensitive upstream body");
		server.verify();
	}

	@Test
	void rejectsARedirectThatLeavesTheFixedGitHubHost() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY)
						.header(HttpHeaders.LOCATION, "https://example.com/repos/octo/example"));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "example")));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		server.verify();
	}

	@Test
	void rejectsARedirectWithDotSegments() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY)
						.header(HttpHeaders.LOCATION, "https://api.github.com/repos/../.."));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "example")));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		server.verify();
	}

	@Test
	void reopensTheGlobalGateAfterItsDeadline() {
		var gate = new GitHubRateLimitGate();
		Clock startedAt = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
		gate.blockFor(Duration.ofSeconds(120), startedAt);

		assertThat(gate.retryAfterSeconds(startedAt)).isEqualTo(120);
		assertThat(gate.retryAfterSeconds(Clock.offset(startedAt, Duration.ofSeconds(121))))
				.isNull();
	}

	@Test
	void acceptsAStandardHttpDateInRetryAfter() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = new GitHubRepositoryFactsAdapter(
				builder,
				new GitHubProperties(
						"test-token", Duration.ofSeconds(3), Duration.ofSeconds(8)),
				new GitHubRateLimitGate(),
				Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
				null);
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
						.header(HttpHeaders.RETRY_AFTER,
								"Tue, 25 Aug 2026 00:02:00 GMT"));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "example")));

		assertThat(failure.retryAfterSeconds()).isEqualTo(120);
		server.verify();
	}

	@Test
	void followsOneControlledRepositoryRenameRedirect() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = new GitHubRepositoryFactsAdapter(
				builder,
				new GitHubProperties(
						"test-token", Duration.ofSeconds(3), Duration.ofSeconds(8)),
				new GitHubRateLimitGate(),
				Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
				null);
		server.expect(requestTo("https://api.github.com/repos/octo/old-name"))
				.andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY)
						.header(HttpHeaders.LOCATION,
								"https://api.github.com/repos/octo/new-name"));
		server.expect(requestTo("https://api.github.com/repos/octo/new-name"))
				.andRespond(withSuccess("""
						{
						  "name": "new-name",
						  "owner": {"login": "octo"},
						  "description": null,
						  "stargazers_count": 1,
						  "created_at": "2024-01-10T08:30:00Z",
						  "pushed_at": null
						}
						""", MediaType.APPLICATION_JSON));

		var result = adapter.fetchMetadata(new RepositoryRef("octo", "old-name"));

		assertThat(result.reference()).isEqualTo(new RepositoryRef("octo", "new-name"));
		assertThat(result.description()).isNull();
		assertThat(result.pushedAt()).isNull();
		server.verify();
	}

	@Test
	void rejectsASecondRepositoryRedirect() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/old-name"))
				.andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY)
						.header(HttpHeaders.LOCATION,
								"https://api.github.com/repos/octo/new-name"));
		server.expect(requestTo("https://api.github.com/repos/octo/new-name"))
				.andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY)
						.header(HttpHeaders.LOCATION,
								"https://api.github.com/repos/octo/old-name"));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "old-name")));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		server.verify();
	}

	@Test
	void classifiesATransportTimeoutWithoutLeakingTheClientException() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = new GitHubRepositoryFactsAdapter(
				builder,
				new GitHubProperties(
						"test-token", Duration.ofSeconds(3), Duration.ofSeconds(8)),
				new GitHubRateLimitGate(),
				Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
				null);
		server.expect(requestTo("https://api.github.com/repos/octo/example/languages"))
				.andRespond(withException(new SocketTimeoutException("read timed out")));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchLanguages(new RepositoryRef("octo", "example")));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_TIMEOUT);
		assertThat(failure.getMessage()).isEqualTo("GITHUB_TIMEOUT");
		server.verify();
	}

	@Test
	void opensAGlobalGateWhenGitHubReturnsARetryAfterLimit() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = new GitHubRepositoryFactsAdapter(
				builder,
				new GitHubProperties(
						"test-token", Duration.ofSeconds(3), Duration.ofSeconds(8)),
				new GitHubRateLimitGate(),
				Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
				null);
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
						.header(HttpHeaders.RETRY_AFTER, "120"));

		GitHubSourceException firstFailure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "example")));
		GitHubSourceException gatedFailure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchLanguages(new RepositoryRef("another", "repository")));

		assertThat(firstFailure.code()).isEqualTo(GuideErrorCode.GITHUB_RATE_LIMITED);
		assertThat(firstFailure.retryAfterSeconds()).isEqualTo(120);
		assertThat(gatedFailure.code()).isEqualTo(GuideErrorCode.GITHUB_RATE_LIMITED);
		assertThat(gatedFailure.retryAfterSeconds()).isEqualTo(120);
		server.verify();
	}

	@Test
	void doesNotInventAWaitOrOpenTheGateWithoutReliableLimitHeaders() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = adapter(builder, new GitHubRateLimitGate());
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(requestTo("https://api.github.com/repos/another/repository/languages"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		GitHubSourceException failure = catchThrowableOfType(
				GitHubSourceException.class,
				() -> adapter.fetchMetadata(new RepositoryRef("octo", "example")));
		var languages = adapter.fetchLanguages(new RepositoryRef("another", "repository"));

		assertThat(failure.code()).isEqualTo(GuideErrorCode.GITHUB_RATE_LIMITED);
		assertThat(failure.retryAfterSeconds()).isNull();
		assertThat(languages.bytesByLanguage()).isEmpty();
		server.verify();
	}

	@Test
	void fetchesLanguageBytesFromTheFixedLanguagesEndpoint() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var adapter = new GitHubRepositoryFactsAdapter(
				builder,
				new GitHubProperties(
						"test-token", Duration.ofSeconds(3), Duration.ofSeconds(8)),
				new GitHubRateLimitGate(),
				Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
				null);
		server.expect(requestTo("https://api.github.com/repos/octo/example/languages"))
				.andRespond(withSuccess("""
						{"Java":80000,"Kotlin":15000,"Shell":5000}
						""", MediaType.APPLICATION_JSON));

		var result = adapter.fetchLanguages(new RepositoryRef("octo", "example"));

		assertThat(result.bytesByLanguage()).containsExactlyInAnyOrderEntriesOf(
				java.util.Map.of("Java", 80_000L, "Kotlin", 15_000L, "Shell", 5_000L));
		server.verify();
	}

	@Test
	void fetchesRepositoryMetadataFromTheFixedGitHubEndpoint() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var properties = new GitHubProperties(
				"test-token", Duration.ofSeconds(3), Duration.ofSeconds(8));
		var adapter = new GitHubRepositoryFactsAdapter(
				builder,
				properties,
				new GitHubRateLimitGate(),
				Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
				null);
		server.expect(requestTo("https://api.github.com/repos/octo/example"))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
				.andExpect(header("X-GitHub-Api-Version", "2026-03-10"))
				.andExpect(header(HttpHeaders.USER_AGENT, "Yuki-RepoGuide/0"))
				.andRespond(withSuccess("""
						{
						  "name": "renamed-example",
						  "owner": {"login": "octocat"},
						  "description": "A traceable repository guide",
						  "homepage": "https://example.com/project",
						  "stargazers_count": 321,
						  "created_at": "2024-01-10T08:30:00Z",
						  "pushed_at": "2026-08-24T12:00:00Z"
						}
						""", MediaType.APPLICATION_JSON));

		var result = adapter.fetchMetadata(new RepositoryRef("octo", "example"));

		assertThat(result.reference()).isEqualTo(new RepositoryRef("octocat", "renamed-example"));
		assertThat(result.description()).isEqualTo("A traceable repository guide");
		assertThat(result.stars()).isEqualTo(321);
		assertThat(result.createdAt()).isEqualTo(Instant.parse("2024-01-10T08:30:00Z"));
		assertThat(result.pushedAt()).isEqualTo(Instant.parse("2026-08-24T12:00:00Z"));
		assertThat(result.projectWebsiteUrl()).isEqualTo("https://example.com/project");
		server.verify();
	}

	private GitHubRepositoryFactsAdapter adapter(
			RestClient.Builder builder,
			GitHubRateLimitGate gate) {
		return new GitHubRepositoryFactsAdapter(
				builder,
				new GitHubProperties(
						"test-token", Duration.ofSeconds(3), Duration.ofSeconds(8)),
				gate,
				Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
				null);
	}

	private static Stream<Arguments> upstreamStatusMappings() {
		return Stream.of(
				Arguments.of(HttpStatus.NOT_FOUND, GuideErrorCode.REPOSITORY_NOT_ACCESSIBLE),
				Arguments.of(HttpStatus.UNAUTHORIZED, GuideErrorCode.GITHUB_SERVICE_UNAVAILABLE),
				Arguments.of(HttpStatus.FORBIDDEN, GuideErrorCode.GITHUB_SERVICE_UNAVAILABLE),
				Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, GuideErrorCode.GITHUB_UPSTREAM_FAILURE));
	}
}
