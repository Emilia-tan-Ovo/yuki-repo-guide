package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record LanguageFailure(GuideErrorCode code, Long retryAfterSeconds) {
}
