package io.github.emiliatanovo.yukirepoguide.guide.application;

public final class ReleaseHistoryUnsupportedException extends RuntimeException {

	public ReleaseHistoryUnsupportedException() {
		super("Release history exceeds the supported pagination limit");
	}
}
