package io.github.emiliatanovo.yukirepoguide.auth.application;

import org.springframework.security.authentication.BadCredentialsException;

public final class InvalidAccessCodeException extends BadCredentialsException {

	public InvalidAccessCodeException() {
		super("Invalid trial access code");
	}
}
