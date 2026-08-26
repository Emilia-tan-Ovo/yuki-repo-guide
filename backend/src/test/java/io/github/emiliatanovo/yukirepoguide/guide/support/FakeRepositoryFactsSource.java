package io.github.emiliatanovo.yukirepoguide.guide.support;

import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryFactsSource;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryFacts;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryLanguageBytes;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;

public final class FakeRepositoryFactsSource implements RepositoryFactsSource {

	private final RepositoryFacts metadata;
	private RuntimeException metadataFailure;
	private RuntimeException languageFailure;
	private RepositoryLanguageBytes languages = RepositoryLanguageBytes.empty();
	private int metadataRequests;
	private int languageRequests;
	private RepositoryRef receivedLanguageRef;

	private FakeRepositoryFactsSource(RepositoryFacts metadata) {
		this.metadata = metadata;
	}

	public static FakeRepositoryFactsSource withMetadata(RepositoryFacts metadata) {
		return new FakeRepositoryFactsSource(metadata);
	}

	public FakeRepositoryFactsSource failingLanguagesWith(RuntimeException failure) {
		this.languageFailure = failure;
		return this;
	}

	public FakeRepositoryFactsSource withLanguages(RepositoryLanguageBytes languages) {
		this.languages = languages;
		return this;
	}

	public FakeRepositoryFactsSource failingMetadataWith(RuntimeException failure) {
		this.metadataFailure = failure;
		return this;
	}

	@Override
	public RepositoryFacts fetchMetadata(RepositoryRef repositoryRef) {
		metadataRequests += 1;
		if (metadataFailure != null) {
			throw metadataFailure;
		}
		return metadata;
	}

	@Override
	public RepositoryLanguageBytes fetchLanguages(RepositoryRef repositoryRef) {
		languageRequests += 1;
		receivedLanguageRef = repositoryRef;
		if (languageFailure != null) {
			throw languageFailure;
		}
		return languages;
	}

	public int metadataRequests() {
		return metadataRequests;
	}

	public int languageRequests() {
		return languageRequests;
	}

	public RepositoryRef receivedLanguageRef() {
		return receivedLanguageRef;
	}
}
