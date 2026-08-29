package io.github.emiliatanovo.yukirepoguide.guide.support;

import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryReadmeSource;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;

import java.util.Optional;

public final class FakeRepositoryReadmeSource implements RepositoryReadmeSource {

	private Optional<RepositoryReadme> readme;
	private RuntimeException failure;
	private int requests;
	private RepositoryRef receivedRef;

	private FakeRepositoryReadmeSource(Optional<RepositoryReadme> readme) {
		this.readme = readme;
	}

	public static FakeRepositoryReadmeSource withoutReadme() {
		return new FakeRepositoryReadmeSource(Optional.empty());
	}

	public static FakeRepositoryReadmeSource withReadme(RepositoryReadme readme) {
		return new FakeRepositoryReadmeSource(Optional.of(readme));
	}

	public FakeRepositoryReadmeSource failingWith(RuntimeException failure) {
		this.failure = failure;
		return this;
	}

	public FakeRepositoryReadmeSource returning(RepositoryReadme readme) {
		this.readme = Optional.of(readme);
		this.failure = null;
		return this;
	}

	@Override
	public Optional<RepositoryReadme> fetchReadme(RepositoryRef repositoryRef) {
		requests += 1;
		receivedRef = repositoryRef;
		if (failure != null) {
			throw failure;
		}
		return readme;
	}

	public int requests() {
		return requests;
	}

	public RepositoryRef receivedRef() {
		return receivedRef;
	}
}
