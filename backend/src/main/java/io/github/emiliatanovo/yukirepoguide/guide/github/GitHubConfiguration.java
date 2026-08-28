package io.github.emiliatanovo.yukirepoguide.guide.github;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubConfiguration {

	@Bean
	GitHubRateLimitGate gitHubRateLimitGate() {
		return new GitHubRateLimitGate();
	}

	@Bean
	GitHubRepositoryFactsAdapter gitHubRepositoryFactsSource(
			RestClient.Builder builder,
			GitHubProperties properties,
			GitHubRateLimitGate rateLimitGate,
			Clock clock) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return new GitHubRepositoryFactsAdapter(
				builder, properties, rateLimitGate, clock, requestFactory);
	}
}
