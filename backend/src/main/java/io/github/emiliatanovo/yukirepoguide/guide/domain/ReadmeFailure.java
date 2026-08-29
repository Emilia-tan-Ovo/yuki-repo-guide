package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record ReadmeFailure(
		GuideErrorCode code,
		boolean retryable,
		Long retryAfterSeconds) {
}
