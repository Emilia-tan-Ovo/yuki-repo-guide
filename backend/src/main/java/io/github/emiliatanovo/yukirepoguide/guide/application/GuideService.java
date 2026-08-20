package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import org.springframework.stereotype.Service;

@Service
public final class GuideService {

	private final RepositoryUrlParser repositoryUrlParser;

	public GuideService(RepositoryUrlParser repositoryUrlParser) {
		this.repositoryUrlParser = repositoryUrlParser;
	}

	public RepositoryRef createGuide(String rawUrl) {
		return repositoryUrlParser.parse(rawUrl);
	}
}
