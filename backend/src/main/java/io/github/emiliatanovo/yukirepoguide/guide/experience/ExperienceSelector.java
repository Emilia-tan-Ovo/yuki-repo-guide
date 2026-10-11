package io.github.emiliatanovo.yukirepoguide.guide.experience;

import io.github.emiliatanovo.yukirepoguide.guide.application.ReleaseInterpreter;
import io.github.emiliatanovo.yukirepoguide.guide.domain.*;
import java.util.*;
import static io.github.emiliatanovo.yukirepoguide.guide.experience.ExperienceResult.*;

/** Deterministic selection over already acquired material; no external I/O. */
public final class ExperienceSelector {
    private final ReleaseInterpreter releases;
    public ExperienceSelector(ReleaseInterpreter releases) { this.releases = releases; }

    public ExperienceResult select(ExperienceInput input, RuntimeEnvironment runtime) {
        var evidence = new LinkedHashMap<String, Evidence>();
        var paths = new ArrayList<Path>();
        var unresolved = new ArrayList<String>();
        var readme = input.readme();
        readme.evidence().forEach((id, item) -> evidence.put(id,
                new Evidence(item.source(), item.readmeUrl(), item.context(), item.path(), item.sha())));
        var online = readme.candidates().stream().map(candidate -> new Option(candidate.label(), candidate.url(),
                List.of(candidate.evidenceId()), candidate.warnings().stream().map(Enum::name).toList())).toList();
        add(paths, "online", Kind.ONLINE, true, online);
        if (readme.status() == ReadmeSectionStatus.FAILED) unresolved.add("README_UNAVAILABLE");
        var section = input.releases().status() == ReleaseSectionStatus.FAILED ? input.releases()
                : releases.forExperience(input.releaseSource(), runtime);
        if (section.status() == ReleaseSectionStatus.FAILED) unresolved.add("RELEASES_UNAVAILABLE");
        else if (section.status() == ReleaseSectionStatus.AVAILABLE) {
            if (runtime == null) {
                boolean hasDownload = java.util.stream.Stream.of(section.latestStable(), section.latestPrerelease())
                        .filter(Objects::nonNull).flatMap(release -> release.assets().stream()).anyMatch(asset -> asset.role().isExperienceResource());
                if (hasDownload) unresolved.add("RUNTIME_REQUIRED");
            }
            else {
                if (section.recommendation().status() == ReleaseRecommendationStatus.NEEDS_LINUX_FAMILY)
                    unresolved.add("LINUX_FAMILY_REQUIRED");
                collectRelease(paths, evidence, section, section.latestStable(), false);
                collectRelease(paths, evidence, section, section.latestPrerelease(), true);
            }
        }
        collectQuickStart(paths, evidence, unresolved, input);
        var primary = paths.stream().filter(Path::reliable).findFirst().orElse(null);
        var alternatives = paths.stream().filter(path -> path != primary)
                .sorted(Comparator.comparing(Path::reliable).reversed()).toList();
        var referenced = new HashSet<String>();
        paths.forEach(path -> path.options().forEach(option -> referenced.addAll(option.evidenceIds())));
        evidence.keySet().retainAll(referenced);
        return new ExperienceResult(primary != null ? Status.PRIMARY_AVAILABLE
                : !alternatives.isEmpty() ? Status.CANDIDATES_ONLY
                : !unresolved.isEmpty() ? Status.UNDETERMINED : Status.NO_PATH,
                primary, alternatives, unresolved, evidence);
    }

