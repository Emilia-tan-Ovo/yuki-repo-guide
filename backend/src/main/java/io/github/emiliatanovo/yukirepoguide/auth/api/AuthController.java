package io.github.emiliatanovo.yukirepoguide.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public final class AuthController {
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final CsrfTokenRepository csrfTokenRepository;
	private final SecurityContextHolderStrategy securityContextHolderStrategy =
			SecurityContextHolder.getContextHolderStrategy();
	private final WebAuthenticationDetailsSource authenticationDetailsSource =
			new WebAuthenticationDetailsSource();

	public AuthController(
			AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	@GetMapping("/csrf")
	public CsrfTokenResponse csrf(CsrfToken csrfToken) {
		return new CsrfTokenResponse(
				csrfToken.getHeaderName(),
				csrfToken.getParameterName(),
				csrfToken.getToken());
	}

	@GetMapping("/session")
	public SessionResponse session(Authentication authentication) {
		boolean authenticated = authentication != null
				&& authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
		return new SessionResponse(authenticated);
	}

	@PostMapping("/login")
	public SessionResponse login(
			@RequestBody LoginRequest loginRequest,
			HttpServletRequest request,
			HttpServletResponse response) {
		var authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(
				"trial-access", loginRequest.accessCode());
		authenticationRequest.setDetails(authenticationDetailsSource.buildDetails(request));
		Authentication authentication = authenticationManager.authenticate(authenticationRequest);

		if (request.getSession(false) != null) {
			request.changeSessionId();
		}

		var securityContext = securityContextHolderStrategy.createEmptyContext();
		securityContext.setAuthentication(authentication);
		securityContextHolderStrategy.setContext(securityContext);
		csrfTokenRepository.saveToken(null, request, response);
		securityContextRepository.saveContext(securityContext, request, response);
		return new SessionResponse(true);
	}

	public record SessionResponse(boolean authenticated) {
	}

	public record CsrfTokenResponse(String headerName, String parameterName, String token) {
	}

	public record LoginRequest(String accessCode) {
	}
}
