package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.*;
import io.github.emiliatanovo.yukirepoguide.guide.explanation.*;
import io.github.emiliatanovo.yukirepoguide.guide.quickstart.*;
import io.github.emiliatanovo.yukirepoguide.guide.support.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GuideQuickStartTest {
    private static final String VALID = """
        {"status":"COMPLETE","requirements":[],"steps":[{"text":"启动应用","evidenceIds":["qs-1","qs-2"],"blockIds":["qs-2"]}],
        "configuration":[],"cautions":[],"gaps":[]}
        """;

    @Test
    void returnsOriginalCommandsFromTheSnapshotWithoutFetchingAgain() {
        var readme = FakeRepositoryReadmeSource.withReadme(new RepositoryReadme("README.md", "abc",
                "https://github.com/octo/notes/blob/main/README.md", "## Quick Start\n\n启动应用：\n\n```sh\njava -jar app.jar\n```\n"));
        var service = service(readme, (request, remaining) -> VALID);
        var guide = service.createGuide("url", "owner");
        var result = service.quickStart(guide.explanationInputId(), "owner");
        assertThat(result.status()).isEqualTo(QuickStartResult.Status.AVAILABLE);
        assertThat(result.contentStatus()).isEqualTo(QuickStartResult.ContentStatus.COMPLETE);
        assertThat(result.steps().getFirst().blocks().getFirst().text()).isEqualTo("java -jar app.jar\n");
        assertThat(result.evidence().get("qs-2").sha()).isEqualTo("abc");
        assertThat(readme.requests()).isEqualTo(1);
    }

    private GuideService service(FakeRepositoryReadmeSource readme, ExplanationModel model) {
        return service(readme, model, Clock.systemUTC(), QuickStartSettings.defaults());
    }

    @Test
    void doesNotCallModelForMissingOrFailedReadme() {
        var model = (ExplanationModel) (request, remaining) -> { throw new AssertionError("must not call model"); };
        var missing = service(FakeRepositoryReadmeSource.withoutReadme(), model);
        assertThat(missing.quickStart(missing.createGuide("url", "a").explanationInputId(), "a").contentStatus())
                .isEqualTo(QuickStartResult.ContentStatus.NOT_PROVIDED);
        var failed = service(FakeRepositoryReadmeSource.withoutReadme()
                .failingWith(new ReadmeContentUnsupportedException()), model);
        assertThat(failed.quickStart(failed.createGuide("url", "a").explanationInputId(), "a").code())
                .isEqualTo("QUICK_START_SOURCE_UNAVAILABLE");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"unknown", "wrong-type", "invented-command", "incomplete-without-gap", "duplicate-field", "trailing-json"})
    void rejectsInvalidOutputAsAWholeWithOneCorrection(String scenario) {
        String output = switch (scenario) {
            case "unknown" -> VALID.replace("qs-2", "invented");
            case "wrong-type" -> VALID.replace("\"blockIds\":[\"qs-2\"]", "\"blockIds\":[\"qs-1\"]");
            case "invented-command" -> VALID.replace("\"blockIds\":[\"qs-2\"]", "\"blockIds\":[\"qs-2\"],\"command\":\"evil\"");
            case "incomplete-without-gap" -> VALID.replace("COMPLETE", "INCOMPLETE");
            case "duplicate-field" -> VALID.replace("\"status\":", "\"status\":\"COMPLETE\",\"status\":");
            default -> VALID + " {}";
        };
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service = service(readme(), (request, remaining) -> { calls.incrementAndGet(); return output; });
        var id = service.createGuide("url", "a").explanationInputId();
        var result = service.quickStart(id, "a");
        assertThat(result.code()).isEqualTo("EXPLANATION_INVALID_OUTPUT");
        assertThat(result.steps()).isEmpty();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void correctsUsingSameInputAndRetainsSuccessfulIntroduction() {
        var requests = new java.util.ArrayList<ModelRequest>();
        var service = service(readme(), (request, remaining) -> {
            if (request.input() instanceof ExplanationInput) return "{\"status\":\"AVAILABLE\",\"introduction\":\"笔记应用\",\"evidenceIds\":[\"repo-description\"]}";
            requests.add(request);
            return requests.size() == 1 ? "{}" : VALID;
        });
        var id = service.createGuide("url", "a").explanationInputId();
        var introduction = service.explain(id, "a");
        assertThat(service.quickStart(id, "a").contentStatus()).isEqualTo(QuickStartResult.ContentStatus.COMPLETE);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).input()).isSameAs(requests.get(0).input());
        assertThat(requests.get(1).instructions()).contains("上次输出");
        assertThat(introduction.introduction()).isEqualTo("笔记应用");
    }

    @Test
    void neverCertifiesATruncatedReadmeAsCompleteOrAbsent() {
        var source = readme().returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                "## Quick Start\n\n启动应用：\n\n```sh\njava -jar app.jar\n```\n\n" + "配置说明".repeat(200)));
        var service = service(source, (request, remaining) -> VALID, Clock.systemUTC(),
                new QuickStartSettings(Duration.ofSeconds(45), 256, 65536, 4096));
        assertThat(service.quickStart(service.createGuide("url", "a").explanationInputId(), "a").code())
                .isEqualTo("EXPLANATION_INVALID_OUTPUT");
    }

    @Test
    void preservesPlatformContextAndInlineOriginals() {
        var source = readme().returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                "## Install\n\n### Windows\n\n执行 `app.exe --start`。\n\n### Linux\n\n```sh\n./app --start\n```\n"));
        var inputs = new java.util.ArrayList<QuickStartInput>();
        var service = service(source, (request, remaining) -> { inputs.add((QuickStartInput) request.input()); return "{}"; });
        service.quickStart(service.createGuide("url", "a").explanationInputId(), "a");
        assertThat(inputs.getFirst().evidence().values()).anySatisfy(e -> {
            assertThat(e.text()).isEqualTo("app.exe --start"); assertThat(e.section()).isEqualTo("Install > Windows");
        }).anySatisfy(e -> {
            assertThat(e.text()).isEqualTo("./app --start\n"); assertThat(e.section()).isEqualTo("Install > Linux");
        });
    }

    private FakeRepositoryReadmeSource readme() {
        return FakeRepositoryReadmeSource.withReadme(new RepositoryReadme("README.md", "abc",
                "https://github.com/octo/notes/blob/main/README.md", "## Quick Start\n\n启动应用：\n\n```sh\njava -jar app.jar\n```\n"));
    }

    @Test
    void doesNotSpendTheQuickStartBudgetOnUnrelatedIntroductionSections() {
        var source = readme().returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                "## About\n\n" + "项目背景。".repeat(100) + "\n\n## Quick Start\n\n启动应用：\n\n```sh\njava -jar app.jar\n```\n"));
        var service = service(source, (request, remaining) -> VALID, Clock.systemUTC(),
                new QuickStartSettings(Duration.ofSeconds(45), 256, 65536, 4096));
        assertThat(service.quickStart(service.createGuide("url", "a").explanationInputId(), "a").contentStatus())
                .isEqualTo(QuickStartResult.ContentStatus.COMPLETE);
    }

    @Test
    void generatesBothRegionsConcurrentlyButRejectsDuplicateQuickStartAndProtectsActiveSnapshot() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var finish = new java.util.concurrent.CountDownLatch(1);
        var service = service(readme(), (request, remaining) -> {
            if (request.input() instanceof ExplanationInput)
                return "{\"status\":\"AVAILABLE\",\"introduction\":\"笔记应用\",\"evidenceIds\":[\"repo-description\"]}";
            entered.countDown();
            try { if (!finish.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("deadline"); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            return VALID;
        });
        var id = service.createGuide("url", "a").explanationInputId();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = executor.submit(() -> service.quickStart(id, "a"));
            try {
                assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(service.explain(id, "a").status()).isEqualTo(ExplanationResult.Status.AVAILABLE);
                assertThatThrownBy(() -> service.quickStart(id, "a")).hasMessage("EXPLANATION_IN_PROGRESS");
                assertThat(service.createGuide("url", "a").explanationInputId()).isNull();
                assertThatThrownBy(() -> service.quickStart(id, "b")).hasMessage("EXPLANATION_INPUT_EXPIRED");
            } finally { finish.countDown(); }
            assertThat(pending.get().contentStatus()).isEqualTo(QuickStartResult.ContentStatus.COMPLETE);
        }
        assertThat(service.createGuide("url", "a").explanationInputId()).isNotNull();
        assertThatThrownBy(() -> service.quickStart(id, "a")).hasMessage("EXPLANATION_INPUT_EXPIRED");
    }

    @Test
    void correctionSharesBudgetAndExpiredSnapshotsCannotBeUsed() {
        var clock = new MutableClock();
        var budgets = new java.util.ArrayList<Duration>();
        var service = service(readme(), (request, remaining) -> {
            budgets.add(remaining); clock.now = clock.now.plusSeconds(25); return budgets.size() == 1 ? "{}" : VALID;
        }, clock, QuickStartSettings.defaults());
        var id = service.createGuide("url", "a").explanationInputId();
        assertThat(service.quickStart(id, "a").code()).isEqualTo("EXPLANATION_TIMEOUT");
        assertThat(budgets).hasSize(2);
        assertThat(budgets.getLast()).isLessThanOrEqualTo(Duration.ofSeconds(20));
        clock.now = clock.now.plusSeconds(600);
        assertThatThrownBy(() -> service.quickStart(id, "a")).hasMessage("EXPLANATION_INPUT_EXPIRED");
    }

    @Test
    void upstreamFailureDoesNotRetryAndIntroductionStillWorks() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service = service(readme(), (request, remaining) -> {
            if (request.input() instanceof ExplanationInput)
                return "{\"status\":\"AVAILABLE\",\"introduction\":\"笔记应用\",\"evidenceIds\":[\"repo-description\"]}";
            calls.incrementAndGet(); throw new ExplanationException("EXPLANATION_RATE_LIMITED", 12L);
        });
        var id = service.createGuide("url", "a").explanationInputId();
        assertThat(service.quickStart(id, "a").retryAfterSeconds()).isEqualTo(12L);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(service.explain(id, "a").status()).isEqualTo(ExplanationResult.Status.AVAILABLE);
    }

    @Test
    void incompleteResultKeepsConfigurationOriginalsAndEvidenceBackedGaps() {
        var source = readme().returning(new RepositoryReadme("README.md", "abc", "https://github.com/octo/notes/blob/main/README.md",
                "## Configuration\n\n创建配置，数据库地址需自行填写。\n\n```yaml\ndatabase: YOUR_DATABASE\n```\n\n## Run\n\n启动应用。\n"));
        var service = service(source, (request, remaining) -> """
            {"status":"INCOMPLETE","requirements":[],"steps":[{"text":"启动应用","evidenceIds":["qs-3"],"blockIds":[]}],
            "configuration":[{"text":"创建配置","evidenceIds":["qs-1","qs-2"],"blockIds":["qs-2"]}],"cautions":[],
            "gaps":[{"text":"缺少数据库地址的获取说明","evidenceIds":["qs-1"],"blockIds":[]}]}
            """);
        var result = service.quickStart(service.createGuide("url", "a").explanationInputId(), "a");
        assertThat(result.contentStatus()).isEqualTo(QuickStartResult.ContentStatus.INCOMPLETE);
        assertThat(result.configuration().getFirst().blocks().getFirst().text()).isEqualTo("database: YOUR_DATABASE\n");
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private GuideService service(FakeRepositoryReadmeSource readme, ExplanationModel model, Clock clock, QuickStartSettings quick) {
        var ref = new RepositoryRef("octo", "notes");
        var settings = new ExplanationSettings(Duration.ofMinutes(10), 1, Duration.ofSeconds(30), 12000, 300, 32768);
        return new GuideService(new FakeRepositoryUrlParser(ref),
                FakeRepositoryFactsSource.withMetadata(new RepositoryFacts(ref, "笔记应用", 0, Instant.EPOCH, null, null)),
                readme, FakeRepositoryReleaseSource.withoutReleases(), new OnlineExperienceRecognizer(),
                new ReleaseInterpreter(new ReleaseAssetAdvisor()), new ExplanationSnapshots(settings, clock),
                new IntroductionGenerator(model, settings, clock), new ExplanationInputSelector(settings),
                new QuickStartInputSelector(quick), new QuickStartGenerator(model, quick, clock));
    }
}
