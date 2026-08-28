package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.application.GuideService;
import jakarta.validation.Valid;
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
	public GuideResponse createGuide(@Valid @RequestBody CreateGuideRequest request) {
		return GuideResponse.from(guideService.createGuide(request.repositoryUrl()));
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
}
