package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;

import java.util.Optional;

public interface RepositoryReadmeSource {

	Optional<RepositoryReadme> fetchReadme(RepositoryRef repositoryRef);
}
