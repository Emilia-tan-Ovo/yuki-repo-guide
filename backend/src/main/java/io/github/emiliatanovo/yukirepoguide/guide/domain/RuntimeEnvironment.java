package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.Objects;

public record RuntimeEnvironment(
		RuntimeOperatingSystem operatingSystem,
		ProcessorArchitecture architecture,
		LinuxPackageFamily linuxPackageFamily) {

	public RuntimeEnvironment {
		Objects.requireNonNull(operatingSystem, "operatingSystem");
		Objects.requireNonNull(architecture, "architecture");
		if (operatingSystem != RuntimeOperatingSystem.LINUX && linuxPackageFamily != null) {
			throw new IllegalArgumentException(
					"linuxPackageFamily is only valid for Linux environments");
		}
	}
}
