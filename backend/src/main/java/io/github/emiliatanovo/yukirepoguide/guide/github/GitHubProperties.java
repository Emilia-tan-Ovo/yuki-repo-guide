package io.github.emiliatanovo.yukirepoguide.guide.github;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("yuki.github")
public record GitHubProperties(
		@NotBlank String token,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout) {
}
