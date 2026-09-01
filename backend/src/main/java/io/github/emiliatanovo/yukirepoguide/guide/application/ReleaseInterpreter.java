package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAsset;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseAssetEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseChannel;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSummary;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseWarning;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRelease;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleaseAsset;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReleases;

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
	private static final String SOURCE = "GitHub Releases REST API";

	public ReleaseSection interpret(RepositoryReleases releases) {
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

		Map<String, GuideEvidence> evidence = new LinkedHashMap<>();
		return ReleaseSection.available(
				summary(latestStable, ReleaseChannel.STABLE, evidence),
				summary(latestPrerelease, ReleaseChannel.PRERELEASE, evidence),
				evidence);
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

	private ReleaseSummary summary(
			RepositoryRelease release,
			ReleaseChannel channel,
			Map<String, GuideEvidence> evidence) {
		if (release == null) {
			return null;
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
				.limit(MAX_VISIBLE_ASSETS)
				.map(asset -> visibleAsset(asset, releaseEvidenceId, evidence))
				.toList();
		List<ReleaseWarning> warnings = new ArrayList<>();
		if (channel == ReleaseChannel.PRERELEASE) {
			warnings.add(ReleaseWarning.PRERELEASE);
		}
		if (release.excludedAssetCount() > 0 || sortedAssets.size() > MAX_VISIBLE_ASSETS) {
			warnings.add(ReleaseWarning.SOME_ASSETS_OMITTED);
		}
		return new ReleaseSummary(
				name,
				release.tagName(),
				release.publishedAt(),
				visibleAssets,
				release.reportedAssetCount(),
				release.excludedAssetCount(),
				sortedAssets.size() > MAX_VISIBLE_ASSETS,
				warnings,
				releaseEvidenceId);
	}

	private ReleaseAsset visibleAsset(
			RepositoryReleaseAsset asset,
			String releaseEvidenceId,
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
		return new ReleaseAsset(
				asset.name(), asset.sizeBytes(), asset.downloadUrl(), evidenceId);
	}
}
