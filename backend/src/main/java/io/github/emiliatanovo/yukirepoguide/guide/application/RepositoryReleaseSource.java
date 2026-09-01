package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleases;

public interface RepositoryReleaseSource {

	RepositoryReleases fetchReleases(RepositoryRef repositoryRef);
}
