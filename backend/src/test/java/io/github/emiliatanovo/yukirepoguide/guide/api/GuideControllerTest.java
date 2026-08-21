package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.application.GuideService;
import io.github.emiliatanovo.yukirepoguide.guide.github.GitHubRepositoryUrlParser;
import io.github.emiliatanovo.yukirepoguide.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuideControllerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		var guideService = new GuideService(new GitHubRepositoryUrlParser());
		mockMvc = MockMvcBuilders.standaloneSetup(new GuideController(guideService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsGroupedRepositoryInformationForAValidGitHubUrl() throws Exception {
		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://github.com/Emilia-tan-Ovo/yuki-repo-guide"}
						"""))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.repository.owner").value("Emilia-tan-Ovo"))
				.andExpect(jsonPath("$.repository.name").value("yuki-repo-guide"))
				.andExpect(jsonPath("$.repository.canonicalUrl")
						.value("https://github.com/Emilia-tan-Ovo/yuki-repo-guide"));
	}

	@Test
	void returnsAReadableProblemDetailForAnUnsupportedRepositoryUrl() throws Exception {
		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":"https://gitlab.com/example/project"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("仓库地址无效"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("目前仅支持 github.com 的仓库地址。"))
				.andExpect(jsonPath("$.code").value("INVALID_REPOSITORY_URL"))
				.andExpect(jsonPath("$.field").value("repositoryUrl"));
	}

	@Test
	void returnsTheSameProblemContractWhenRepositoryUrlIsBlank() throws Exception {
		mockMvc.perform(post("/api/guides")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"repositoryUrl":" "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("仓库地址无效"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("请输入 GitHub 仓库地址。"))
				.andExpect(jsonPath("$.code").value("INVALID_REPOSITORY_URL"))
				.andExpect(jsonPath("$.field").value("repositoryUrl"));
	}
}
