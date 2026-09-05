package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.support.TestTrialAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		TestTrialAccess.HASH_PROPERTY,
		TestTrialAccess.GITHUB_TOKEN_PROPERTY
})
@AutoConfigureMockMvc
class GuideAccessSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rejectsGuideCreationWithoutTrialAccess() throws Exception {
		mockMvc.perform(post("/api/guides")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsLanguageRetryWithoutTrialAccess() throws Exception {
		mockMvc.perform(post("/api/guides/languages/retry")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsReadmeRetryWithoutTrialAccess() throws Exception {
		mockMvc.perform(post("/api/guides/readme/retry")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsReleaseRetryWithoutTrialAccess() throws Exception {
		mockMvc.perform(post("/api/guides/releases/retry")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsAuthenticatedReleaseRetryWithoutCsrf() throws Exception {
		mockMvc.perform(post("/api/guides/releases/retry")
				.with(user("trial-user"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"canonicalUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void doesNotExposeUnlistedResourcesByDefault() throws Exception {
		mockMvc.perform(get("/internal"))
				.andExpect(status().isUnauthorized());
	}
}
