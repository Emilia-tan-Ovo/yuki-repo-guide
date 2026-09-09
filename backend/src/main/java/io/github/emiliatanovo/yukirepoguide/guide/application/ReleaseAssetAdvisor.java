package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.LinuxPackageFamily;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ProcessorArchitecture;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAssetMatchStatus;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAssetRole;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseRecommendationStatus;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleaseAsset;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeEnvironment;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeOperatingSystem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public final class ReleaseAssetAdvisor {

	public Advice advise(
			List<RepositoryReleaseAsset> stableAssets,
			List<RepositoryReleaseAsset> prereleaseAssets,
			RuntimeEnvironment runtime) {
		Map<Long, AssetDecision> decisions = new LinkedHashMap<>();
		List<AnalyzedAsset> stable = analyze(stableAssets, runtime, decisions);
		List<AnalyzedAsset> prerelease = analyze(prereleaseAssets, runtime, decisions);

		if (runtime == null) {
			return new Advice(
					ReleaseRecommendationStatus.NOT_REQUESTED,
					null,
					List.of(),
					decisions,
					List.of(),
					List.of());
		}

		List<AnalyzedAsset> all = new ArrayList<>(stable);
		all.addAll(prerelease);
		boolean hasStableGenericExactMatch = stable.stream()
				.anyMatch(asset -> eligible(asset.decision().role())
						&& asset.decision().matchStatus() == ReleaseAssetMatchStatus.MATCHED);
		List<LinuxPackageFamily> availableFamilies = availableFamilies(all, runtime);
		if (runtime.operatingSystem() == RuntimeOperatingSystem.LINUX
				&& runtime.linuxPackageFamily() == null
				&& !hasStableGenericExactMatch
				&& !availableFamilies.isEmpty()) {
			availableFamilies = new ArrayList<>(availableFamilies);
			availableFamilies.add(LinuxPackageFamily.OTHER_OR_UNKNOWN);
			return new Advice(
					ReleaseRecommendationStatus.NEEDS_LINUX_FAMILY,
					runtime,
					availableFamilies,
					decisions,
					List.of(),
					List.of());
		}

		int bestStableRoleRank = stable.stream()
				.filter(asset -> asset.decision().matchStatus() == ReleaseAssetMatchStatus.MATCHED)
				.filter(asset -> eligible(asset.decision().role()))
				.mapToInt(asset -> roleRank(asset.decision().role()))
				.min()
				.orElse(Integer.MAX_VALUE);
		if (bestStableRoleRank != Integer.MAX_VALUE) {
			stable.stream()
					.filter(asset -> asset.decision().matchStatus() == ReleaseAssetMatchStatus.MATCHED)
					.filter(asset -> roleRank(asset.decision().role()) == bestStableRoleRank)
					.forEach(asset -> decisions.put(
							asset.asset().id(), asset.decision().withDirectRecommendation()));
		}
		return new Advice(
				ReleaseRecommendationStatus.READY,
				runtime,
				List.of(),
				decisions,
				orderedMatches(stable, decisions),
				orderedMatches(prerelease, decisions));
	}

	private List<Long> orderedMatches(
			List<AnalyzedAsset> assets,
			Map<Long, AssetDecision> decisions) {
		return assets.stream()
				.filter(asset -> decisions.get(asset.asset().id()).matchStatus()
						== ReleaseAssetMatchStatus.MATCHED)
				.filter(asset -> eligible(decisions.get(asset.asset().id()).role()))
				.sorted((left, right) -> {
					int roleComparison = Integer.compare(
							roleRank(decisions.get(left.asset().id()).role()),
							roleRank(decisions.get(right.asset().id()).role()));
					if (roleComparison != 0) {
						return roleComparison;
					}
					int nameComparison = left.asset().name().compareToIgnoreCase(right.asset().name());
					return nameComparison != 0
							? nameComparison
							: Long.compare(left.asset().id(), right.asset().id());
				})
				.map(asset -> asset.asset().id())
				.toList();
	}

	private List<AnalyzedAsset> analyze(
			List<RepositoryReleaseAsset> assets,
			RuntimeEnvironment runtime,
			Map<Long, AssetDecision> decisions) {
		List<AnalyzedAsset> analyzed = new ArrayList<>();
		for (RepositoryReleaseAsset asset : assets) {
			AssetSignals signals = signals(asset.name());
			AssetDecision decision = new AssetDecision(
					role(signals),
					runtime == null ? null : match(signals, runtime),
					false,
					single(signals.operatingSystems()),
					single(signals.architectures()),
					signals.linuxPackageFamily());
			decisions.put(asset.id(), decision);
			analyzed.add(new AnalyzedAsset(asset, signals, decision));
		}
		return analyzed;
	}

	private List<LinuxPackageFamily> availableFamilies(
			List<AnalyzedAsset> assets,
			RuntimeEnvironment runtime) {
		Set<LinuxPackageFamily> families = EnumSet.noneOf(LinuxPackageFamily.class);
		for (AnalyzedAsset asset : assets) {
			if (eligible(asset.decision().role())
					&& asset.signals().linuxPackageFamily() != null
					&& matchesOsAndArchitecture(asset.signals(), runtime)) {
				families.add(asset.signals().linuxPackageFamily());
			}
		}
		return List.copyOf(families);
	}

	private boolean matchesOsAndArchitecture(
			AssetSignals signals,
			RuntimeEnvironment runtime) {
		return !signals.unreliable()
				&& !signals.unsupportedOperatingSystem()
				&& !signals.unsupportedArchitecture()
				&& signals.operatingSystems().size() == 1
				&& signals.operatingSystems().contains(runtime.operatingSystem())
				&& (signals.universalArchitecture()
						|| (signals.architectures().size() == 1
						&& signals.architectures().contains(runtime.architecture())));
	}

	private ReleaseAssetMatchStatus match(AssetSignals signals, RuntimeEnvironment runtime) {
		if (signals.unreliable()
				|| signals.operatingSystems().size() > 1
				|| signals.architectures().size() > 1) {
			return ReleaseAssetMatchStatus.UNABLE_TO_CONFIRM;
		}
		if (signals.unsupportedOperatingSystem() || signals.unsupportedArchitecture()) {
			return ReleaseAssetMatchStatus.NOT_MATCHED;
		}
		if (!signals.operatingSystems().isEmpty()
				&& !signals.operatingSystems().contains(runtime.operatingSystem())) {
			return ReleaseAssetMatchStatus.NOT_MATCHED;
		}
		if (!signals.architectures().isEmpty()
				&& !signals.architectures().contains(runtime.architecture())) {
			return ReleaseAssetMatchStatus.NOT_MATCHED;
		}
		if (signals.operatingSystems().isEmpty()
				|| (signals.architectures().isEmpty() && !signals.universalArchitecture())) {
			return ReleaseAssetMatchStatus.POSSIBLY_APPLICABLE;
		}
		if (runtime.operatingSystem() == RuntimeOperatingSystem.LINUX
				&& signals.linuxPackageFamily() != null) {
			if (runtime.linuxPackageFamily() == null) {
				return ReleaseAssetMatchStatus.POSSIBLY_APPLICABLE;
			}
			if (runtime.linuxPackageFamily() != signals.linuxPackageFamily()) {
				return ReleaseAssetMatchStatus.NOT_MATCHED;
			}
		}
		return ReleaseAssetMatchStatus.MATCHED;
	}

	private AssetSignals signals(String rawName) {
		String name = rawName == null ? "" : rawName.toLowerCase(Locale.ROOT);
		Set<RuntimeOperatingSystem> operatingSystems =
				EnumSet.noneOf(RuntimeOperatingSystem.class);
		Set<ProcessorArchitecture> architectures =
				EnumSet.noneOf(ProcessorArchitecture.class);

		boolean archPackage = name.endsWith(".pkg.tar.zst");
		boolean alpineContext = hasToken(name, "alpine") || hasToken(name, "linux");
		if (hasAnyToken(name, "windows", "win", "win32", "win64")
				|| endsWithAny(name, ".exe", ".msi", ".msix")) {
			operatingSystems.add(RuntimeOperatingSystem.WINDOWS);
		}
		if (hasAnyToken(name, "mac", "macos", "osx", "darwin")
				|| name.endsWith(".dmg")
				|| (name.endsWith(".pkg") && !archPackage)) {
			operatingSystems.add(RuntimeOperatingSystem.MACOS);
		}
		Set<LinuxPackageFamily> linuxPackageFamilies = linuxFamilies(name, alpineContext);
		LinuxPackageFamily linuxPackageFamily = single(linuxPackageFamilies);
		if (hasToken(name, "linux")
				|| !linuxPackageFamilies.isEmpty()
				|| endsWithAny(name, ".appimage", ".flatpak", ".snap")) {
			operatingSystems.add(RuntimeOperatingSystem.LINUX);
		}

		boolean x64 = hasAnyToken(name, "x64", "amd64", "x86_64", "x86-64", "win64");
		boolean arm64 = hasAnyToken(name, "arm64", "arm-64", "aarch64", "arm64v8");
		if (x64) {
			architectures.add(ProcessorArchitecture.X64);
		}
		if (arm64) {
			architectures.add(ProcessorArchitecture.ARM64);
		}
		boolean unsupportedArchitecture =
				(!x64 && hasAnyToken(name, "x86", "386", "i386", "i486", "i586", "i686"))
						|| (!arm64 && hasAnyToken(
								name, "arm", "armv6", "armv7", "armhf", "armel"))
						|| hasAnyToken(name, "s390x", "ppc64", "ppc64le", "riscv64");
		boolean unsupportedOperatingSystem = hasAnyToken(
				name, "android", "ios", "freebsd", "openbsd", "netbsd");
		boolean unresolvedLinuxAbi = operatingSystems.contains(RuntimeOperatingSystem.LINUX)
				&& linuxPackageFamilies.isEmpty()
				&& hasAnyToken(name, "musl", "gnu", "glibc");
		boolean conflictingLinuxPackageFamilies = linuxPackageFamilies.size() > 1;

		return new AssetSignals(
				name,
				operatingSystems,
				architectures,
				linuxPackageFamily,
				hasAnyToken(name, "universal", "universal2", "noarch"),
				unsupportedOperatingSystem,
				unsupportedArchitecture,
				conflictingLinuxPackageFamilies || unresolvedLinuxAbi);
	}

	private Set<LinuxPackageFamily> linuxFamilies(String name, boolean alpineContext) {
		Set<LinuxPackageFamily> families = EnumSet.noneOf(LinuxPackageFamily.class);
		if (name.endsWith(".pkg.tar.zst")
				|| hasAnyToken(name, "archlinux", "manjaro")) {
			families.add(LinuxPackageFamily.ARCH);
		}
		if (name.endsWith(".deb")
				|| hasAnyToken(name, "debian", "ubuntu", "mint")) {
			families.add(LinuxPackageFamily.DEB);
		}
		if (name.endsWith(".rpm")
				|| hasAnyToken(
						name,
						"fedora", "rhel", "centos", "rocky", "alma", "suse", "opensuse")) {
			families.add(LinuxPackageFamily.RPM);
		}
		if (hasToken(name, "alpine") || (name.endsWith(".apk") && alpineContext)) {
			families.add(LinuxPackageFamily.ALPINE);
		}
		return families;
	}

	private ReleaseAssetRole role(AssetSignals signals) {
		String name = signals.normalizedName();
		if (endsWithAny(
				name,
				".sha256", ".sha512", ".sha1", ".md5", ".sig", ".asc", ".checksum",
				".checksums", ".sum", ".blockmap", ".zsync", ".minisig", ".nupkg",
				".delta")
				|| hasAnyToken(
						name, "checksum", "checksums", "signature")
				|| (hasAnyToken(name, "latest", "app-update")
				&& endsWithAny(name, ".yml", ".yaml", ".json"))) {
			return ReleaseAssetRole.AUXILIARY;
		}
		boolean source = hasAnyToken(name, "source", "sources", "src");
		boolean portable = hasAnyToken(name, "portable", "standalone")
				|| name.endsWith(".appimage");
		boolean explicitInstaller = hasAnyToken(name, "setup", "installer");
		boolean installerExtension = endsWithAny(
				name, ".exe", ".msi", ".msix", ".dmg", ".pkg", ".deb", ".rpm",
				".flatpak", ".snap", ".pkg.tar.zst")
				|| (name.endsWith(".apk")
				&& signals.linuxPackageFamily() == LinuxPackageFamily.ALPINE);
		boolean archive = endsWithAny(
				name, ".zip", ".tar.gz", ".tgz", ".tar.xz", ".tar.bz2", ".tar.zst",
				".7z", ".rar");

		if (source) {
			return portable || explicitInstaller ? ReleaseAssetRole.UNKNOWN : ReleaseAssetRole.SOURCE;
		}
		if (portable) {
			return explicitInstaller ? ReleaseAssetRole.UNKNOWN : ReleaseAssetRole.PORTABLE;
		}
		if (explicitInstaller || installerExtension) {
			return ReleaseAssetRole.STANDARD_INSTALLER;
		}
		if (archive) {
			return ReleaseAssetRole.MANUAL_ARCHIVE;
		}
		return ReleaseAssetRole.UNKNOWN;
	}

	private boolean eligible(ReleaseAssetRole role) {
		return role == ReleaseAssetRole.STANDARD_INSTALLER
				|| role == ReleaseAssetRole.PORTABLE
				|| role == ReleaseAssetRole.MANUAL_ARCHIVE;
	}

	private int roleRank(ReleaseAssetRole role) {
		return switch (role) {
			case STANDARD_INSTALLER -> 0;
			case PORTABLE -> 1;
			case MANUAL_ARCHIVE -> 2;
			case AUXILIARY, SOURCE, UNKNOWN -> Integer.MAX_VALUE;
		};
	}

	private boolean hasAnyToken(String value, String... tokens) {
		for (String token : tokens) {
			if (hasToken(value, token)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasToken(String value, String token) {
		return Pattern.compile(
				"(^|[^a-z0-9])" + Pattern.quote(token) + "($|[^a-z0-9])")
				.matcher(value)
				.find();
	}

	private boolean endsWithAny(String value, String... suffixes) {
		for (String suffix : suffixes) {
			if (value.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	private <T> T single(Set<T> values) {
		return values.size() == 1 ? values.iterator().next() : null;
	}

	public record Advice(
			ReleaseRecommendationStatus status,
			RuntimeEnvironment runtime,
			List<LinuxPackageFamily> availableLinuxFamilies,
			Map<Long, AssetDecision> decisions,
			List<Long> orderedStableAssetIds,
			List<Long> orderedPrereleaseAssetIds) {

		public Advice {
			availableLinuxFamilies = List.copyOf(availableLinuxFamilies);
			decisions = Map.copyOf(decisions);
			orderedStableAssetIds = List.copyOf(orderedStableAssetIds);
			orderedPrereleaseAssetIds = List.copyOf(orderedPrereleaseAssetIds);
		}
	}

	public record AssetDecision(
			ReleaseAssetRole role,
			ReleaseAssetMatchStatus matchStatus,
			boolean directlyRecommended,
			RuntimeOperatingSystem detectedOperatingSystem,
			ProcessorArchitecture detectedArchitecture,
			LinuxPackageFamily detectedLinuxPackageFamily) {

		private AssetDecision withDirectRecommendation() {
			return new AssetDecision(
					role,
					matchStatus,
					true,
					detectedOperatingSystem,
					detectedArchitecture,
					detectedLinuxPackageFamily);
		}
	}

	private record AnalyzedAsset(
			RepositoryReleaseAsset asset,
			AssetSignals signals,
			AssetDecision decision) {
	}

	private record AssetSignals(
			String normalizedName,
			Set<RuntimeOperatingSystem> operatingSystems,
			Set<ProcessorArchitecture> architectures,
			LinuxPackageFamily linuxPackageFamily,
			boolean universalArchitecture,
			boolean unsupportedOperatingSystem,
			boolean unsupportedArchitecture,
			boolean unreliable) {
	}
}
