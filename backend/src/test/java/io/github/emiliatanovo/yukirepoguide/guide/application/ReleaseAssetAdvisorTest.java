package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.LinuxPackageFamily;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ProcessorArchitecture;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAssetMatchStatus;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAssetRole;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseRecommendationStatus;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleaseAsset;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeEnvironment;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeOperatingSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseAssetAdvisorTest {

	private final ReleaseAssetAdvisor advisor = new ReleaseAssetAdvisor();

	@ParameterizedTest(name = "{0}")
	@MethodSource("assetCases")
	void classifiesRolesAndMatchesOnlyHighConfidenceEnvironmentSignals(
			String fileName,
			RuntimeEnvironment runtime,
			ReleaseAssetRole expectedRole,
			ReleaseAssetMatchStatus expectedMatch) {
		var asset = asset(1L, fileName);

		var advice = advisor.advise(List.of(asset), List.of(), runtime);

		assertThat(advice.decisions().get(asset.id()))
				.extracting(
						ReleaseAssetAdvisor.AssetDecision::role,
						ReleaseAssetAdvisor.AssetDecision::matchStatus)
				.containsExactly(expectedRole, expectedMatch);
	}

	@Test
	void asksForLinuxPackageFamilyOnlyWhenItCanCreateAnExactMatch() {
		var deb = asset(1L, "yuki-linux-amd64.deb");
		var rpm = asset(2L, "yuki-linux-x86_64.rpm");
		var runtime = new RuntimeEnvironment(
				RuntimeOperatingSystem.LINUX, ProcessorArchitecture.X64, null);

		var advice = advisor.advise(List.of(deb, rpm), List.of(), runtime);

		assertThat(advice.status()).isEqualTo(ReleaseRecommendationStatus.NEEDS_LINUX_FAMILY);
		assertThat(advice.availableLinuxFamilies())
				.containsExactly(
						LinuxPackageFamily.DEB,
						LinuxPackageFamily.RPM,
						LinuxPackageFamily.OTHER_OR_UNKNOWN);
		assertThat(advice.decisions().values())
				.allMatch(decision -> !decision.directlyRecommended());
	}

	@Test
	void skipsLinuxFamilyQuestionWhenAGenericExactAssetExists() {
		var appImage = asset(1L, "yuki-linux-x86_64.AppImage");
		var deb = asset(2L, "yuki-linux-amd64.deb");
		var runtime = new RuntimeEnvironment(
				RuntimeOperatingSystem.LINUX, ProcessorArchitecture.X64, null);

		var advice = advisor.advise(List.of(appImage, deb), List.of(), runtime);

		assertThat(advice.status()).isEqualTo(ReleaseRecommendationStatus.READY);
		assertThat(advice.decisions().get(appImage.id()).matchStatus())
				.isEqualTo(ReleaseAssetMatchStatus.MATCHED);
		assertThat(advice.decisions().get(appImage.id()).directlyRecommended()).isTrue();
	}

	@Test
	void stillAsksForLinuxFamilyWhenOnlyPrereleaseHasAGenericExactAsset() {
		var stableDeb = asset(1L, "yuki-linux-amd64.deb");
		var prereleaseAppImage = asset(2L, "yuki-linux-x86_64.AppImage");
		var runtime = new RuntimeEnvironment(
				RuntimeOperatingSystem.LINUX, ProcessorArchitecture.X64, null);

		var advice = advisor.advise(
				List.of(stableDeb), List.of(prereleaseAppImage), runtime);

		assertThat(advice.status()).isEqualTo(ReleaseRecommendationStatus.NEEDS_LINUX_FAMILY);
		assertThat(advice.availableLinuxFamilies())
				.containsExactly(LinuxPackageFamily.DEB, LinuxPackageFamily.OTHER_OR_UNKNOWN);
		assertThat(advice.decisions().values())
				.allMatch(decision -> !decision.directlyRecommended());
	}

	@Test
	void recommendsAllTopRankedStableInstallersButNeverPrereleaseAssets() {
		var setupExe = asset(1L, "yuki-windows-x64-setup.exe");
		var installerMsi = asset(2L, "yuki-win64-x64-installer.msi");
		var portableExe = asset(3L, "yuki-windows-x64-portable.exe");
		var prereleaseMsi = asset(4L, "yuki-windows-x64-installer.msi");
		var runtime = new RuntimeEnvironment(
				RuntimeOperatingSystem.WINDOWS, ProcessorArchitecture.X64, null);

		var advice = advisor.advise(
				List.of(setupExe, installerMsi, portableExe),
				List.of(prereleaseMsi),
				runtime);

		assertThat(advice.decisions().get(setupExe.id()).directlyRecommended()).isTrue();
		assertThat(advice.decisions().get(installerMsi.id()).directlyRecommended()).isTrue();
		assertThat(advice.decisions().get(portableExe.id()).directlyRecommended()).isFalse();
		assertThat(advice.decisions().get(prereleaseMsi.id()).directlyRecommended()).isFalse();
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("supportedRuntimeCombinations")
	void deterministicallyMatchesAllSixSupportedRuntimeCombinations(
			RuntimeOperatingSystem operatingSystem,
			ProcessorArchitecture architecture,
			String fileName) {
		var asset = asset(1L, fileName);

		var advice = advisor.advise(
				List.of(asset),
				List.of(),
				new RuntimeEnvironment(operatingSystem, architecture, null));

		assertThat(advice.decisions().get(asset.id()).matchStatus())
				.isEqualTo(ReleaseAssetMatchStatus.MATCHED);
	}

	private static Stream<Arguments> assetCases() {
		RuntimeEnvironment windowsX64 = new RuntimeEnvironment(
				RuntimeOperatingSystem.WINDOWS, ProcessorArchitecture.X64, null);
		RuntimeEnvironment macArm64 = new RuntimeEnvironment(
				RuntimeOperatingSystem.MACOS, ProcessorArchitecture.ARM64, null);
		RuntimeEnvironment linuxDebX64 = new RuntimeEnvironment(
				RuntimeOperatingSystem.LINUX,
				ProcessorArchitecture.X64,
				LinuxPackageFamily.DEB);
		return Stream.of(
				Arguments.of("yuki-windows-x64-setup.exe", windowsX64,
						ReleaseAssetRole.STANDARD_INSTALLER, ReleaseAssetMatchStatus.MATCHED),
				Arguments.of("yuki-win-x64-portable.exe", windowsX64,
						ReleaseAssetRole.PORTABLE, ReleaseAssetMatchStatus.MATCHED),
				Arguments.of("yuki-x86_64.zip", windowsX64,
						ReleaseAssetRole.MANUAL_ARCHIVE,
						ReleaseAssetMatchStatus.POSSIBLY_APPLICABLE),
				Arguments.of("yuki-darwin-arm64.dmg", macArm64,
						ReleaseAssetRole.STANDARD_INSTALLER, ReleaseAssetMatchStatus.MATCHED),
				Arguments.of("yuki-linux-amd64.deb", linuxDebX64,
						ReleaseAssetRole.STANDARD_INSTALLER, ReleaseAssetMatchStatus.MATCHED),
				Arguments.of("yuki-linux-amd64.rpm", linuxDebX64,
						ReleaseAssetRole.STANDARD_INSTALLER, ReleaseAssetMatchStatus.NOT_MATCHED),
				Arguments.of("yuki-linux-amd64-musl.tar.gz", linuxDebX64,
						ReleaseAssetRole.MANUAL_ARCHIVE,
						ReleaseAssetMatchStatus.UNABLE_TO_CONFIRM),
				Arguments.of("yuki-windows-x86-setup.exe", windowsX64,
						ReleaseAssetRole.STANDARD_INSTALLER, ReleaseAssetMatchStatus.NOT_MATCHED),
				Arguments.of("yuki-windows-linux-x64.zip", windowsX64,
						ReleaseAssetRole.MANUAL_ARCHIVE,
						ReleaseAssetMatchStatus.UNABLE_TO_CONFIRM),
				Arguments.of("yuki-windows-x64.zip.sha256", windowsX64,
						ReleaseAssetRole.AUXILIARY, ReleaseAssetMatchStatus.MATCHED),
				Arguments.of("yuki-source.tar.gz", windowsX64,
						ReleaseAssetRole.SOURCE, ReleaseAssetMatchStatus.POSSIBLY_APPLICABLE),
				Arguments.of("latest.yml", windowsX64,
						ReleaseAssetRole.AUXILIARY,
						ReleaseAssetMatchStatus.POSSIBLY_APPLICABLE),
				Arguments.of("yuki-windows-x64.bin", windowsX64,
						ReleaseAssetRole.UNKNOWN, ReleaseAssetMatchStatus.MATCHED));
	}

	private static Stream<Arguments> supportedRuntimeCombinations() {
		return Stream.of(
				Arguments.of(
						RuntimeOperatingSystem.WINDOWS,
						ProcessorArchitecture.X64,
						"yuki-windows-x64.exe"),
				Arguments.of(
						RuntimeOperatingSystem.WINDOWS,
						ProcessorArchitecture.ARM64,
						"yuki-windows-arm64.msi"),
				Arguments.of(
						RuntimeOperatingSystem.MACOS,
						ProcessorArchitecture.X64,
						"yuki-macos-x86_64.dmg"),
				Arguments.of(
						RuntimeOperatingSystem.MACOS,
						ProcessorArchitecture.ARM64,
						"yuki-darwin-aarch64.pkg"),
				Arguments.of(
						RuntimeOperatingSystem.LINUX,
						ProcessorArchitecture.X64,
						"yuki-linux-amd64.AppImage"),
				Arguments.of(
						RuntimeOperatingSystem.LINUX,
						ProcessorArchitecture.ARM64,
						"yuki-linux-arm64.AppImage"));
	}

	private RepositoryReleaseAsset asset(long id, String name) {
		return new RepositoryReleaseAsset(
				id,
				name,
				1024L,
				"https://github.com/octo/example/releases/download/v1.0/" + name);
	}
}
