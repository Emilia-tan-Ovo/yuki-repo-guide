package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAsset;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAssetAssessment;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAssetEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseChannel;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseRecommendation;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSummary;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseWarning;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRelease;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleaseAsset;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleases;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public final class ReleaseInterpreter {
	private static final int MAX_VISIBLE_ASSETS = 50;
	private static final int MAX_VISIBLE_MATCHES = 50;
	private static final String SOURCE = "GitHub Releases REST API";
	private final ReleaseAssetAdvisor assetAdvisor;

	public ReleaseInterpreter(ReleaseAssetAdvisor assetAdvisor) {
		this.assetAdvisor = assetAdvisor;
	}

	public ReleaseSection interpret(RepositoryReleases releases) {
		return interpret(releases, null);
	}

	public ReleaseSection recommend(
			RepositoryReleases releases,
			RuntimeEnvironment runtime) {
		return interpret(releases, runtime);
	}

	private ReleaseSection interpret(
			RepositoryReleases releases,
			RuntimeEnvironment runtime) {
        return interpret(releases, runtime, MAX_VISIBLE_ASSETS, MAX_VISIBLE_MATCHES);
    }

    /** Selection must see every eligible asset before the experience response is clipped. */
    public ReleaseSection forExperience(RepositoryReleases releases, RuntimeEnvironment runtime) {
        return interpret(releases, runtime, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private ReleaseSection interpret(RepositoryReleases releases, RuntimeEnvironment runtime, int assetLimit, int matchLimit) {
		List<RepositoryRelease> published = releases.items().stream()
				.filter(release -> !release.draft())
				.toList();
		if (published.isEmpty()) {
			return ReleaseSection.notProvided();
		}

		published.forEach(this::validatePublishedRelease);
		Comparator<RepositoryRelease> newestPublished = Comparator
				.comparing(RepositoryRelease::publishedAt)
				.thenComparingLong(RepositoryRelease::id);
		RepositoryRelease latestStable = published.stream()
				.filter(release -> !release.prerelease())
				.max(newestPublished)
				.orElse(null);
		RepositoryRelease latestPrerelease = published.stream()
				.filter(RepositoryRelease::prerelease)
				.max(newestPublished)
				.orElse(null);
		ReleaseAssetAdvisor.Advice advice = assetAdvisor.advise(
				assetsOf(latestStable), assetsOf(latestPrerelease), runtime);

		Map<String, GuideEvidence> evidence = new LinkedHashMap<>();
		SummaryResult stable = summary(
				latestStable,
				ReleaseChannel.STABLE,
				advice.orderedStableAssetIds(),
				advice,
				matchLimit, assetLimit,
				evidence);
		int remainingMatches = matchLimit - stable.visibleMatchingAssetCount();
		SummaryResult prerelease = summary(
				latestPrerelease,
				ReleaseChannel.PRERELEASE,
				advice.orderedPrereleaseAssetIds(),
				advice,
				remainingMatches, assetLimit,
				evidence);
		return ReleaseSection.available(
				stable.summary(),
				prerelease.summary(),
				new ReleaseRecommendation(
						advice.status(), advice.runtime(), advice.availableLinuxFamilies()),
				evidence);
	}

	private List<RepositoryReleaseAsset> assetsOf(RepositoryRelease release) {
		return release == null ? List.of() : release.assets();
	}

	private void validatePublishedRelease(RepositoryRelease release) {
		if (release.id() <= 0
				|| release.tagName() == null
				|| release.tagName().isBlank()
				|| release.releaseUrl() == null
				|| release.publishedAt() == null) {
			throw new GitHubSourceException(GuideErrorCode.GITHUB_UPSTREAM_FAILURE);
		}
	}

	private SummaryResult summary(
			RepositoryRelease release,
			ReleaseChannel channel,
			List<Long> orderedMatchingAssetIds,
			ReleaseAssetAdvisor.Advice advice,
			int matchingLimit,
            int assetLimit,
			Map<String, GuideEvidence> evidence) {
		if (release == null) {
			return new SummaryResult(null, 0);
		}
		String name = release.name() == null || release.name().isBlank()
				? release.tagName()
				: release.name();
		String releaseEvidenceId = "github-release-" + release.id();
		evidence.put(releaseEvidenceId, new ReleaseEvidence(
				releaseEvidenceId,
				SOURCE,
				release.releaseUrl(),
				release.id(),
				release.tagName(),
				release.publishedAt(),
				channel,
				release.reportedAssetCount()));

		List<RepositoryReleaseAsset> sortedAssets = release.assets().stream()
				.sorted(Comparator
						.comparing((RepositoryReleaseAsset asset) ->
								asset.name().toLowerCase(Locale.ROOT))
						.thenComparing(RepositoryReleaseAsset::name)
						.thenComparingLong(RepositoryReleaseAsset::id))
				.toList();
		List<ReleaseAsset> visibleAssets = sortedAssets.stream()
				.limit(assetLimit)
				.map(asset -> visibleAsset(
						asset, releaseEvidenceId, advice.decisions().get(asset.id()), evidence))
				.toList();
		Map<Long, RepositoryReleaseAsset> assetsById = new LinkedHashMap<>();
		release.assets().forEach(asset -> assetsById.put(asset.id(), asset));
		List<ReleaseAsset> matchingAssets = orderedMatchingAssetIds.stream()
				.limit(matchingLimit)
				.map(assetsById::get)
				.filter(java.util.Objects::nonNull)
				.map(asset -> visibleAsset(
						asset, releaseEvidenceId, advice.decisions().get(asset.id()), evidence))
				.toList();
		List<ReleaseWarning> warnings = new ArrayList<>();
		if (channel == ReleaseChannel.PRERELEASE) {
			warnings.add(ReleaseWarning.PRERELEASE);
		}
		if (release.excludedAssetCount() > 0 || sortedAssets.size() > assetLimit) {
			warnings.add(ReleaseWarning.SOME_ASSETS_OMITTED);
		}
		return new SummaryResult(new ReleaseSummary(
				name,
				release.tagName(),
				release.publishedAt(),
				visibleAssets,
				matchingAssets,
				orderedMatchingAssetIds.size(),
				orderedMatchingAssetIds.size() > matchingAssets.size(),
				release.reportedAssetCount(),
				release.excludedAssetCount(),
				sortedAssets.size() > assetLimit,
				warnings,
				releaseEvidenceId), matchingAssets.size());
	}

	private ReleaseAsset visibleAsset(
			RepositoryReleaseAsset asset,
			String releaseEvidenceId,
			ReleaseAssetAdvisor.AssetDecision decision,
			Map<String, GuideEvidence> evidence) {
		String evidenceId = "github-release-asset-" + asset.id();
		evidence.put(evidenceId, new ReleaseAssetEvidence(
				evidenceId,
				SOURCE,
				releaseEvidenceId,
				asset.id(),
				asset.name(),
				asset.sizeBytes(),
				asset.downloadUrl()));
		ReleaseAssetAssessment assessment = decision.matchStatus() == null
				? null
				: new ReleaseAssetAssessment(
						decision.matchStatus(),
						decision.directlyRecommended(),
						decision.detectedOperatingSystem(),
						decision.detectedArchitecture(),
						decision.detectedLinuxPackageFamily());
		return new ReleaseAsset(
				asset.name(),
				asset.sizeBytes(),
				asset.downloadUrl(),
				evidenceId,
				decision.role(),
				assessment);
	}

	private record SummaryResult(ReleaseSummary summary, int visibleMatchingAssetCount) {
	}
}
