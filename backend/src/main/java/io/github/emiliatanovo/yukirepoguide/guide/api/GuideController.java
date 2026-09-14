package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.application.GuideService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import io.github.emiliatanovo.yukirepoguide.guide.explanation.ExplanationResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guides")
public final class GuideController {

	private final GuideService guideService;

	public GuideController(GuideService guideService) {
		this.guideService = guideService;
	}

	@PostMapping
	public GuideResponse createGuide(@Valid @RequestBody CreateGuideRequest request, HttpSession session) {
		return GuideResponse.from(guideService.createGuide(request.repositoryUrl(), session.getId()));
	}

	@PostMapping("/explanation")
	public ExplanationResult explain(@Valid @RequestBody ExplanationRequest request, HttpSession session) {
		return guideService.explain(request.explanationInputId(), session.getId());
	}

	@PostMapping("/quick-start")
	public io.github.emiliatanovo.yukirepoguide.guide.quickstart.QuickStartResult quickStart(
			@Valid @RequestBody ExplanationRequest request, HttpSession session) {
		return guideService.quickStart(request.explanationInputId(), session.getId());
	}

	@PostMapping("/languages/retry")
	public LanguageRetryResponse retryLanguages(
			@Valid @RequestBody RetryLanguagesRequest request) {
		return LanguageRetryResponse.from(
				guideService.retryLanguages(request.canonicalUrl()));
	}

	@PostMapping("/readme/retry")
	public ReadmeRetryResponse retryReadme(
			@Valid @RequestBody RetryReadmeRequest request) {
		return ReadmeRetryResponse.from(
				guideService.retryReadme(request.canonicalUrl()));
	}

	@PostMapping("/releases/retry")
	public ReleaseRetryResponse retryReleases(
			@Valid @RequestBody RetryReleasesRequest request) {
		return ReleaseRetryResponse.from(
				guideService.retryReleases(request.canonicalUrl()));
	}

	@PostMapping("/releases/recommendation")
	public ReleaseRecommendationResponse recommendReleases(
			@Valid @RequestBody ReleaseRecommendationRequest request) {
		return ReleaseRecommendationResponse.from(
				guideService.recommendReleases(
						request.canonicalUrl(), request.confirmedRuntime()));
	}
}
