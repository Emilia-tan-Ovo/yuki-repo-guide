package io.github.emiliatanovo.yukirepoguide.web;

import io.github.emiliatanovo.yukirepoguide.auth.application.TooManyLoginAttemptsException;
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
		var fieldError = exception.getBindingResult().getFieldError("repositoryUrl");
		var detail = fieldError != null && fieldError.getDefaultMessage() != null
				? fieldError.getDefaultMessage()
				: "请输入有效的 GitHub 仓库地址。";
		return invalidRepositoryUrlProblem(detail);
	}

	private ProblemDetail invalidRepositoryUrlProblem(String detail) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("仓库地址无效");
		problem.setProperty("code", "INVALID_REPOSITORY_URL");
		problem.setProperty("field", "repositoryUrl");
		return problem;
	}
}
