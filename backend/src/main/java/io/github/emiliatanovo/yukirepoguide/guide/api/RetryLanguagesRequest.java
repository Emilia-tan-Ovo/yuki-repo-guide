package io.github.emiliatanovo.yukirepoguide.guide.api;

import jakarta.validation.constraints.NotBlank;

public record RetryLanguagesRequest(
		@NotBlank(message = "缺少可重试的 GitHub 仓库地址。") String canonicalUrl) {
}
