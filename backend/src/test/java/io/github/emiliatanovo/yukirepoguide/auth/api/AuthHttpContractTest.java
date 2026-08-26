package io.github.emiliatanovo.yukirepoguide.auth.api;

import com.jayway.jsonpath.JsonPath;
import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryFactsSource;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryFacts;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import io.github.emiliatanovo.yukirepoguide.guide.support.FakeRepositoryFactsSource;
import io.github.emiliatanovo.yukirepoguide.support.TestTrialAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

@SpringBootTest(properties = {
		TestTrialAccess.HASH_PROPERTY,
		TestTrialAccess.GITHUB_TOKEN_PROPERTY
})
@AutoConfigureMockMvc
@Import(AuthHttpContractTest.FakeRepositoryFactsConfiguration.class)
class AuthHttpContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Value("${server.servlet.session.timeout}")
	private Duration sessionTimeout;

	@Test
	void reportsThatAnAnonymousSessionIsNotAuthenticated() throws Exception {
		mockMvc.perform(get("/api/auth/session"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authenticated").value(false));
	}

	@Test
	void exposesACsrfTokenForPublicAuthenticationRequests() throws Exception {
		mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
				.andExpect(jsonPath("$.token").value(not(emptyOrNullString())));
	}

	@Test
	void configuresSessionsToExpireAfterTwoHoursOfInactivity() {
		assertThat(sessionTimeout).isEqualTo(Duration.ofHours(2));
	}

	@Test
	void rotatesTheCsrfTokenAfterSuccessfulLogin() throws Exception {
		var csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		String csrfBody = csrfResult.getResponse().getContentAsString();
		String headerName = JsonPath.read(csrfBody, "$.headerName");
		String firstToken = JsonPath.read(csrfBody, "$.token");
		var session = (MockHttpSession) csrfResult.getRequest().getSession(false);

		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.header(headerName, firstToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"accessCode":"test-access-code"}
						"""))
				.andExpect(status().isOk());

		var refreshedCsrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
				.andExpect(status().isOk())
				.andReturn();
		String refreshedToken = JsonPath.read(
				refreshedCsrfResult.getResponse().getContentAsString(), "$.token");
		assertThat(refreshedToken).isNotEqualTo(firstToken);
	}

	@Test
	void validAccessCodeEstablishesAnAuthenticatedSession() throws Exception {
		var loginResult = mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"accessCode":"test-access-code"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authenticated").value(true))
				.andReturn();

		var session = (MockHttpSession) loginResult.getRequest().getSession(false);

		mockMvc.perform(get("/api/auth/session").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authenticated").value(true));

		mockMvc.perform(post("/api/guides")
				.session(session)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsAnInvalidAccessCodeWithoutCreatingAQualifiedSession() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"accessCode":"wrong-access-code"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
	}

	@Test
	void loginRequiresACsrfToken() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"accessCode":"test-access-code"}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void blocksTheSameSourceAfterFiveInvalidAccessCodes() throws Exception {
		for (int attempt = 0; attempt < 5; attempt++) {
			mockMvc.perform(post("/api/auth/login")
					.with(csrf())
					.with(request -> {
						request.setRemoteAddr("203.0.113.20");
						return request;
					})
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"accessCode":"wrong-access-code"}
							"""))
					.andExpect(status().isUnauthorized());
		}

		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.with(request -> {
					request.setRemoteAddr("203.0.113.20");
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"accessCode":"test-access-code"}
						"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "600"))
				.andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
	}

	@Test
	void logoutInvalidatesTheAuthenticatedSession() throws Exception {
		var loginResult = mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"accessCode":"test-access-code"}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		var session = (MockHttpSession) loginResult.getRequest().getSession(false);

		mockMvc.perform(post("/api/auth/logout")
				.session(session)
				.with(csrf()))
				.andExpect(status().isNoContent())
				.andExpect(unauthenticated());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeRepositoryFactsConfiguration {

		@Bean
		@Primary
		RepositoryFactsSource fakeRepositoryFactsSource() {
			var reference = new RepositoryRef("Emilia-tan-Ovo", "yuki-repo-guide");
			return FakeRepositoryFactsSource.withMetadata(new RepositoryFacts(
					reference,
					"Evidence-first GitHub repository guide",
					123,
					Instant.parse("2026-08-01T00:00:00Z"),
					Instant.parse("2026-08-24T12:00:00Z")));
		}
	}
}
