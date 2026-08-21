package io.github.emiliatanovo.yukirepoguide.web;

import io.github.emiliatanovo.yukirepoguide.guide.domain.InvalidRepositoryUrlException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalExceptionHandler {

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
