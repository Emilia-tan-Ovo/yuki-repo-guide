package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.InvalidRuntimeEnvironmentException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LinuxPackageFamily;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ProcessorArchitecture;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeEnvironment;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeOperatingSystem;
import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

public record ReleaseRecommendationRequest(
		@NotBlank(message = "缺少 GitHub 仓库地址。") String canonicalUrl,
		ConfirmedRuntime runtime, String guideId) {

	public RuntimeEnvironment confirmedRuntime() {
		if (runtime == null) {
			throw invalid("runtime", "请先确认运行环境。");
		}
		RuntimeOperatingSystem operatingSystem = parse(
				runtime.operatingSystem(),
				RuntimeOperatingSystem.class,
				"runtime.operatingSystem",
				"请选择 Windows、macOS 或 Linux。");
		ProcessorArchitecture architecture = parse(
				runtime.architecture(),
				ProcessorArchitecture.class,
				"runtime.architecture",
				"请选择 x64 或 arm64 架构。");
		LinuxPackageFamily family = null;
		if (runtime.linuxPackageFamily() != null
				&& !runtime.linuxPackageFamily().isBlank()) {
			family = parse(
					runtime.linuxPackageFamily(),
					LinuxPackageFamily.class,
					"runtime.linuxPackageFamily",
					"请选择受支持的 Linux 资源族。");
		}
		if (operatingSystem != RuntimeOperatingSystem.LINUX && family != null) {
			throw invalid(
					"runtime.linuxPackageFamily",
					"只有 Linux 环境可以指定 Linux 资源族。");
		}
		return new RuntimeEnvironment(operatingSystem, architecture, family);
	}

	private <T extends Enum<T>> T parse(
			String rawValue,
			Class<T> type,
			String field,
			String detail) {
		if (rawValue == null || rawValue.isBlank()) {
			throw invalid(field, detail);
		}
		try {
			return Enum.valueOf(type, rawValue.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			throw invalid(field, detail);
		}
	}

	private InvalidRuntimeEnvironmentException invalid(String field, String detail) {
		return new InvalidRuntimeEnvironmentException(field, detail);
	}

	public record ConfirmedRuntime(
			String operatingSystem,
			String architecture,
			String linuxPackageFamily) {
	}
}
