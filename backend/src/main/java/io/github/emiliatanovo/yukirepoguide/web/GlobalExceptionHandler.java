package io.github.emiliatanovo.yukirepoguide.web;

import io.github.emiliatanovo.yukirepoguide.auth.application.TooManyLoginAttemptsException;
import io.github.emiliatanovo.yukirepoguide.guide.application.GitHubSourceException;
import io.github.emiliatanovo.yukirepoguide.guide.application.ReadmeContentUnsupportedException;
import io.github.emiliatanovo.yukirepoguide.guide.application.ReleaseHistoryUnsupportedException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;
import io.github.emiliatanovo.yukirepoguide.guide.domain.InvalidRepositoryUrlException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalExceptionHandler {

	@ExceptionHandler(GitHubSourceException.class)
	public ResponseEntity<ProblemDetail> handleGitHubSourceFailure(
			GitHubSourceException exception) {
		GitHubErrorPresentation presentation = presentationFor(exception.code());
		var problem = ProblemDetail.forStatusAndDetail(
				presentation.status(), presentation.detail());
		problem.setTitle(presentation.title());
		problem.setProperty("code", exception.code().name());

		ResponseEntity.BodyBuilder response = ResponseEntity.status(presentation.status());
		if (exception.code() == GuideErrorCode.GITHUB_RATE_LIMITED
				&& exception.retryAfterSeconds() != null) {
			problem.setProperty("retryAfterSeconds", exception.retryAfterSeconds());
			response.header(
					HttpHeaders.RETRY_AFTER,
					Long.toString(exception.retryAfterSeconds()));
		}
		return response.body(problem);
	}

	@ExceptionHandler(ReadmeContentUnsupportedException.class)
	public ProblemDetail handleReadmeContentUnsupported(
			ReadmeContentUnsupportedException exception) {
		var problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"README 内容格式暂不受支持，无法识别在线体验入口。");
		problem.setTitle("README 内容不受支持");
		problem.setProperty("code", GuideErrorCode.README_CONTENT_UNSUPPORTED.name());
		problem.setProperty("retryable", false);
		return problem;
	}

	@ExceptionHandler(ReleaseHistoryUnsupportedException.class)
	public ProblemDetail handleReleaseHistoryUnsupported(
			ReleaseHistoryUnsupportedException exception) {
		var problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"该仓库的 Release 历史超过 1000 条，暂不支持完整导览。");
		problem.setTitle("Release 历史超出支持范围");
		problem.setProperty("code", GuideErrorCode.RELEASE_HISTORY_UNSUPPORTED.name());
		problem.setProperty("retryable", false);
		return problem;
	}

	@ExceptionHandler(TooManyLoginAttemptsException.class)
	public ResponseEntity<ProblemDetail> handleTooManyLoginAttempts(
			TooManyLoginAttemptsException exception) {
		var problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.TOO_MANY_REQUESTS, "访问码尝试次数过多，请稍后再试。");
		problem.setTitle("尝试过于频繁");
		problem.setProperty("code", "TOO_MANY_ATTEMPTS");
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
				.body(problem);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ProblemDetail handleInvalidAccessCode(BadCredentialsException exception) {
		var problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNAUTHORIZED, "试用访问码不正确，请重新输入。");
		problem.setTitle("访问码无效");
		problem.setProperty("code", "INVALID_ACCESS_CODE");
		return problem;
	}

	@ExceptionHandler(InvalidRepositoryUrlException.class)
	public ProblemDetail handleInvalidRepositoryUrl(InvalidRepositoryUrlException exception) {
		return invalidRepositoryUrlProblem(exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleInvalidRequest(MethodArgumentNotValidException exception) {
		var fieldError = exception.getBindingResult().getFieldError();
		var detail = fieldError != null && fieldError.getDefaultMessage() != null
				? fieldError.getDefaultMessage()
				: "请输入有效的 GitHub 仓库地址。";
		String field = fieldError != null ? fieldError.getField() : "repositoryUrl";
		return invalidRepositoryUrlProblem(detail, field);
	}

	private ProblemDetail invalidRepositoryUrlProblem(String detail) {
		return invalidRepositoryUrlProblem(detail, "repositoryUrl");
	}

	private ProblemDetail invalidRepositoryUrlProblem(String detail, String field) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("仓库地址无效");
		problem.setProperty("code", "INVALID_REPOSITORY_URL");
		problem.setProperty("field", field);
		return problem;
	}

	private GitHubErrorPresentation presentationFor(GuideErrorCode code) {
		return switch (code) {
			case REPOSITORY_NOT_ACCESSIBLE -> new GitHubErrorPresentation(
					HttpStatus.NOT_FOUND,
					"仓库不可访问",
					"仓库可能不存在、不是公开仓库，或暂时无法访问。请检查地址和可见性。");
			case GITHUB_RATE_LIMITED -> new GitHubErrorPresentation(
					HttpStatus.TOO_MANY_REQUESTS,
					"GitHub 请求受限",
					"GitHub 暂时限制了请求，请稍后重试。");
			case GITHUB_UPSTREAM_FAILURE -> new GitHubErrorPresentation(
					HttpStatus.BAD_GATEWAY,
					"GitHub 上游故障",
					"暂时无法从 GitHub 获取数据，请稍后重试。");
			case GITHUB_SERVICE_UNAVAILABLE -> new GitHubErrorPresentation(
					HttpStatus.SERVICE_UNAVAILABLE,
					"GitHub 服务暂不可用",
					"暂时无法从 GitHub 获取数据。这不是你的操作造成的，请稍后重试。");
			case GITHUB_TIMEOUT -> new GitHubErrorPresentation(
					HttpStatus.GATEWAY_TIMEOUT,
					"GitHub 请求超时",
					"连接 GitHub 超时，请检查网络状况或稍后重试。");
			case README_CONTENT_UNSUPPORTED -> new GitHubErrorPresentation(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"README 内容不受支持",
					"README 内容格式暂不受支持，无法识别在线体验入口。");
			case RELEASE_HISTORY_UNSUPPORTED -> new GitHubErrorPresentation(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"Release 历史超出支持范围",
					"该仓库的 Release 历史超过当前导览支持范围。");
		};
	}

	private record GitHubErrorPresentation(HttpStatus status, String title, String detail) {
	}
}
