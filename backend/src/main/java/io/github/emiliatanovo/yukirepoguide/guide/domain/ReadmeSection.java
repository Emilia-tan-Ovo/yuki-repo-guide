package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.List;
import java.util.Map;

public record ReadmeSection(
		ReadmeSectionStatus status,
		List<OnlineExperienceCandidate> candidates,
		boolean truncated,
		ReadmeFailure failure,
		Map<String, ReadmeEvidence> evidence) {

	public ReadmeSection {
		candidates = List.copyOf(candidates);
		evidence = Map.copyOf(evidence);
	}

	public static ReadmeSection available(
			List<OnlineExperienceCandidate> candidates,
			boolean truncated,
			Map<String, ReadmeEvidence> evidence) {
		return new ReadmeSection(
				ReadmeSectionStatus.AVAILABLE,
				candidates,
				truncated,
				null,
				evidence);
	}

	public static ReadmeSection notProvided() {
		return new ReadmeSection(
				ReadmeSectionStatus.NOT_PROVIDED,
				List.of(),
				false,
				null,
				Map.of());
	}

	public static ReadmeSection failed(
			GuideErrorCode code,
			boolean retryable,
			Long retryAfterSeconds) {
		return new ReadmeSection(
				ReadmeSectionStatus.FAILED,
				List.of(),
				false,
				new ReadmeFailure(code, retryable, retryAfterSeconds),
				Map.of());
	}
}
