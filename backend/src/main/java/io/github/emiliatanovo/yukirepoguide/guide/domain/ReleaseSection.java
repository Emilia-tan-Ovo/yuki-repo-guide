package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.Map;

public record ReleaseSection(
		ReleaseSectionStatus status,
		ReleaseSummary latestStable,
		ReleaseSummary latestPrerelease,
		ReleaseFailure failure,
		Map<String, GuideEvidence> evidence) {

	public ReleaseSection {
		evidence = Map.copyOf(evidence);
	}

	public static ReleaseSection notProvided() {
		return new ReleaseSection(
				ReleaseSectionStatus.NOT_PROVIDED, null, null, null, Map.of());
	}

	public static ReleaseSection available(
			ReleaseSummary latestStable,
			ReleaseSummary latestPrerelease,
			Map<String, GuideEvidence> evidence) {
		return new ReleaseSection(
				ReleaseSectionStatus.AVAILABLE,
				latestStable,
				latestPrerelease,
				null,
				evidence);
	}

	public static ReleaseSection failed(
			GuideErrorCode code,
			boolean retryable,
			Long retryAfterSeconds) {
		return new ReleaseSection(
				ReleaseSectionStatus.FAILED,
				null,
				null,
				new ReleaseFailure(code, retryable, retryAfterSeconds),
				Map.of());
	}
}
