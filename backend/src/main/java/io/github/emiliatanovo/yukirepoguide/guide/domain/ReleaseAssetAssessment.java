package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record ReleaseAssetAssessment(
		ReleaseAssetMatchStatus matchStatus,
		boolean directlyRecommended,
		RuntimeOperatingSystem detectedOperatingSystem,
		ProcessorArchitecture detectedArchitecture,
		LinuxPackageFamily detectedLinuxPackageFamily) {
}
