package io.github.emiliatanovo.yukirepoguide.auth.application;

import org.springframework.security.core.AuthenticationException;

public final class TooManyLoginAttemptsException extends AuthenticationException {

	private final long retryAfterSeconds;

	public TooManyLoginAttemptsException(long retryAfterSeconds) {
		super("Too many trial access attempts");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
