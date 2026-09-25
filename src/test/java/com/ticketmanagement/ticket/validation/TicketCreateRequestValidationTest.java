package com.ticketmanagement.ticket.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketmanagement.ticket.dto.TicketCreateRequest;
import com.ticketmanagement.ticket.entity.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TicketCreateRequestValidationTest {

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
  void validRequestHasNoViolations() {
    var request = new TicketCreateRequest("T", "D", Priority.LOW, "a", null);
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void blankTitleViolates() {
    var request = new TicketCreateRequest("", "D", Priority.LOW, "a", null);
    Set<ConstraintViolation<TicketCreateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
  }

  @Test
  void blankDescriptionViolates() {
    var request = new TicketCreateRequest("T", "", Priority.LOW, "a", null);
    assertThat(validator.validate(request)).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
  }

  @Test
  void blankAssigneeViolates() {
    var request = new TicketCreateRequest("T", "D", Priority.LOW, "", null);
    assertThat(validator.validate(request)).anyMatch(v -> v.getPropertyPath().toString().equals("assignee"));
  }

  @Test
  void nullPriorityViolates() {
    var request = new TicketCreateRequest("T", "D", null, "a", null);
    assertThat(validator.validate(request)).anyMatch(v -> v.getPropertyPath().toString().equals("priority"));
  }

  @Test
  void nullCategoryIsAllowed() {
    var request = new TicketCreateRequest("T", "D", Priority.LOW, "a", null);
    assertThat(validator.validate(request)).isEmpty();
  }
}
