package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageShare;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ProjectGuide;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RawFact;
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

	public GuideService(
			RepositoryUrlParser repositoryUrlParser,
			RepositoryFactsSource repositoryFactsSource) {
		this.repositoryUrlParser = repositoryUrlParser;
		this.repositoryFactsSource = repositoryFactsSource;
	}

	public ProjectGuide createGuide(String rawUrl) {
		RepositoryRef requestedRepository = repositoryUrlParser.parse(rawUrl);
		RepositoryFacts repository = repositoryFactsSource.fetchMetadata(requestedRepository);
		Map<String, GuideEvidence> evidence = new LinkedHashMap<>();
		evidence.put(REPOSITORY_EVIDENCE_ID, repositoryEvidence(repository));
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
		return new ProjectGuide(repository, REPOSITORY_EVIDENCE_ID, languages, evidence);
	}

	public LanguageSection retryLanguages(String canonicalUrl) {
		RepositoryRef repository = repositoryUrlParser.parse(canonicalUrl);
		return languageSection(repositoryFactsSource.fetchLanguages(repository));
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
