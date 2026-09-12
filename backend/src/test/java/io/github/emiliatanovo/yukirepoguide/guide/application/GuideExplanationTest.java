package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.*;
import io.github.emiliatanovo.yukirepoguide.guide.explanation.*;
import io.github.emiliatanovo.yukirepoguide.guide.support.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GuideExplanationTest {
	private static final String VALID = "{\"status\":\"AVAILABLE\",\"introduction\":\"一个开源笔记应用。\",\"evidenceIds\":[\"repo-description\"]}";

	@Test
	void retriesInvalidReferencesOnceWithTheSameInputAndDoesNotCacheResults() {
		var seen = new ArrayList<ExplanationInput>();
		var corrections = new ArrayList<Boolean>();
		var fixture = fixture((input, correction, remaining) -> {
			seen.add(input); corrections.add(correction);
			return seen.size() == 1 ? VALID.replace("repo-description", "invented") : VALID;
		});
		var id = fixture.service.createGuide("url", "a").explanationInputId();
		assertThat(fixture.service.explain(id, "a").status()).isEqualTo(ExplanationResult.Status.AVAILABLE);
		assertThat(corrections).containsExactly(false, true);
		assertThat(seen.get(0)).isSameAs(seen.get(1));
		fixture.service.explain(id, "a");
		assertThat(corrections).containsExactly(false, true, false);
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {
			"{}", "{\"status\":\"AVAILABLE\",\"introduction\":\"无来源\",\"evidenceIds\":[]}",
			"{\"status\":\"INSUFFICIENT_EVIDENCE\",\"introduction\":\"猜测\",\"evidenceIds\":[]}",
			"{\"status\":\"INSUFFICIENT_EVIDENCE\",\"introduction\":null,\"evidenceIds\":[],\"extra\":true}",
			"{\"status\":\"INSUFFICIENT_EVIDENCE\",\"introduction\":null,\"evidenceIds\":[]} {}",
			"{\"status\":\"AVAILABLE\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"introduction\":null,\"evidenceIds\":[]}"
	})
	void rejectsMalformedOrInconsistentOutputAndReleasesTheSnapshot(String output) {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var fixture = fixture((input, correction, remaining) -> { calls.incrementAndGet(); return output; });
		var id = fixture.service.createGuide("url", "a").explanationInputId();
		assertThat(fixture.service.explain(id, "a").code()).isEqualTo("EXPLANATION_INVALID_OUTPUT");
		assertThat(calls.get()).isEqualTo(2);
		assertThat(fixture.service.explain(id, "a").code()).isEqualTo("EXPLANATION_INVALID_OUTPUT");
		assertThat(calls.get()).isEqualTo(4);
	}

	@Test
	void preservesInsufficientEvidenceAsANormalResult() {
		var fixture = fixture((input, correction, remaining) ->
				"{\"status\":\"INSUFFICIENT_EVIDENCE\",\"introduction\":null,\"evidenceIds\":[]}");
		var id = fixture.service.createGuide("url", "a").explanationInputId();
		assertThat(fixture.service.explain(id, "a")).isEqualTo(ExplanationResult.insufficient());
	}

	@Test
	void upstreamFailureDoesNotAutomaticallyRetryOrExposeUpstreamText() {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var fixture = fixture((input, correction, remaining) -> {
			calls.incrementAndGet(); throw new ExplanationException("EXPLANATION_RATE_LIMITED", 12L);
		});
		var id = fixture.service.createGuide("url", "a").explanationInputId();
		var result = fixture.service.explain(id, "a");
		assertThat(result.status()).isEqualTo(ExplanationResult.Status.UNAVAILABLE);
		assertThat(result.retryAfterSeconds()).isEqualTo(12);
		assertThat(result.introduction()).isNull();
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void rejectsAnotherSessionAndExpiredOrEvictedInputsWithoutCallingTheModel() {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var fixture = fixture((input, correction, remaining) -> { calls.incrementAndGet(); return VALID; });
		var first = fixture.service.createGuide("url", "a").explanationInputId();
		assertThatThrownBy(() -> fixture.service.explain(first, "b")).hasMessage("EXPLANATION_INPUT_EXPIRED");
		fixture.service.createGuide("url", "a");
		assertThatThrownBy(() -> fixture.service.explain(first, "a")).hasMessage("EXPLANATION_INPUT_EXPIRED");
		var third = fixture.service.createGuide("url", "a").explanationInputId();
		fixture.clock.advance(Duration.ofMinutes(10));
		assertThatThrownBy(() -> fixture.service.explain(third, "a")).hasMessage("EXPLANATION_INPUT_EXPIRED");
		assertThat(calls.get()).isZero();
	}

	@Test
	void onlyOneGenerationMayRunForASnapshotAndBusyEntryCannotBeEvicted() throws Exception {
		var entered = new java.util.concurrent.CountDownLatch(1);
		var finish = new java.util.concurrent.CountDownLatch(1);
		var fixture = fixture((input, correction, remaining) -> {
			entered.countDown();
			try { if (!finish.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("test deadline"); }
			catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
			return VALID;
		});
		var id = fixture.service.createGuide("url", "a").explanationInputId();
		try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var future = executor.submit(() -> fixture.service.explain(id, "a"));
			try {
				assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
				assertThatThrownBy(() -> fixture.service.explain(id, "a")).hasMessage("EXPLANATION_IN_PROGRESS");
				assertThat(fixture.service.createGuide("url", "b").explanationInputId()).isNull();
			} finally { finish.countDown(); }
			assertThat(future.get().status()).isEqualTo(ExplanationResult.Status.AVAILABLE);
		}
	}

	@Test
	void correctionSharesTheOriginalTimeBudget() {
		var clock = new MutableClock();
		var budgets = new ArrayList<Duration>();
		var fixture = fixture((input, correction, remaining) -> {
			budgets.add(remaining);
			clock.advance(Duration.ofSeconds(20));
			return correction ? VALID : "{}";
		}, clock);
		var id = fixture.service.createGuide("url", "a").explanationInputId();
		assertThat(fixture.service.explain(id, "a").code()).isEqualTo("EXPLANATION_TIMEOUT");
		assertThat(budgets).hasSize(2);
		assertThat(budgets.get(1)).isLessThanOrEqualTo(Duration.ofSeconds(10));
	}

	@Test
	void selectsIntroductionButExcludesNestedInstallationAndImageContent() {
		var seen = new ArrayList<ExplanationInput>();
		var fixture = fixture((input, correction, remaining) -> { seen.add(input); return VALID; });
		fixture.readme.returning(new RepositoryReadme("README.md", "sha", "https://github.com/octo/notes/blob/main/README.md",
				"# Notes\n\n开源笔记应用。\n\n![广告](https://evil.example/ad.png)\n\n## Features\n\n标签整理。\n\n### Installation\n\n运行危险安装操作。\n\n#### Overview\n\n不应选中的安装说明。\n\n## Overview\n\n本地保存。\n\n支持 `Java` 和 **Python**。"));
		var id = fixture.service.createGuide("url", "a").explanationInputId();
		fixture.service.explain(id, "a");
		assertThat(seen.getFirst().evidence().values()).extracting(ExplanationInput.Evidence::text)
				.contains("标签整理。", "本地保存。", "支持 `Java` 和 **Python**。")
				.doesNotContain("运行危险安装操作。", "广告", "不应选中的安装说明。");
	}

	private Fixture fixture(ExplanationModel model) { return fixture(model, new MutableClock()); }
	private Fixture fixture(ExplanationModel model, MutableClock clock) {
		var ref = new RepositoryRef("octo", "notes");
		var readme = FakeRepositoryReadmeSource.withReadme(new RepositoryReadme("README.md", "abc",
				"https://github.com/octo/notes/blob/main/README.md", "# Notes\n\n本地笔记。"));
		var settings = new ExplanationSettings(Duration.ofMinutes(10), 1, Duration.ofSeconds(30), 12000, 300, 32768);
		var service = new GuideService(new FakeRepositoryUrlParser(ref),
				FakeRepositoryFactsSource.withMetadata(new RepositoryFacts(ref, "开源笔记应用", 0, Instant.EPOCH, null, null)),
				readme, FakeRepositoryReleaseSource.withoutReleases(), new OnlineExperienceRecognizer(),
				new ReleaseInterpreter(new ReleaseAssetAdvisor()), new ExplanationSnapshots(settings, clock),
				new IntroductionGenerator(model, settings, clock), new ExplanationInputSelector(settings));
		return new Fixture(service, readme, clock);
	}
	private record Fixture(GuideService service, FakeRepositoryReadmeSource readme, MutableClock clock) {}
	private static final class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-09-12T00:00:00Z");
		void advance(Duration duration) { now = now.plus(duration); }
		@Override public Instant instant() { return now; }
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
	}
	@Test
	void generatesFromTheOriginalSnapshotWithoutFetchingSourcesAgain() {
		var ref = new RepositoryRef("octo", "notes");
		var facts = FakeRepositoryFactsSource.withMetadata(new RepositoryFacts(ref,
				"一个开源笔记应用", 1, Instant.EPOCH, Instant.EPOCH, null));
		var readme = FakeRepositoryReadmeSource.withReadme(new RepositoryReadme("README.md", "abc",
				"https://github.com/octo/notes/blob/main/README.md", "# Notes\n\n支持本地保存笔记。"));
		var inputs = new ArrayList<ExplanationInput>();
		ExplanationModel model = (input, correction, remaining) -> {
			inputs.add(input);
			return "{\"status\":\"AVAILABLE\",\"introduction\":\"一个开源笔记应用。\",\"evidenceIds\":[\"repo-description\"]}";
		};
		var settings = ExplanationSettings.defaults();
		var service = new GuideService(new FakeRepositoryUrlParser(ref), facts, readme,
				FakeRepositoryReleaseSource.withoutReleases(), new OnlineExperienceRecognizer(),
				new ReleaseInterpreter(new ReleaseAssetAdvisor()), new ExplanationSnapshots(settings, Clock.systemUTC()),
				new IntroductionGenerator(model, settings, Clock.systemUTC()), new ExplanationInputSelector(settings));
		var guide = service.createGuide(ref.canonicalUrl(), "session-a");
		assertThat(inputs).isEmpty();
		assertThat(guide.explanationInputId()).isNotBlank();
		var result = service.explain(guide.explanationInputId(), "session-a");
		assertThat(result.status()).isEqualTo(ExplanationResult.Status.AVAILABLE);
		assertThat(result.introduction()).isEqualTo("一个开源笔记应用。");
		assertThat(result.evidence()).containsKey("repo-description");
		assertThat(readme.requests()).isEqualTo(1);
		assertThat(inputs).hasSize(1);
		assertThat(inputs.getFirst().evidence().values()).anyMatch(e -> "abc".equals(e.sha()));
	}
}