    private void collectRelease(List<Path> paths, Map<String, Evidence> evidence, ReleaseSection section,
            ReleaseSummary release, boolean prerelease) {
        if (release == null) return;
        var direct = new ArrayList<Option>();
        var other = new ArrayList<Option>();
        var possible = new ArrayList<Option>();
        var releaseEvidence = (ReleaseEvidence) section.evidence().get(release.evidenceId());
        for (var asset : release.assets()) {
            var assessment = asset.assessment();
            if (assessment == null || !asset.role().isExperienceResource()) continue;
            var match = assessment.matchStatus();
            if (match != ReleaseAssetMatchStatus.MATCHED && match != ReleaseAssetMatchStatus.POSSIBLY_APPLICABLE) continue;
            evidence.put(asset.evidenceId(), new Evidence("GitHub Release Asset", asset.downloadUrl(),
                    asset.name() + " · " + asset.sizeBytes() + " bytes · " + asset.role() + " · " + match
                    + " · " + section.recommendation().runtime(), null, null));
            evidence.put(release.evidenceId(), new Evidence(releaseEvidence.source(), releaseEvidence.releaseUrl(),
                    release.tagName() + " · " + releaseEvidence.channel() + " · " + release.publishedAt(), null, null));
            var warnings = new ArrayList<String>();
            warnings.add("DOWNLOAD_NOT_VERIFIED");
            warnings.add("MATCH_NOT_GUARANTEED");
            release.warnings().forEach(warning -> warnings.add(warning.name()));
            if (match == ReleaseAssetMatchStatus.POSSIBLY_APPLICABLE) warnings.add("POSSIBLY_APPLICABLE");
            var option = new Option(asset.name(), asset.downloadUrl(), List.of(asset.evidenceId(), release.evidenceId()), warnings);
            if (match == ReleaseAssetMatchStatus.POSSIBLY_APPLICABLE) possible.add(option);
            else if (assessment.directlyRecommended()) direct.add(option);
            else other.add(option);
        }
        Kind kind = prerelease ? Kind.PRERELEASE : Kind.STABLE_RELEASE;
        String prefix = prerelease ? "prerelease" : "stable";
        add(paths, prefix + "-recommended", kind, !prerelease, direct);
        add(paths, prefix + "-other", kind, !prerelease, other);
        add(paths, prefix + "-possible", kind, false, possible);
    }

    private void collectQuickStart(List<Path> paths, Map<String, Evidence> evidence, List<String> unresolved,
            ExperienceInput input) {
        var quick = input.quickStart();
        if (quick == null) {
            if (input.explanationSourceChanged()) unresolved.add("QUICK_START_SOURCE_CHANGED");
            else if (input.readme().status() == ReadmeSectionStatus.AVAILABLE) unresolved.add("QUICK_START_PENDING");
            else if (input.readme().status() == ReadmeSectionStatus.FAILED) unresolved.add("QUICK_START_UNAVAILABLE");
            return;
        }
        if (quick.status() == io.github.emiliatanovo.yukirepoguide.guide.quickstart.QuickStartResult.Status.UNAVAILABLE) {
            unresolved.add("QUICK_START_UNAVAILABLE");
            return;
        }
        if (quick.contentStatus() == io.github.emiliatanovo.yukirepoguide.guide.quickstart.QuickStartResult.ContentStatus.NOT_PROVIDED) return;
        quick.evidence().forEach((id, item) -> evidence.put(id,
                new Evidence("README · Quick Start", item.sourceUrl(), item.text(), item.path(), item.sha())));
        boolean complete = quick.contentStatus() == io.github.emiliatanovo.yukirepoguide.guide.quickstart.QuickStartResult.ContentStatus.COMPLETE;
        var warnings = new ArrayList<String>();
        warnings.add("COMMANDS_NOT_VERIFIED");
        if (!complete) {
            warnings.add("INCOMPLETE_QUICK_START");
            quick.gaps().forEach(gap -> warnings.add(gap.text()));
        }
        add(paths, "quick-start", Kind.QUICK_START, complete, List.of(new Option("查看 Quick Start", "#quick-start",
                new ArrayList<>(quick.evidence().keySet()), warnings)));
    }

    private static void add(List<Path> paths, String id, Kind kind, boolean reliable, List<Option> options) {
        if (!options.isEmpty()) paths.add(new Path(id, kind, reliable, options));
    }
}
