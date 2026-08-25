package io.github.emiliatanovo.yukirepoguide.auth.api;

import io.github.emiliatanovo.yukirepoguide.support.TestTrialAccess;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = TestTrialAccess.HASH_PROPERTY)
@Import(SessionExpirationHttpTest.SessionCaptureConfiguration.class)
class SessionExpirationHttpTest {

	private static final Pattern CSRF_TOKEN_PATTERN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");

	@LocalServerPort
	private int port;

	private HttpClient client;

	private final SessionCapture sessionCapture;

	@Autowired
	SessionExpirationHttpTest(SessionCapture sessionCapture) {
		this.sessionCapture = sessionCapture;
	}

	@BeforeEach
	void createClientWithSessionCookies() {
		var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		client = HttpClient.newBuilder()
				.cookieHandler(cookieManager)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}

	@Test
	void expiredSessionRejectsTheGuideAsUnauthenticated() throws Exception {
		String loginCsrfToken = getCsrfToken();
		assertThat(postJson("/api/auth/login", loginCsrfToken, """
				{"accessCode":"test-access-code"}
				""").statusCode()).isEqualTo(200);

		String authenticatedCsrfToken = getCsrfToken();
		sessionCapture.currentSession().setMaxInactiveInterval(1);
		Thread.sleep(1_500);

		var expiredRequest = postJson("/api/guides", authenticatedCsrfToken, """
				{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
				""");
		assertThat(expiredRequest.statusCode()).isEqualTo(401);
	}

	private String getCsrfToken() throws Exception {
		var request = HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build();
		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		var matcher = CSRF_TOKEN_PATTERN.matcher(response.body());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private HttpResponse<String> postJson(String path, String csrfToken, String body) throws Exception {
		var request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.header("X-CSRF-TOKEN", csrfToken)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SessionCaptureConfiguration {

		@Bean
		SessionCapture sessionCapture() {
			return new SessionCapture();
		}

		@Bean
		ServletListenerRegistrationBean<HttpSessionListener> sessionCaptureListener(SessionCapture capture) {
			return new ServletListenerRegistrationBean<>(new HttpSessionListener() {
				@Override
				public void sessionCreated(HttpSessionEvent event) {
					capture.capture(event.getSession());
				}
			});
		}
	}

	static class SessionCapture {

		private volatile HttpSession currentSession;

		void capture(HttpSession session) {
			currentSession = session;
		}

		HttpSession currentSession() {
			assertThat(currentSession).isNotNull();
			return currentSession;
		}
	}
}
