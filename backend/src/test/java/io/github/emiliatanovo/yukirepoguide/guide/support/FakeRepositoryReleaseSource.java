package io.github.emiliatanovo.yukirepoguide.guide.support;

import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryReleaseSource;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleases;

public final class FakeRepositoryReleaseSource implements RepositoryReleaseSource {

	private RepositoryReleases releases;
	private RuntimeException failure;
	private int requests;
	private RepositoryRef receivedRef;

	private FakeRepositoryReleaseSource(RepositoryReleases releases) {
		this.releases = releases;
	}

	public static FakeRepositoryReleaseSource withoutReleases() {
		return new FakeRepositoryReleaseSource(RepositoryReleases.empty());
	}

	public static FakeRepositoryReleaseSource withReleases(RepositoryReleases releases) {
		return new FakeRepositoryReleaseSource(releases);
	}

	public FakeRepositoryReleaseSource failingWith(RuntimeException failure) {
		this.failure = failure;
		return this;
	}

	public FakeRepositoryReleaseSource returning(RepositoryReleases releases) {
		this.releases = releases;
		this.failure = null;
		return this;
	}

	@Override
	public RepositoryReleases fetchReleases(RepositoryRef repositoryRef) {
		requests += 1;
		receivedRef = repositoryRef;
		if (failure != null) {
			throw failure;
		}
		return releases;
	}

	public int requests() {
		return requests;
	}

	public RepositoryRef receivedRef() {
		return receivedRef;
	}
}
