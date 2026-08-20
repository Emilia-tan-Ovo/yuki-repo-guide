package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record RepositoryRef(String owner, String name) {

	public String canonicalUrl() {
		return "https://github.com/%s/%s".formatted(owner, name);
	}
}
