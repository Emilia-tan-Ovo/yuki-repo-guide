package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageShare;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ProjectGuide;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RawFact;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryFacts;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryLanguageBytes;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeEnvironment;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.emiliatanovo.yukirepoguide.guide.explanation.*;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public final class GuideService {
	private static final String REPOSITORY_EVIDENCE_ID = "repository-metadata";
	private static final String LANGUAGE_EVIDENCE_ID = "repository-languages";

	private final RepositoryUrlParser repositoryUrlParser;
	private final RepositoryFactsSource repositoryFactsSource;
	private final RepositoryReadmeSource repositoryReadmeSource;
	private final RepositoryReleaseSource repositoryReleaseSource;
	private final OnlineExperienceRecognizer onlineExperienceRecognizer;
	private final ReleaseInterpreter releaseInterpreter;
	private final ExplanationSnapshots snapshots;
	private final IntroductionGenerator introductions;
	private final ExplanationInputSelector inputSelector;

	public GuideService(
			RepositoryUrlParser repositoryUrlParser,
			RepositoryFactsSource repositoryFactsSource,
			RepositoryReadmeSource repositoryReadmeSource,
			RepositoryReleaseSource repositoryReleaseSource,
			OnlineExperienceRecognizer onlineExperienceRecognizer,
			ReleaseInterpreter releaseInterpreter) {
		this(repositoryUrlParser, repositoryFactsSource, repositoryReadmeSource, repositoryReleaseSource,
				onlineExperienceRecognizer, releaseInterpreter,
				new ExplanationSnapshots(ExplanationSettings.defaults(), java.time.Clock.systemUTC()),
				new IntroductionGenerator((input, correction, remaining) -> {
					throw new ExplanationException("EXPLANATION_NOT_CONFIGURED");
				}, ExplanationSettings.defaults(), java.time.Clock.systemUTC()),
				new ExplanationInputSelector(ExplanationSettings.defaults()));
	}

	@Autowired
	public GuideService(RepositoryUrlParser repositoryUrlParser, RepositoryFactsSource repositoryFactsSource,
			RepositoryReadmeSource repositoryReadmeSource, RepositoryReleaseSource repositoryReleaseSource,
			OnlineExperienceRecognizer onlineExperienceRecognizer, ReleaseInterpreter releaseInterpreter,
			ExplanationSnapshots snapshots, IntroductionGenerator introductions, ExplanationInputSelector inputSelector) {
		this.repositoryUrlParser = repositoryUrlParser;
		this.repositoryFactsSource = repositoryFactsSource;
		this.repositoryReadmeSource = repositoryReadmeSource;
		this.repositoryReleaseSource = repositoryReleaseSource;
		this.onlineExperienceRecognizer = onlineExperienceRecognizer;
		this.releaseInterpreter = releaseInterpreter;
		this.snapshots = snapshots;
		this.introductions = introductions;
		this.inputSelector = inputSelector;
	}

	public ProjectGuide createGuide(String rawUrl) {
		return createGuide(rawUrl, null);
	}

	public ProjectGuide createGuide(String rawUrl, String snapshotOwner) {
		RepositoryRef requestedRepository = repositoryUrlParser.parse(rawUrl);
		RepositoryFacts repository = repositoryFactsSource.fetchMetadata(requestedRepository);
		Map<String, GuideEvidence> evidence = new LinkedHashMap<>();
		evidence.put(REPOSITORY_EVIDENCE_ID, repositoryEvidence(repository));
		ReadmeResult readmeResult = initialReadmeSection(repository.reference());
		ReadmeSection readme = readmeResult.section();
		evidence.putAll(readme.evidence());
		LanguageSection languages;
		try {
			RepositoryLanguageBytes languageBytes =
					repositoryFactsSource.fetchLanguages(repository.reference());
			languages = languageSection(languageBytes);
			if (languages.evidence() != null) {
				evidence.put(languages.evidenceId(), languages.evidence());
			}
		}
		catch (GitHubSourceException exception) {
			languages = LanguageSection.failed(exception.code(), exception.retryAfterSeconds());
		}
		ReleaseSection releases;
		try {
			releases = releaseInterpreter.interpret(
					repositoryReleaseSource.fetchReleases(repository.reference()));
			evidence.putAll(releases.evidence());
		}
		catch (GitHubSourceException exception) {
			releases = ReleaseSection.failed(
					exception.code(), true, exception.retryAfterSeconds());
		}
		catch (ReleaseHistoryUnsupportedException exception) {
			releases = ReleaseSection.failed(
					GuideErrorCode.RELEASE_HISTORY_UNSUPPORTED,
					false,
					null);
		}
		return new ProjectGuide(
				repository, REPOSITORY_EVIDENCE_ID, readme, languages, releases, evidence,
				snapshots.save(snapshotOwner, inputSelector.select(repository, readmeResult.source())));
	}

	public ExplanationResult explain(String inputId, String snapshotOwner) {
		ExplanationInput input = snapshots.acquire(inputId, snapshotOwner);
		try {
			return introductions.generate(input);
		} finally {
			snapshots.release(inputId);
		}
	}

	private record ReadmeResult(ReadmeSection section, RepositoryReadme source) {}

	private ReadmeResult initialReadmeSection(RepositoryRef repository) {
		try {
			var source = repositoryReadmeSource.fetchReadme(repository).orElse(null);
			return new ReadmeResult(source == null ? ReadmeSection.notProvided()
					: onlineExperienceRecognizer.recognize(source), source);
		}
		catch (ReadmeContentUnsupportedException exception) {
			return new ReadmeResult(ReadmeSection.failed(
					GuideErrorCode.README_CONTENT_UNSUPPORTED,
					false,
					null), null);
		}
		catch (GitHubSourceException exception) {
			return new ReadmeResult(ReadmeSection.failed(
					exception.code(), true, exception.retryAfterSeconds()), null);
		}
	}

	public ReadmeSection retryReadme(String canonicalUrl) {
		RepositoryRef repository = repositoryUrlParser.parse(canonicalUrl);
		return repositoryReadmeSource.fetchReadme(repository)
				.map(onlineExperienceRecognizer::recognize)
				.orElseGet(ReadmeSection::notProvided);
	}

	public LanguageSection retryLanguages(String canonicalUrl) {
		RepositoryRef repository = repositoryUrlParser.parse(canonicalUrl);
		return languageSection(repositoryFactsSource.fetchLanguages(repository));
	}

	public ReleaseSection retryReleases(String canonicalUrl) {
		RepositoryRef repository = repositoryUrlParser.parse(canonicalUrl);
		return releaseInterpreter.interpret(repositoryReleaseSource.fetchReleases(repository));
	}

	public ReleaseSection recommendReleases(
			String canonicalUrl,
			RuntimeEnvironment runtime) {
		RepositoryRef repository = repositoryUrlParser.parse(canonicalUrl);
		return releaseInterpreter.recommend(
				repositoryReleaseSource.fetchReleases(repository), runtime);
	}

	private LanguageSection languageSection(RepositoryLanguageBytes languageBytes) {
		long totalBytes = languageBytes.bytesByLanguage().values().stream()
				.filter(bytes -> bytes > 0)
				.mapToLong(Long::longValue)
				.sum();
		if (totalBytes == 0) {
			return LanguageSection.notProvided();
		}

		List<LanguageShare> items = languageBytes.bytesByLanguage().entrySet().stream()
				.filter(entry -> entry.getValue() > 0)
				.map(entry -> new LanguageShare(
						entry.getKey(),
						entry.getValue(),
						percentage(entry.getValue(), totalBytes)))
				.sorted(Comparator.comparingLong(LanguageShare::bytes).reversed()
						.thenComparing(LanguageShare::name))
				.toList();
		LanguageEvidence evidence = new LanguageEvidence(
				LANGUAGE_EVIDENCE_ID,
				"GitHub Languages REST API",
				totalBytes,
				items);
		return LanguageSection.available(items, evidence);
	}

	private BigDecimal percentage(long bytes, long totalBytes) {
		return BigDecimal.valueOf(bytes)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(totalBytes), 1, RoundingMode.HALF_UP);
	}

	private RepositoryEvidence repositoryEvidence(RepositoryFacts repository) {
		RawFact recentCodeUpdate = repository.pushedAt() == null
				? null
				: new RawFact("pushed_at", repository.pushedAt().toString());
		return new RepositoryEvidence(
				REPOSITORY_EVIDENCE_ID,
				"GitHub",
				repository.reference().canonicalUrl(),
				recentCodeUpdate);
	}
}
