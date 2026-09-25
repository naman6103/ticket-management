package com.ticketmanagement.rag.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AskRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  @Test
  void validQuestionHasNoViolations() {
    assertThat(validator.validate(new AskRequest("Have we seen payment failures before?"))).isEmpty();
  }

  @Test
  void blankQuestionViolates() {
    assertThat(validator.validate(new AskRequest(""))).isNotEmpty();
  }

  @Test
  void whitespaceOnlyQuestionViolates() {
    assertThat(validator.validate(new AskRequest("   "))).isNotEmpty();
  }

  @Test
  void questionOverOneThousandCharactersViolates() {
    assertThat(validator.validate(new AskRequest("x".repeat(1001)))).isNotEmpty();
  }

  @Test
  void questionAtExactlyOneThousandCharactersIsValid() {
    assertThat(validator.validate(new AskRequest("x".repeat(1000)))).isEmpty();
  }
}
