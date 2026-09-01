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
import org.springframework.stereotype.Service;

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

	public GuideService(
			RepositoryUrlParser repositoryUrlParser,
			RepositoryFactsSource repositoryFactsSource,
			RepositoryReadmeSource repositoryReadmeSource,
			RepositoryReleaseSource repositoryReleaseSource,
			OnlineExperienceRecognizer onlineExperienceRecognizer,
			ReleaseInterpreter releaseInterpreter) {
		this.repositoryUrlParser = repositoryUrlParser;
		this.repositoryFactsSource = repositoryFactsSource;
		this.repositoryReadmeSource = repositoryReadmeSource;
		this.repositoryReleaseSource = repositoryReleaseSource;
		this.onlineExperienceRecognizer = onlineExperienceRecognizer;
		this.releaseInterpreter = releaseInterpreter;
	}

	public ProjectGuide createGuide(String rawUrl) {
		RepositoryRef requestedRepository = repositoryUrlParser.parse(rawUrl);
		RepositoryFacts repository = repositoryFactsSource.fetchMetadata(requestedRepository);
		Map<String, GuideEvidence> evidence = new LinkedHashMap<>();
		evidence.put(REPOSITORY_EVIDENCE_ID, repositoryEvidence(repository));
		ReadmeSection readme = initialReadmeSection(repository.reference());
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
				repository, REPOSITORY_EVIDENCE_ID, readme, languages, releases, evidence);
	}

	private ReadmeSection initialReadmeSection(RepositoryRef repository) {
		try {
			return repositoryReadmeSource.fetchReadme(repository)
					.map(onlineExperienceRecognizer::recognize)
					.orElseGet(ReadmeSection::notProvided);
		}
		catch (ReadmeContentUnsupportedException exception) {
			return ReadmeSection.failed(
					GuideErrorCode.README_CONTENT_UNSUPPORTED,
					false,
					null);
		}
		catch (GitHubSourceException exception) {
			return ReadmeSection.failed(
					exception.code(), true, exception.retryAfterSeconds());
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
