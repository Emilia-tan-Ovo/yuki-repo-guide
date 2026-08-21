package io.github.emiliatanovo.yukirepoguide.auth.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-08-21T00:00:00Z"));
	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

	@Test
	void blocksTheSameSourceAfterFiveFailuresForTenMinutes() {
		for (int attempt = 0; attempt < 5; attempt++) {
			assertThatThrownBy(() -> limiter.verify("203.0.113.10", () -> false))
					.isInstanceOf(InvalidAccessCodeException.class);
		}

		assertThatThrownBy(() -> limiter.verify("203.0.113.10", () -> true))
				.isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
						exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(600));

		clock.advance(Duration.ofMinutes(10));

		assertThatThrownBy(() -> limiter.verify("203.0.113.10", () -> false))
				.isInstanceOf(InvalidAccessCodeException.class);
	}

	@Test
	void successfulAuthenticationClearsPreviousFailures() {
		for (int attempt = 0; attempt < 4; attempt++) {
			assertThatThrownBy(() -> limiter.verify("203.0.113.11", () -> false))
					.isInstanceOf(InvalidAccessCodeException.class);
		}

		limiter.verify("203.0.113.11", () -> true);

		for (int attempt = 0; attempt < 5; attempt++) {
			assertThatThrownBy(() -> limiter.verify("203.0.113.11", () -> false))
					.isInstanceOf(InvalidAccessCodeException.class);
		}
	}

	private static final class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
