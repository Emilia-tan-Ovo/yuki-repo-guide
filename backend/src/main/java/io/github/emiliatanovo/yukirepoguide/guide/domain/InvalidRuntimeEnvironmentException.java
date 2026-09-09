package io.github.emiliatanovo.yukirepoguide.guide.domain;

public final class InvalidRuntimeEnvironmentException extends RuntimeException {

	private final String field;

	public InvalidRuntimeEnvironmentException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
