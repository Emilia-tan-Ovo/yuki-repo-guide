package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.time.Instant;

public record RepositoryFacts(
		RepositoryRef reference,
		String description,
		long stars,
		Instant createdAt,
		Instant pushedAt) {
}
