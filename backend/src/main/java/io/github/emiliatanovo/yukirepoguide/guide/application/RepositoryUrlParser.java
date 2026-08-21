package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;

public interface RepositoryUrlParser {

	RepositoryRef parse(String rawUrl);
}
