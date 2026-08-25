package io.github.emiliatanovo.yukirepoguide.guide.github;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesConfiguration.class)
			.withPropertyValues(
					"yuki.github.connect-timeout=3s",
					"yuki.github.read-timeout=8s");

	@Test
	void failsStartupWhenTheGitHubTokenIsMissing() {
		contextRunner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
					.hasMessageContaining("yuki.github");
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(GitHubProperties.class)
	static class PropertiesConfiguration {
	}
}
