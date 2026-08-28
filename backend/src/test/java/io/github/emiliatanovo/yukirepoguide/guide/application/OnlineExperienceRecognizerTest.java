package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;
import io.github.emiliatanovo.yukirepoguide.guide.domain.OnlineExperienceWarning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OnlineExperienceRecognizerTest {

	private final OnlineExperienceRecognizer recognizer = new OnlineExperienceRecognizer();

	@Test
	void recognizesExplicitMeaningFromLinksSurroundingTextAndHeadingSections() {
		RepositoryReadme readme = readme("""
				[Demo](https://one.example.com)

				在线体验：[立即打开](https://two.example.com)

				## Playground

				### Web version

				[打开](https://three.example.com)

				## Documentation

				[Docs](https://docs.example.com)
				""");

		var result = recognizer.recognize(readme);

		assertThat(result.candidates())
				.extracting(candidate -> candidate.url())
				.containsExactly(
						"https://one.example.com",
						"https://two.example.com",
						"https://three.example.com");
		assertThat(result.evidence().get("readme-online-experience-3").context())
				.contains("Playground", "打开");
	}

	@Test
	void rejectsRelativeNonHttpAndCredentialBearingUrls() {
		var result = recognizer.recognize(readme("""
				[Demo](javascript:alert(1))
				[Live Demo](/demo)
				[Online Demo](mailto:demo@example.com)
				[Try it online](https://user@example.com/demo)
				[Playground](https://safe.example.com)
				"""));

		assertThat(result.candidates())
				.extracting(candidate -> candidate.url())
				.containsExactly("https://safe.example.com");
	}

	@Test
	void rejectsAmbiguousOrdinaryLinksInsideAMarkedSection() {
		var result = recognizer.recognize(readme("""
				## Demo

				[Open](https://demo.example.com) and [Documentation](https://docs.example.com)
				"""));

		assertThat(result.candidates()).isEmpty();
	}

	@Test
	void recognizesClickableBadgesAndWarnsAboutPlainHttp() {
		var result = recognizer.recognize(readme(
				"[![Live Demo](badge.svg)](http://demo.example.com)"));

		assertThat(result.candidates()).singleElement().satisfies(candidate -> {
			assertThat(candidate.label()).isEqualTo("Live Demo");
			assertThat(candidate.warnings()).containsExactly(
					OnlineExperienceWarning.EXTERNAL_SITE_NOT_VERIFIED,
					OnlineExperienceWarning.INSECURE_HTTP);
		});
	}

	@Test
	void keepsStableOrderDeduplicatesUrlsAndReportsTheCandidateLimit() {
		StringBuilder content = new StringBuilder();
		for (int index = 1; index <= 21; index += 1) {
			content.append("[Live Demo ")
					.append(index)
					.append("](https://demo.example.com/")
					.append(index)
					.append(")\n\n");
		}
		content.append("[Online Demo](https://demo.example.com/1)");

		var result = recognizer.recognize(readme(content.toString()));

		assertThat(result.candidates()).hasSize(20);
		assertThat(result.candidates().getFirst().url()).isEqualTo("https://demo.example.com/1");
		assertThat(result.candidates().getLast().url()).isEqualTo("https://demo.example.com/20");
		assertThat(result.truncated()).isTrue();
	}

	private RepositoryReadme readme(String content) {
		return new RepositoryReadme(
				"README.md",
				"abc123",
				"https://github.com/octo/example/blob/main/README.md",
				content);
	}
}
