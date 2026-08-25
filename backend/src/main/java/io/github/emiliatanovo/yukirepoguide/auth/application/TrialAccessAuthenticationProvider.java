package io.github.emiliatanovo.yukirepoguide.auth.application;

import io.github.emiliatanovo.yukirepoguide.auth.config.TrialAccessProperties;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class TrialAccessAuthenticationProvider implements AuthenticationProvider {

	private final PasswordEncoder passwordEncoder;
	private final String accessCodeHash;
	private final LoginAttemptLimiter loginAttemptLimiter;

	public TrialAccessAuthenticationProvider(
			PasswordEncoder passwordEncoder,
			TrialAccessProperties properties,
			LoginAttemptLimiter loginAttemptLimiter) {
		this.passwordEncoder = passwordEncoder;
		this.accessCodeHash = properties.codeHash();
		this.loginAttemptLimiter = loginAttemptLimiter;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String accessCode = authentication.getCredentials() instanceof String value ? value : "";
		String sourceAddress = authentication.getDetails() instanceof WebAuthenticationDetails details
				? details.getRemoteAddress()
				: "unknown";
		loginAttemptLimiter.verify(
				sourceAddress,
				() -> passwordEncoder.matches(accessCode, accessCodeHash));

		return UsernamePasswordAuthenticationToken.authenticated("trial-access", null, List.of());
	}

	@Override
	public boolean supports(Class<?> authenticationType) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authenticationType);
	}
}
