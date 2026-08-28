package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.List;

public record OnlineExperienceCandidate(
		String label,
		String url,
		String evidenceId,
		List<OnlineExperienceWarning> warnings) {

	public OnlineExperienceCandidate {
		warnings = List.copyOf(warnings);
	}
}
