package io.github.emiliatanovo.yukirepoguide.auth.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrialAccessPropertiesTest {

	@Test
	void rejectsABcryptHashWithAnInvalidCostBeforeAuthenticationIsUsed() {
		try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
			var validator = validatorFactory.getValidator();
			var properties = new TrialAccessProperties(
					"{bcrypt}$2a$99$2ndaYCYAwF.fbsSYbB03Xu9wsRIbVet3BsAvLWWqJdumbKNOScBpe");

			assertThat(validator.validate(properties)).isNotEmpty();
		}
	}
}
