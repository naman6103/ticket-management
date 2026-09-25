package com.ticketmanagement.ticket.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketmanagement.ticket.dto.TicketTransitionRequest;
import com.ticketmanagement.ticket.entity.TicketStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TicketTransitionRequestValidationTest {

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
  void validTargetStatusHasNoViolations() {
    assertThat(validator.validate(new TicketTransitionRequest(TicketStatus.IN_PROGRESS))).isEmpty();
  }

  @Test
  void nullTargetStatusViolates() {
    assertThat(validator.validate(new TicketTransitionRequest(null))).isNotEmpty();
  }
}
