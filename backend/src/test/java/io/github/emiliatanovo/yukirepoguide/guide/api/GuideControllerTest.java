package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.application.GuideService;
import io.github.emiliatanovo.yukirepoguide.guide.application.GitHubSourceException;
import io.github.emiliatanovo.yukirepoguide.guide.application.OnlineExperienceRecognizer;
import io.github.emiliatanovo.yukirepoguide.guide.application.ReadmeContentUnsupportedException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryFacts;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryLanguageBytes;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import io.github.emiliatanovo.yukirepoguide.guide.github.GitHubRepositoryUrlParser;
import io.github.emiliatanovo.yukirepoguide.guide.support.FakeRepositoryFactsSource;
import io.github.emiliatanovo.yukirepoguide.guide.support.FakeRepositoryReadmeSource;
import io.github.emiliatanovo.yukirepoguide.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuideControllerTest {

	@Test
	void returnsReadmeCandidatesWithPlainEvidenceAndKeepsHomepageSeparate() throws Exception {
		readmeSource.returning(new RepositoryReadme(
				"README.md",
				"abc123",
				"https://github.com/Emilia-tan-Ovo/yuki-repo-guide/blob/main/README.md",
				"## Demo\n[Try it online](https://demo.example.com)"));

		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repository.projectWebsiteUrl")
						.value("https://project.example.com"))
				.andExpect(jsonPath("$.readme.status").value("AVAILABLE"))
				.andExpect(jsonPath("$.readme.candidates[0].label").value("Try it online"))
				.andExpect(jsonPath("$.readme.candidates[0].url")
						.value("https://demo.example.com"))
				.andExpect(jsonPath("$.readme.candidates[0].evidenceId")
						.value("readme-online-experience-1"))
				.andExpect(jsonPath("$.evidence.readme-online-experience-1.type")
						.value("README"))
				.andExpect(jsonPath("$.evidence.readme-online-experience-1.path")
						.value("README.md"))
				.andExpect(jsonPath("$.evidence.readme-online-experience-1.sha")
						.value("abc123"))
				.andExpect(jsonPath("$.evidence.readme-online-experience-1.context")
						.value("Try it online"))
				.andExpect(jsonPath("$.evidence.readme-online-experience-1.executableHtml")
						.doesNotExist());
	}

	@Test
	void keepsAnInitialReadmeFailureInsideTheSuccessfulGuideResponse() throws Exception {
		readmeSource.failingWith(new GitHubSourceException(GuideErrorCode.GITHUB_TIMEOUT));

		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readme.status").value("FAILED"))
				.andExpect(jsonPath("$.readme.failure.code").value("GITHUB_TIMEOUT"))
				.andExpect(jsonPath("$.readme.failure.retryable").value(true));
	}

	@Test
	void mapsUnsupportedReadmeOnExplicitRetryToUnprocessableContent() throws Exception {
		readmeSource.failingWith(new ReadmeContentUnsupportedException());

		mockMvc.perform(post("/api/guides/readme/retry")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("README_CONTENT_UNSUPPORTED"))
				.andExpect(jsonPath("$.retryable").value(false));
	}

	@Test
	void preservesRetryAfterWhenExplicitReadmeRetryIsRateLimited() throws Exception {
		readmeSource.failingWith(new GitHubSourceException(
				GuideErrorCode.GITHUB_RATE_LIMITED, 90L));

		mockMvc.perform(post("/api/guides/readme/retry")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "90"))
				.andExpect(jsonPath("$.code").value("GITHUB_RATE_LIMITED"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(90));
	}

	@Test
	void mapsMissingReadmeOnExplicitRetryToNotProvided() throws Exception {
		mockMvc.perform(post("/api/guides/readme/retry")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repository").doesNotExist())
				.andExpect(jsonPath("$.languages").doesNotExist())
				.andExpect(jsonPath("$.readme.status").value("NOT_PROVIDED"))
				.andExpect(jsonPath("$.evidence").isEmpty());
	}

	@ParameterizedTest
	@MethodSource("coreFailureMappings")
	void mapsCoreGitHubFailuresToThePublicProblemContract(
			GuideErrorCode code,
			int expectedStatus) throws Exception {
		factsSource.failingMetadataWith(new GitHubSourceException(code));

		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().is(expectedStatus))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value(code.name()));
	}

	@Test
	void returnsRetryAfterAndAStableCodeWhenLanguageRetryIsRateLimited() throws Exception {
		factsSource.failingLanguagesWith(new GitHubSourceException(
				GuideErrorCode.GITHUB_RATE_LIMITED, 120L));

		mockMvc.perform(post("/api/guides/languages/retry")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "120"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("GITHUB_RATE_LIMITED"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(120))
				.andExpect(jsonPath("$.detail").value("GitHub 暂时限制了请求，请稍后重试。"));
	}

	@Test
	void retriesOnlyTheLanguageRegionAndReturnsItsEvidence() throws Exception {
		factsSource.withLanguages(new RepositoryLanguageBytes(Map.of(
				"Java", 80L,
				"Kotlin", 20L)));

		mockMvc.perform(post("/api/guides/languages/retry")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repository").doesNotExist())
				.andExpect(jsonPath("$.languages.status").value("AVAILABLE"))
				.andExpect(jsonPath("$.languages.items[0].name").value("Java"))
				.andExpect(jsonPath("$.languages.items[0].percentage").value(80.0))
				.andExpect(jsonPath("$.languages.evidenceId")
						.value("repository-languages"))
				.andExpect(jsonPath("$.evidence.repository-languages.totalBytes").value(100))
				.andExpect(jsonPath("$.evidence.repository-languages.languages[0].bytes")
						.value(80));
		assertThat(factsSource.metadataRequests()).isZero();
		assertThat(factsSource.languageRequests()).isEqualTo(1);
	}

	private MockMvc mockMvc;
	private FakeRepositoryFactsSource factsSource;
	private FakeRepositoryReadmeSource readmeSource;

	@BeforeEach
	void setUp() {
		var repositoryRef = new RepositoryRef("Emilia-tan-Ovo", "yuki-repo-guide");
		var repository = new RepositoryFacts(
				repositoryRef,
				"Evidence-first GitHub repository guide",
				123,
				Instant.parse("2026-08-01T00:00:00Z"),
				Instant.parse("2026-08-24T12:00:00Z"),
				"https://project.example.com");
		factsSource = FakeRepositoryFactsSource.withMetadata(repository);
		readmeSource = FakeRepositoryReadmeSource.withoutReadme();
		var guideService = new GuideService(
				new GitHubRepositoryUrlParser(),
				factsSource,
				readmeSource,
				new OnlineExperienceRecognizer());
		mockMvc = MockMvcBuilders.standaloneSetup(new GuideController(guideService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsRepositoryFactsAndAFailedLanguageRegionAsAPartialGuide() throws Exception {
		factsSource.failingLanguagesWith(new GitHubSourceException(
				GuideErrorCode.GITHUB_TIMEOUT));

		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repository.description")
						.value("Evidence-first GitHub repository guide"))
				.andExpect(jsonPath("$.repository.stars").value(123))
				.andExpect(jsonPath("$.repository.createdAt")
						.value("2026-08-01T00:00:00Z"))
				.andExpect(jsonPath("$.repository.pushedAt")
						.value("2026-08-24T12:00:00Z"))
				.andExpect(jsonPath("$.repository.evidenceId")
						.value("repository-metadata"))
				.andExpect(jsonPath("$.languages.status").value("FAILED"))
				.andExpect(jsonPath("$.languages.failure.code").value("GITHUB_TIMEOUT"))
				.andExpect(jsonPath("$.languages.items").isEmpty())
				.andExpect(jsonPath("$.languages.evidenceId").doesNotExist())
				.andExpect(jsonPath("$.evidence.repository-metadata.source").value("GitHub"))
				.andExpect(jsonPath("$.evidence.repository-languages").doesNotExist());
	}

	@Test
	void returnsGroupedRepositoryInformationForAValidGitHubUrl() throws Exception {
		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.repository.owner").value("Emilia-tan-Ovo"))
				.andExpect(jsonPath("$.repository.name").value("yuki-repo-guide"))
				.andExpect(jsonPath("$.repository.canonicalUrl")
						.value("https://github.com/Emilia-tan-Ovo/yuki-repo-guide"));
	}

	@Test
	void returnsAReadableProblemDetailForAnUnsupportedRepositoryUrl() throws Exception {
		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://gitlab.com/example/project"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("仓库地址无效"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("目前仅支持 github.com 的仓库地址。"))
				.andExpect(jsonPath("$.code").value("INVALID_REPOSITORY_URL"))
				.andExpect(jsonPath("$.field").value("repositoryUrl"));
	}

	@Test
	void returnsTheSameProblemContractWhenRepositoryUrlIsBlank() throws Exception {
		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":" "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("仓库地址无效"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("请输入 GitHub 仓库地址。"))
				.andExpect(jsonPath("$.code").value("INVALID_REPOSITORY_URL"))
				.andExpect(jsonPath("$.field").value("repositoryUrl"));
	}

	private static Stream<Arguments> coreFailureMappings() {
		return Stream.of(
				Arguments.of(GuideErrorCode.REPOSITORY_NOT_ACCESSIBLE, 404),
				Arguments.of(GuideErrorCode.GITHUB_UPSTREAM_FAILURE, 502),
				Arguments.of(GuideErrorCode.GITHUB_SERVICE_UNAVAILABLE, 503),
				Arguments.of(GuideErrorCode.GITHUB_TIMEOUT, 504));
	}
}
