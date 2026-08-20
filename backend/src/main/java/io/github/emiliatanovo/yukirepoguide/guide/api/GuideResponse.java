package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;

public record GuideResponse(Repository repository) {

	public static GuideResponse from(RepositoryRef repositoryRef) {
		return new GuideResponse(new Repository(
				repositoryRef.owner(),
				repositoryRef.name(),
				repositoryRef.canonicalUrl()));
	}

	public record Repository(String owner, String name, String canonicalUrl) {
	}
}
