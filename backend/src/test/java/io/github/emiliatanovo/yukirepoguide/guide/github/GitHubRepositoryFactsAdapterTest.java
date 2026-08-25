package io.github.emiliatanovo.yukirepoguide.guide.github;

import io.github.emiliatanovo.yukirepoguide.guide.application.GitHubSourceException;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class GitHubRepositoryFactsAdapterTest {

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
