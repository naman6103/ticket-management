package com.ticketmanagement.ticket.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketmanagement.ticket.dto.CommentCreateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CommentCreateRequestValidationTest {

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
  void validContentHasNoViolations() {
    assertThat(validator.validate(new CommentCreateRequest("hello"))).isEmpty();
  }

  @Test
  void blankContentViolates() {
    assertThat(validator.validate(new CommentCreateRequest(""))).isNotEmpty();
  }

  @Test
  void nullContentViolates() {
    assertThat(validator.validate(new CommentCreateRequest(null))).isNotEmpty();
  }
}
