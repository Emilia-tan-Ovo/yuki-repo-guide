package io.github.emiliatanovo.yukirepoguide.guide.application;

public final class ReadmeContentUnsupportedException extends RuntimeException {
	public ReadmeContentUnsupportedException() {
		super("README content is unsupported");
	}

	public ReadmeContentUnsupportedException(String message) {
		super(message);
	}

	public ReadmeContentUnsupportedException(String message, Throwable cause) {
		super(message, cause);
	}
}
