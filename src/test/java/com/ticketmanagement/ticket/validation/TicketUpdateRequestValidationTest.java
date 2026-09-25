package com.ticketmanagement.ticket.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketmanagement.ticket.dto.TicketUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TicketUpdateRequest intentionally carries no Bean Validation annotations: {@code @NotBlank}
 * implies not-null (Jakarta Validation spec), which would reject every absent field on a partial
 * update. Present-but-blank checking is done in {@code TicketServiceImpl.update()} instead
 * (see TicketServiceImplTest / TicketCrudIntegrationTest).
 */
class TicketUpdateRequestValidationTest {

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
  void allFieldsAbsentHasNoDtoLevelViolations() {
    assertThat(validator.validate(new TicketUpdateRequest(null, null, null, null, null))).isEmpty();
  }

  @Test
  void blankFieldsHaveNoDtoLevelViolationsByDesign() {
    assertThat(validator.validate(new TicketUpdateRequest("", "", null, "", null))).isEmpty();
  }
}
