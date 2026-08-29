package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;

import io.github.emiliatanovo.yukirepoguide.guide.domain.InvalidRepositoryUrlException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageSectionStatus;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ProjectGuide;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeSectionStatus;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryFacts;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryLanguageBytes;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import io.github.emiliatanovo.yukirepoguide.guide.github.GitHubRepositoryUrlParser;
import io.github.emiliatanovo.yukirepoguide.guide.support.FakeRepositoryFactsSource;
import io.github.emiliatanovo.yukirepoguide.guide.support.FakeRepositoryReadmeSource;
import io.github.emiliatanovo.yukirepoguide.guide.support.FakeRepositoryUrlParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideServiceTest {

	@Test
	void keepsRepositoryFactsWhileUntrustedReadmeContentProducesNoCandidate() {
		RepositoryRef reference = new RepositoryRef("octo", "example");
		RepositoryFacts repository = new RepositoryFacts(
				reference,
				"Repository description",
				42,
				Instant.parse("2024-01-10T08:30:00Z"),
				Instant.parse("2026-08-24T12:00:00Z"),
				"https://project.example.com");
		var readmeSource = FakeRepositoryReadmeSource.withReadme(new RepositoryReadme(
				"README.md",
				"abc123",
				"https://github.com/octo/example/blob/main/README.md",
				"""
						[Project website](https://project.example.com)
						<script><a href="https://evil.example.com">Live Demo</a></script>
						<a hidden href="https://hidden.example.com">Online Demo</a>
						"""));
		GuideService service = new GuideService(
				new FakeRepositoryUrlParser(reference),
				FakeRepositoryFactsSource.withMetadata(repository),
				readmeSource,
				new OnlineExperienceRecognizer());

		ProjectGuide guide = service.createGuide("https://github.com/octo/example");

		assertThat(guide.repository()).isEqualTo(repository);
		assertThat(guide.readme().status()).isEqualTo(ReadmeSectionStatus.AVAILABLE);
		assertThat(guide.readme().candidates()).isEmpty();
		assertThat(readmeSource.requests()).isEqualTo(1);
	}

	@Test
	void returnsAnExplicitOnlineExperienceWithReadmeEvidence() {
		RepositoryRef reference = new RepositoryRef("octo", "example");
		RepositoryFacts repository = new RepositoryFacts(
				reference,
				"Repository description",
				42,
				Instant.parse("2024-01-10T08:30:00Z"),
				Instant.parse("2026-08-24T12:00:00Z"),
				"https://project.example.com");
		var readmeSource = FakeRepositoryReadmeSource.withReadme(new RepositoryReadme(
				"README.md",
				"abc123",
				"https://github.com/octo/example/blob/main/README.md",
				"[Live Demo](https://demo.example.com)"));
		GuideService service = new GuideService(
				new FakeRepositoryUrlParser(reference),
				FakeRepositoryFactsSource.withMetadata(repository),
				readmeSource,
				new OnlineExperienceRecognizer());

		ProjectGuide guide = service.createGuide("https://github.com/octo/example");

		assertThat(guide.repository().projectWebsiteUrl())
				.isEqualTo("https://project.example.com");
		assertThat(guide.readme().candidates()).singleElement()
				.satisfies(candidate -> {
					assertThat(candidate.label()).isEqualTo("Live Demo");
					assertThat(candidate.url()).isEqualTo("https://demo.example.com");
					assertThat(candidate.evidenceId()).isEqualTo("readme-online-experience-1");
				});
		assertThat(guide.evidence().get("readme-online-experience-1"))
				.isEqualTo(guide.readme().evidence().get("readme-online-experience-1"));
	}

	@Test
	void keepsLanguagesAvailableWhenReadmeFetchFails() {
		RepositoryRef reference = new RepositoryRef("octo", "example");
		var factsSource = FakeRepositoryFactsSource.withMetadata(repositoryFacts(reference))
				.withLanguages(new RepositoryLanguageBytes(Map.of("Java", 100L)));
		var readmeSource = FakeRepositoryReadmeSource.withoutReadme()
				.failingWith(new GitHubSourceException(GuideErrorCode.GITHUB_TIMEOUT));
		GuideService service = new GuideService(
				new FakeRepositoryUrlParser(reference),
				factsSource,
				readmeSource,
				new OnlineExperienceRecognizer());

		ProjectGuide guide = service.createGuide("https://github.com/octo/example");

		assertThat(guide.readme().status()).isEqualTo(ReadmeSectionStatus.FAILED);
		assertThat(guide.readme().failure().code()).isEqualTo(GuideErrorCode.GITHUB_TIMEOUT);
		assertThat(guide.readme().failure().retryable()).isTrue();
		assertThat(guide.languages().status()).isEqualTo(LanguageSectionStatus.AVAILABLE);
		assertThat(factsSource.languageRequests()).isEqualTo(1);
	}

	@Test
	void representsUnsupportedReadmeContentAsANonRetryableInitialFailure() {
		RepositoryRef reference = new RepositoryRef("octo", "example");
		var readmeSource = FakeRepositoryReadmeSource.withoutReadme()
				.failingWith(new ReadmeContentUnsupportedException());
		GuideService service = new GuideService(
				new FakeRepositoryUrlParser(reference),
				FakeRepositoryFactsSource.withMetadata(repositoryFacts(reference)),
				readmeSource,
				new OnlineExperienceRecognizer());

		ProjectGuide guide = service.createGuide("https://github.com/octo/example");

		assertThat(guide.readme().status()).isEqualTo(ReadmeSectionStatus.FAILED);
		assertThat(guide.readme().failure().code())
				.isEqualTo(GuideErrorCode.README_CONTENT_UNSUPPORTED);
		assertThat(guide.readme().failure().retryable()).isFalse();
	}

	@Test
	void retriesOnlyTheReadmeRegionAndPreservesAbsence() {
		RepositoryRef reference = new RepositoryRef("octo", "example");
		var factsSource = FakeRepositoryFactsSource.withMetadata(repositoryFacts(reference));
		var readmeSource = FakeRepositoryReadmeSource.withoutReadme();
		GuideService service = new GuideService(
				new GitHubRepositoryUrlParser(),
				factsSource,
				readmeSource,
				new OnlineExperienceRecognizer());

		var readme = service.retryReadme("https://github.com/octo/example");

		assertThat(readme.status()).isEqualTo(ReadmeSectionStatus.NOT_PROVIDED);
		assertThat(readmeSource.receivedRef()).isEqualTo(reference);
		assertThat(factsSource.metadataRequests()).isZero();
		assertThat(factsSource.languageRequests()).isZero();
	}

	@Test
	void representsAnEmptyLanguageResponseAsNotProvided() {
		RepositoryRef reference = new RepositoryRef("octo", "empty");
		GuideService service = guideService(
				new FakeRepositoryUrlParser(reference),
				FakeRepositoryFactsSource.withMetadata(repositoryFacts(reference))
						.withLanguages(new RepositoryLanguageBytes(Map.of())));

		ProjectGuide guide = service.createGuide("https://github.com/octo/empty");

		assertThat(guide.languages().status()).isEqualTo(LanguageSectionStatus.NOT_PROVIDED);
		assertThat(guide.languages().failure()).isNull();
		assertThat(guide.languages().evidenceId()).isNull();
		assertThat(guide.evidence()).doesNotContainKey("repository-languages");
	}

	@Test
	void abortsTheGuideWhenRepositoryMetadataCannotBeConfirmed() {
		RepositoryRef reference = new RepositoryRef("octo", "missing");
		GitHubSourceException failure = new GitHubSourceException(
				GuideErrorCode.REPOSITORY_NOT_ACCESSIBLE);
		FakeRepositoryFactsSource factsSource = FakeRepositoryFactsSource
				.withMetadata(repositoryFacts(reference))
				.failingMetadataWith(failure);
		GuideService service = guideService(
				new FakeRepositoryUrlParser(reference), factsSource);

		assertThatThrownBy(() -> service.createGuide("https://github.com/octo/missing"))
				.isSameAs(failure);
		assertThat(factsSource.languageRequests()).isZero();
	}

	@Test
	void propagatesLanguageFailureFromAnExplicitRetry() {
		RepositoryRef reference = new RepositoryRef("octo", "example");
		GitHubSourceException failure = new GitHubSourceException(
				GuideErrorCode.GITHUB_RATE_LIMITED, 120L);
		FakeRepositoryFactsSource factsSource = FakeRepositoryFactsSource
				.withMetadata(repositoryFacts(reference))
				.failingLanguagesWith(failure);
		GuideService service = guideService(new GitHubRepositoryUrlParser(), factsSource);

		assertThatThrownBy(() -> service.retryLanguages("https://github.com/octo/example"))
				.isSameAs(failure);
	}

	@Test
	void retriesOnlyLanguagesUsingTheCanonicalRepositoryUrl() {
		RepositoryRef reference = new RepositoryRef("octo", "example");
		FakeRepositoryFactsSource factsSource = FakeRepositoryFactsSource
				.withMetadata(repositoryFacts(reference))
				.withLanguages(new RepositoryLanguageBytes(Map.of("Java", 25L)));
		GuideService service = guideService(new GitHubRepositoryUrlParser(), factsSource);

		var languages = service.retryLanguages("https://github.com/octo/example");

		assertThat(languages.status()).isEqualTo(LanguageSectionStatus.AVAILABLE);
		assertThat(factsSource.metadataRequests()).isZero();
		assertThat(factsSource.languageRequests()).isEqualTo(1);
		assertThat(factsSource.receivedLanguageRef()).isEqualTo(reference);
	}

	@Test
	void calculatesSortedLanguagePercentagesAndAssociatesEvidence() {
		RepositoryRef reference = new RepositoryRef("octo", "example");
		FakeRepositoryFactsSource factsSource = FakeRepositoryFactsSource
				.withMetadata(repositoryFacts(reference))
				.withLanguages(new RepositoryLanguageBytes(Map.of(
						"Shell", 5_000L,
						"Java", 80_000L,
						"Kotlin", 15_000L)));
		GuideService service = guideService(
				new FakeRepositoryUrlParser(reference), factsSource);

		ProjectGuide guide = service.createGuide("https://github.com/octo/example");

		assertThat(guide.languages().status()).isEqualTo(LanguageSectionStatus.AVAILABLE);
		assertThat(guide.languages().items())
				.extracting(item -> item.name() + ":" + item.percentage())
				.containsExactly("Java:80.0", "Kotlin:15.0", "Shell:5.0");
		assertThat(guide.languages().evidenceId()).isEqualTo("repository-languages");
		assertThat(guide.evidence().get("repository-languages"))
				.isEqualTo(new LanguageEvidence(
						"repository-languages",
						"GitHub Languages REST API",
						100_000,
						guide.languages().items()));
	}

	@Test
	void returnsPartialGuideWhenRepositoryMetadataSucceedsAndLanguagesTimeOut() {
		RepositoryRef inputRef = new RepositoryRef("openai", "openai-java");
		RepositoryFacts repository = new RepositoryFacts(
				inputRef,
				"The official Java library for the OpenAI API",
				12_345,
				Instant.parse("2024-01-10T08:30:00Z"),
				Instant.parse("2026-08-24T12:00:00Z"));
		FakeRepositoryFactsSource factsSource = FakeRepositoryFactsSource.withMetadata(repository)
				.failingLanguagesWith(new GitHubSourceException(
						GuideErrorCode.GITHUB_TIMEOUT));
		GuideService service = guideService(
				new FakeRepositoryUrlParser(inputRef), factsSource);

		ProjectGuide guide = service.createGuide("https://github.com/openai/openai-java");

		assertThat(guide.repository()).isEqualTo(repository);
		assertThat(guide.languages().status()).isEqualTo(LanguageSectionStatus.FAILED);
		assertThat(guide.languages().failure().code()).isEqualTo(GuideErrorCode.GITHUB_TIMEOUT);
		assertThat(guide.languages().items()).isEmpty();
		assertThat(guide.languages().evidenceId()).isNull();
	}

	@Test
	void startsGuideWithRepositoryReferenceResolvedFromRawUrl() {
		RepositoryRef expected = new RepositoryRef("openai", "openai-java");
		FakeRepositoryUrlParser parser = new FakeRepositoryUrlParser(expected);
		RepositoryFacts repository = repositoryFacts(expected);
		GuideService service = guideService(
				parser, FakeRepositoryFactsSource.withMetadata(repository));

		ProjectGuide result = service.createGuide("https://github.com/openai/openai-java/issues");

		assertThat(result.repository()).isEqualTo(repository);
		assertThat(parser.receivedRawUrl()).isEqualTo("https://github.com/openai/openai-java/issues");
	}

	@Test
	void preservesRepositoryUrlRejectionFromParser() {
		InvalidRepositoryUrlException rejection =
				new InvalidRepositoryUrlException("目前仅支持 github.com 的仓库地址。");
		FakeRepositoryUrlParser parser = FakeRepositoryUrlParser.failingWith(rejection);
		GuideService service = guideService(
				parser,
				FakeRepositoryFactsSource.withMetadata(repositoryFacts(
						new RepositoryRef("unused", "unused"))));

		assertThatThrownBy(() -> service.createGuide("https://gitlab.com/openai/openai-java"))
				.isSameAs(rejection);
		assertThat(parser.receivedRawUrl()).isEqualTo("https://gitlab.com/openai/openai-java");
	}

	private RepositoryFacts repositoryFacts(RepositoryRef reference) {
		return new RepositoryFacts(
				reference,
				"Repository description",
				42,
				Instant.parse("2024-01-10T08:30:00Z"),
				Instant.parse("2026-08-24T12:00:00Z"));
	}

	private GuideService guideService(
			RepositoryUrlParser parser,
			RepositoryFactsSource factsSource) {
		return new GuideService(
				parser,
				factsSource,
				FakeRepositoryReadmeSource.withoutReadme(),
				new OnlineExperienceRecognizer());
	}
}
