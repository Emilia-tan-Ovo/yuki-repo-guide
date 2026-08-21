package io.github.emiliatanovo.yukirepoguide.auth.config;

import io.github.emiliatanovo.yukirepoguide.auth.application.TrialAccessAuthenticationProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrialAccessProperties.class)
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	AuthenticationManager authenticationManager(TrialAccessAuthenticationProvider provider) {
		return new ProviderManager(provider);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return new HttpSessionCsrfTokenRepository();
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) throws Exception {
		http
				.authenticationManager(authenticationManager)
				.securityContext(context -> context
						.securityContextRepository(securityContextRepository)
						.requireExplicitSave(true))
				.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET,
								"/api/auth/csrf",
								"/api/auth/session").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers("/api/guides/**").authenticated()
						.requestMatchers("/api/**").denyAll()
						.requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
						.anyRequest().denyAll())
				.logout(logout -> logout
						.logoutUrl("/api/auth/logout")
						.logoutSuccessHandler((request, response, authentication) ->
								response.setStatus(HttpStatus.NO_CONTENT.value())))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

		return http.build();
	}
}
