package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.time.Instant;

public record RepositoryFacts(
		RepositoryRef reference,
		String description,
		long stars,
		Instant createdAt,
		Instant pushedAt,
		String projectWebsiteUrl) {

	public RepositoryFacts(
			RepositoryRef reference,
			String description,
			long stars,
			Instant createdAt,
			Instant pushedAt) {
		this(reference, description, stars, createdAt, pushedAt, null);
	}
}
