package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryFacts;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryLanguageBytes;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;

public interface RepositoryFactsSource {

	RepositoryFacts fetchMetadata(RepositoryRef repositoryRef);

	RepositoryLanguageBytes fetchLanguages(RepositoryRef repositoryRef);
}
