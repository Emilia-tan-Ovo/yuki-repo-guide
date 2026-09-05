package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.List;

public record RepositoryReleases(List<RepositoryRelease> items) {

	public RepositoryReleases {
		items = List.copyOf(items);
	}

	public static RepositoryReleases empty() {
		return new RepositoryReleases(List.of());
	}
}
