package io.github.emiliatanovo.yukirepoguide.guide.support;

import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryUrlParser;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;

public final class FakeRepositoryUrlParser implements RepositoryUrlParser {

	private final RepositoryRef result;
	private final RuntimeException failure;
	private String receivedRawUrl;

	public FakeRepositoryUrlParser(RepositoryRef result) {
		this.result = result;
		this.failure = null;
	}

	private FakeRepositoryUrlParser(RuntimeException failure) {
		this.result = null;
		this.failure = failure;
	}

	public static FakeRepositoryUrlParser failingWith(RuntimeException failure) {
		return new FakeRepositoryUrlParser(failure);
	}

	@Override
	public RepositoryRef parse(String rawUrl) {
		receivedRawUrl = rawUrl;
		if (failure != null) {
			throw failure;
		}
		return result;
	}

	public String receivedRawUrl() {
		return receivedRawUrl;
	}
}
