package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;

public final class GitHubSourceException extends RuntimeException {

	private final GuideErrorCode code;
	private final Long retryAfterSeconds;

	public GitHubSourceException(GuideErrorCode code) {
		this(code, null);
	}

	public GitHubSourceException(GuideErrorCode code, Long retryAfterSeconds) {
		super(code.name());
		this.code = code;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public GuideErrorCode code() {
		return code;
	}

	public Long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
