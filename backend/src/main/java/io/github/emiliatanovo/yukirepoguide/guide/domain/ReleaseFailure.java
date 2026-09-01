package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record ReleaseFailure(
		GuideErrorCode code,
		boolean retryable,
		Long retryAfterSeconds) {
}
