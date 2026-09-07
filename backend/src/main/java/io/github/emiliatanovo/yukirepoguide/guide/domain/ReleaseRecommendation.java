package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.List;

public record ReleaseRecommendation(
		ReleaseRecommendationStatus status,
		RuntimeEnvironment runtime,
		List<LinuxPackageFamily> availableLinuxFamilies) {

	public ReleaseRecommendation {
		availableLinuxFamilies = List.copyOf(availableLinuxFamilies);
	}

	public static ReleaseRecommendation notRequested() {
		return new ReleaseRecommendation(
				ReleaseRecommendationStatus.NOT_REQUESTED, null, List.of());
	}
}
