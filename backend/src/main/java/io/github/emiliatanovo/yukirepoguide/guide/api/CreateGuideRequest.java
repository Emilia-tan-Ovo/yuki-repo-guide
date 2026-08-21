package io.github.emiliatanovo.yukirepoguide.guide.api;

import jakarta.validation.constraints.NotBlank;

public record CreateGuideRequest(
		@NotBlank(message = "请输入 GitHub 仓库地址。") String repositoryUrl) {
}
