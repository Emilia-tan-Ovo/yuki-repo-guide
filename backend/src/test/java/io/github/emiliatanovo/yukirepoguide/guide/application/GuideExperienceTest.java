package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.*;
import io.github.emiliatanovo.yukirepoguide.guide.explanation.*;
import io.github.emiliatanovo.yukirepoguide.guide.quickstart.*;
import io.github.emiliatanovo.yukirepoguide.guide.support.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GuideExperienceTest {
    private final RepositoryRef repository = new RepositoryRef("octo", "notes");
    private FakeRepositoryReadmeSource readme = FakeRepositoryReadmeSource.withReadme(
            new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                    "[Try online](https://example.com/demo)"));
    private final FakeRepositoryReleaseSource releases = FakeRepositoryReleaseSource.withoutReleases();
    private final MutableClock clock = new MutableClock();

    @Test
    void offersOnlineExperienceWithEvidenceWithoutCallingSourcesOrWaitingForAi() {
        var service = service((request, remaining) -> { throw new AssertionError("AI must not run"); });
        var guide = service.createGuide("url", "owner");
        var result = service.experiencePath(guide.experience(), "owner", null);
        assertThat(result.status().name()).isEqualTo("PRIMARY_AVAILABLE");
        assertThat(result.primary().kind().name()).isEqualTo("ONLINE");
        assertThat(result.primary().options()).singleElement().satisfies(option -> {
            assertThat(option.url()).isEqualTo("https://example.com/demo");
            assertThat(option.warnings()).contains("EXTERNAL_SITE_NOT_VERIFIED");
            assertThat(result.evidence()).containsKey(option.evidenceIds().getFirst());
        });
        assertThat(readme.requests()).isEqualTo(1);
        assertThat(releases.requests()).isEqualTo(1);
    }

    private GuideService service(ExplanationModel model) {
        var settings = new ExplanationSettings(Duration.ofMinutes(10), 2, Duration.ofSeconds(30), 12000, 300, 32768);
        return new GuideService(new FakeRepositoryUrlParser(repository),
                FakeRepositoryFactsSource.withMetadata(new RepositoryFacts(repository, "笔记应用", 0, Instant.EPOCH, null, null)),
                readme, releases, new OnlineExperienceRecognizer(), new ReleaseInterpreter(new ReleaseAssetAdvisor()),
                new ExplanationSnapshots(settings, clock), new IntroductionGenerator(model, settings, clock),
                new ExplanationInputSelector(settings), new QuickStartInputSelector(QuickStartSettings.defaults()),
                new QuickStartGenerator(model, QuickStartSettings.defaults(), clock));
    }

    @Test
    void selectsTiedStableInstallersAndKeepsPossibleCandidatesBeyondDisplayLimit() {
        readme.returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md", "Notes"));
        var assets = new java.util.ArrayList<RepositoryReleaseAsset>();
        for (int i = 1; i <= 55; i++) assets.add(asset(i, "a" + i + ".sha256"));
        assets.add(asset(100, "z-windows-x64-setup.exe"));
        assets.add(asset(101, "z-windows-x64-setup.msi"));
        assets.add(asset(102, "z-windows-setup.exe"));
        releases.returning(new RepositoryReleases(java.util.List.of(release(1, false, assets))));
        var service = service((request, remaining) -> { throw new AssertionError("AI must not run"); });
        var guide = service.createGuide("url", "owner");
        var result = service.experiencePath(guide.experience(), "owner", windows());
        assertThat(result.primary().kind().name()).isEqualTo("STABLE_RELEASE");
        assertThat(result.primary().options()).extracting(option -> option.label())
                .containsExactly("z-windows-x64-setup.exe", "z-windows-x64-setup.msi");
        assertThat(result.alternatives()).singleElement().satisfies(path -> {
            assertThat(path.reliable()).isFalse();
            assertThat(path.options()).singleElement().satisfies(option -> {
                assertThat(option.label()).isEqualTo("z-windows-setup.exe");
                assertThat(option.warnings()).contains("POSSIBLY_APPLICABLE");
            });
        });
        var mac = service.experiencePath(guide.experience(), "owner",
                new RuntimeEnvironment(RuntimeOperatingSystem.MACOS, ProcessorArchitecture.ARM64, null));
        assertThat(mac.primary()).isNull();
        assertThat(releases.requests()).isEqualTo(1);
    }

    private RuntimeEnvironment windows() {
        return new RuntimeEnvironment(RuntimeOperatingSystem.WINDOWS, ProcessorArchitecture.X64, null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"missing,NO_PATH", "auxiliary,NO_PATH", "prerelease,CANDIDATES_ONLY", "failed,UNDETERMINED"})
    void distinguishesAbsentWarnedAndUnresolvedPaths(String scenario, String expected) {
        readme = FakeRepositoryReadmeSource.withoutReadme();
        if (scenario.equals("auxiliary")) releases.returning(new RepositoryReleases(java.util.List.of(
                release(1, false, java.util.List.of(asset(1, "SHA256SUMS"))))));
        if (scenario.equals("prerelease")) releases.returning(new RepositoryReleases(java.util.List.of(
                release(1, true, java.util.List.of(asset(1, "windows-x64-setup.exe"))))));
        if (scenario.equals("failed")) releases.failingWith(new GitHubSourceException(GuideErrorCode.GITHUB_TIMEOUT));
        var service = service((request, remaining) -> { throw new AssertionError("AI must not run"); });
        var guide = service.createGuide("url", "owner");
        var result = service.experiencePath(guide.experience(), "owner", scenario.equals("prerelease") ? windows() : null);
        assertThat(result.status().name()).isEqualTo(expected);
        assertThat(result.primary()).isNull();
        if (scenario.equals("prerelease")) assertThat(result.alternatives().getFirst().options().getFirst().warnings()).contains("PRERELEASE");
    }

    @Test
    void usesTheGeneratedQuickStartThenRecomputesForConfirmedRuntime() {
        readme.returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                "## Quick Start\n\n启动应用：\n\n```sh\njava -jar app.jar\n```\n"));
        releases.returning(new RepositoryReleases(java.util.List.of(release(1, false,
                java.util.List.of(asset(1, "windows-x64-setup.exe"))))));
        var service = service((request, remaining) -> """
            {"status":"COMPLETE","requirements":[],"steps":[{"text":"启动应用","evidenceIds":["qs-1","qs-2"],"blockIds":["qs-2"]}],
            "configuration":[],"cautions":[],"gaps":[]}
            """);
        var guide = service.createGuide("url", "owner");
        var quick = service.quickStart(guide.explanationInputId(), "owner");
        var refs = guide.experience().withQuickStart(quick.resultId());
        var before = service.experiencePath(refs, "owner", null);
        assertThat(before.primary().kind().name()).isEqualTo("QUICK_START");
        assertThat(before.unresolved()).contains("RUNTIME_REQUIRED");
        var after = service.experiencePath(refs, "owner", windows());
        assertThat(after.primary().kind().name()).isEqualTo("STABLE_RELEASE");
        assertThat(after.alternatives()).extracting(path -> path.kind().name()).contains("QUICK_START");
        assertThat(after.evidence().get("qs-2").detail()).contains("java -jar app.jar");
        assertThat(readme.requests()).isEqualTo(1);
        assertThat(releases.requests()).isEqualTo(1);
    }

    private RepositoryReleaseAsset asset(long id, String name) {
        return new RepositoryReleaseAsset(id, name, 100, "https://github.com/octo/notes/releases/download/v1/" + name);
    }

    @Test
    void bindsImmutableRegionVersionsToTheGuideSessionAndReadmeSource() {
        var service = service((request, remaining) -> "{\"status\":\"NOT_PROVIDED\",\"requirements\":[],\"steps\":[],\"configuration\":[],\"cautions\":[],\"gaps\":[]}");
        var guide = service.createGuide("url", "owner");
        var quick = service.quickStart(guide.explanationInputId(), "owner");
        readme.returning(new RepositoryReadme("README.md", "def", "https://github.com/octo/notes/blob/main/README.md",
                "[Try online](https://example.com/new)"));
        var updated = service.retryReadme(repository.canonicalUrl(), guide.explanationInputId(), "owner");
        var refs = new io.github.emiliatanovo.yukirepoguide.guide.experience.ExperienceReferences(
                guide.explanationInputId(), updated.resultId(), guide.experience().releasesResultId(), null);
        assertThat(service.experiencePath(refs, "owner", null).primary().options().getFirst().url()).endsWith("/new");
        assertThat(service.experiencePath(guide.experience(), "owner", null).primary().options().getFirst().url()).endsWith("/demo");
        assertThatThrownBy(() -> service.experiencePath(refs.withQuickStart(quick.resultId()), "owner", null))
                .hasMessage("EXPERIENCE_SOURCE_MISMATCH");
        assertThatThrownBy(() -> service.experiencePath(refs, "other", null)).hasMessage("EXPERIENCE_INPUT_EXPIRED");
        clock.now = clock.now.plusSeconds(601);
        assertThatThrownBy(() -> service.experiencePath(refs, "owner", null)).hasMessage("EXPERIENCE_INPUT_EXPIRED");
    }

    private RepositoryRelease release(long id, boolean prerelease, java.util.List<RepositoryReleaseAsset> assets) {
        return new RepositoryRelease(id, "v" + id, "v" + id, "https://github.com/octo/notes/releases/tag/v" + id,
                Instant.EPOCH.plusSeconds(id), false, prerelease, assets.size(), 0, assets);
    }

    @Test
    void keepsIncompleteQuickStartAsAWarnedCandidate() {
        readme.returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                "## Quick Start\n\n启动应用前需要配置数据库，本文未提供地址。\n"));
        var service = service((request, remaining) -> """
            {"status":"INCOMPLETE","requirements":[],"steps":[{"text":"准备数据库配置","evidenceIds":["qs-1"],"blockIds":[]}],
            "configuration":[],"cautions":[],"gaps":[{"text":"缺少数据库地址","evidenceIds":["qs-1"],"blockIds":[]}]}
            """);
        var guide = service.createGuide("url", "owner");
        var quick = service.quickStart(guide.explanationInputId(), "owner");
        var selected = service.experiencePath(guide.experience().withQuickStart(quick.resultId()), "owner", null);
        assertThat(selected.status().name()).isEqualTo("CANDIDATES_ONLY");
        assertThat(selected.primary()).isNull();
        assertThat(selected.alternatives().getFirst().options().getFirst().warnings()).contains("缺少数据库地址");
    }

    @Test
    void canSelectWhileQuickStartIsRunningAndKeepsTheActiveGuideFromEviction() throws Exception {
        readme.returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                "[Demo](https://example.com/demo)\n\n## Quick Start\n\n启动应用。\n"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var finish = new java.util.concurrent.CountDownLatch(1);
        var service = service((request, remaining) -> {
            entered.countDown();
            try { if (!finish.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("generation blocked"); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            return "{\"status\":\"NOT_PROVIDED\",\"requirements\":[],\"steps\":[],\"configuration\":[],\"cautions\":[],\"gaps\":[]}";
        });
        var guide = service.createGuide("url", "owner");
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var generating = executor.submit(() -> service.quickStart(guide.explanationInputId(), "owner"));
            try {
                assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                for (int i = 0; i < 3; i++) service.createGuide("url", "other");
                var selection = executor.submit(() -> service.experiencePath(guide.experience(), "owner", null));
                assertThat(selection.get(1, java.util.concurrent.TimeUnit.SECONDS).primary().kind().name()).isEqualTo("ONLINE");
            } finally { finish.countDown(); }
            assertThat(generating.get().resultId()).isNotBlank();
        }
    }

    @Test
    void reportsClippingAfterSelectingFromAllAssets() {
        readme = FakeRepositoryReadmeSource.withoutReadme();
        var assets = java.util.stream.IntStream.rangeClosed(1, 60)
                .mapToObj(i -> asset(i, "app-" + i + "-windows-x64-setup.exe")).toList();
        releases.returning(new RepositoryReleases(java.util.List.of(release(1, false, assets))));
        var service = service((request, remaining) -> { throw new AssertionError("AI must not run"); });
        var guide = service.createGuide("url", "owner");
        var selected = service.experiencePath(guide.experience(), "owner", windows());
        assertThat(selected.primary().optionCount()).isEqualTo(60);
        assertThat(selected.primary().options()).hasSize(50);
        assertThat(selected.evidence()).hasSize(51);
    }

    @Test
    void registersReleaseUpdatesWithoutMutatingEarlierResultsAndBoundsRetainedVersions() {
        var service = service((request, remaining) -> { throw new AssertionError("AI must not run"); });
        var guide = service.createGuide("url", "owner");
        releases.returning(new RepositoryReleases(java.util.List.of(release(1, false,
                java.util.List.of(asset(1, "windows-x64-setup.exe"))))));
        var update = service.recommendReleases(repository.canonicalUrl(), windows(), guide.explanationInputId(), "owner");
        var refs = new io.github.emiliatanovo.yukirepoguide.guide.experience.ExperienceReferences(
                guide.explanationInputId(), guide.experience().readmeResultId(), update.resultId(), null);
        assertThat(service.experiencePath(refs, "owner", windows()).alternatives())
                .extracting(path -> path.kind().name()).contains("STABLE_RELEASE");
        assertThat(service.experiencePath(guide.experience(), "owner", windows()).alternatives()).isEmpty();
        for (int i = 0; i < 8; i++) service.retryReadme(repository.canonicalUrl(), guide.explanationInputId(), "owner");
        assertThatThrownBy(() -> service.experiencePath(refs, "owner", windows())).hasMessage("EXPERIENCE_INPUT_EXPIRED");
    }

    @Test
    void ranksCompleteQuickStartAheadOfWarnedReleaseAlternatives() {
        readme.returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                "[Try online](https://example.com/demo)\n\n## Quick Start\n\n启动应用：\n\n```sh\njava -jar app.jar\n```\n"));
        releases.returning(new RepositoryReleases(java.util.List.of(release(1, true,
                java.util.List.of(asset(1, "windows-x64-setup.exe"))))));
        var service = service((request, remaining) -> """
            {"status":"COMPLETE","requirements":[],"steps":[{"text":"启动应用","evidenceIds":["qs-2","qs-3"],"blockIds":["qs-3"]}],
            "configuration":[],"cautions":[],"gaps":[]}
            """);
        var guide = service.createGuide("url", "owner");
        var quick = service.quickStart(guide.explanationInputId(), "owner");
        var selected = service.experiencePath(guide.experience().withQuickStart(quick.resultId()), "owner", windows());
        assertThat(selected.primary().kind().name()).isEqualTo("ONLINE");
        assertThat(selected.alternatives()).extracting(path -> path.kind().name()).containsExactly("QUICK_START", "PRERELEASE");
    }

    @Test
    void registersSourceFailuresAndPreservesOtherReliablePaths() {
        releases.returning(new RepositoryReleases(java.util.List.of(release(1, false,
                java.util.List.of(asset(1, "windows-x64-setup.exe"))))));
        var service = service((request, remaining) -> { throw new AssertionError("AI must not run"); });
        var guide = service.createGuide("url", "owner");
        readme.failingWith(new ReadmeContentUnsupportedException());
        var failedReadme = service.retryReadme(repository.canonicalUrl(), guide.explanationInputId(), "owner");
        var readmeRefs = new io.github.emiliatanovo.yukirepoguide.guide.experience.ExperienceReferences(
                guide.explanationInputId(), failedReadme.resultId(), guide.experience().releasesResultId(), null);
        var download = service.experiencePath(readmeRefs, "owner", windows());
        assertThat(download.primary().kind().name()).isEqualTo("STABLE_RELEASE");
        assertThat(download.unresolved()).contains("README_UNAVAILABLE");
        releases.failingWith(new ReleaseHistoryUnsupportedException());
        var failedRelease = service.retryReleases(repository.canonicalUrl(), guide.explanationInputId(), "owner");
        var releaseRefs = new io.github.emiliatanovo.yukirepoguide.guide.experience.ExperienceReferences(
                guide.explanationInputId(), guide.experience().readmeResultId(), failedRelease.resultId(), null);
        var online = service.experiencePath(releaseRefs, "owner", windows());
        assertThat(online.primary().kind().name()).isEqualTo("ONLINE");
        assertThat(online.unresolved()).contains("RELEASES_UNAVAILABLE");
        var failedRecommendation = service.recommendReleases(repository.canonicalUrl(), windows(), guide.explanationInputId(), "owner");
        assertThat(failedRecommendation.value().status()).isEqualTo(ReleaseSectionStatus.FAILED);
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
