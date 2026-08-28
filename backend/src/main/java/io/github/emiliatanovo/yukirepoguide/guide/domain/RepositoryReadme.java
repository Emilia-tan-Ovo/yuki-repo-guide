package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.Objects;

public record RepositoryReadme(
		String path,
		String sha,
		String htmlUrl,
		String content) {

	public RepositoryReadme {
		Objects.requireNonNull(path);
		Objects.requireNonNull(sha);
		Objects.requireNonNull(htmlUrl);
		Objects.requireNonNull(content);
	}
}
