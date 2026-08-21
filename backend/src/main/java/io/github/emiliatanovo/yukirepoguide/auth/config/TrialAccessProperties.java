package io.github.emiliatanovo.yukirepoguide.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("yuki.trial-access")
public record TrialAccessProperties(
		@NotBlank
		@Pattern(regexp = "\\{bcrypt\\}\\$2[aby]\\$(?:0[4-9]|[12]\\d|3[01])\\$[./A-Za-z0-9]{53}")
		String codeHash) {
}
