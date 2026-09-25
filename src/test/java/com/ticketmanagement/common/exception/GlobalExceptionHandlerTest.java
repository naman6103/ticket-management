package com.ticketmanagement.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketmanagement.ticket.entity.TicketStatus;
import com.ticketmanagement.ticket.exception.InvalidTransitionException;
import com.ticketmanagement.ticket.exception.TicketNotFoundException;
import com.ticketmanagement.ticket.exception.UnknownFilterException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/v1/tickets");
  }

  @Test
  void mapsValidationExceptionTo400WithFieldDetails() throws NoSuchMethodException {
    BindException bindingResult = new BindException(new Object(), "ticketCreateRequest");
    bindingResult.addError(new FieldError("ticketCreateRequest", "title", "must not be blank"));
    Method dummyMethod = DummyController.class.getMethod("dummy", String.class);
    MethodParameter methodParameter = new MethodParameter(dummyMethod, 0);
    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

    ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.getBody().details()).hasSize(1);
    assertThat(response.getBody().details().get(0).field()).isEqualTo("title");
  }

  @Test
  void mapsTicketNotFoundTo404() {
    ResponseEntity<ErrorResponse> response =
        handler.handleNotFound(new TicketNotFoundException(UUID.randomUUID()), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("TICKET_NOT_FOUND");
  }

  @Test
  void mapsInvalidTransitionTo409() {
    ResponseEntity<ErrorResponse> response = handler.handleInvalidTransition(
        new InvalidTransitionException(TicketStatus.CLOSED, TicketStatus.OPEN), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("INVALID_TRANSITION");
  }

  @Test
  void mapsUnknownFilterTo400() {
    ResponseEntity<ErrorResponse> response = handler.handleUnknownFilter(
        new UnknownFilterException("status", "Unrecognized status value"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("UNKNOWN_FILTER");
  }

  /** Supplies a real {@link Method} so a real {@link MethodParameter} can be constructed for the test above. */
  private static final class DummyController {
    public void dummy(String title) {
      // unused - reflection target only
    }
  }
}
