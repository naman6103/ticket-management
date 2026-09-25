package com.ticketmanagement.ticket.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.entity.TicketStatus;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.util.EnumSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketLifecycleIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TicketRepository ticketRepository;

  private Ticket seedTicket(TicketStatus status) {
    Ticket ticket = new Ticket("t", "d", com.ticketmanagement.ticket.entity.Priority.LOW, "a", null);
    ticket.setStatus(status);
    return ticketRepository.save(ticket);
  }

  @ParameterizedTest
  @MethodSource("validTransitions")
  void appliesValidTransition(TicketStatus from, TicketStatus target) throws Exception {
    Ticket ticket = seedTicket(from);

    mockMvc.perform(post("/api/v1/tickets/" + ticket.getId() + "/transitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetStatus\":\"" + target + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(target.name()));

    assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus()).isEqualTo(target);
  }

  @ParameterizedTest
  @MethodSource("invalidTransitions")
  void rejectsInvalidTransition(TicketStatus from, TicketStatus target) throws Exception {
    Ticket ticket = seedTicket(from);

    mockMvc.perform(post("/api/v1/tickets/" + ticket.getId() + "/transitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetStatus\":\"" + target + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

    assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus()).isEqualTo(from);
  }

  @Test
  void transitionOnMissingTicketReturns404() throws Exception {
    mockMvc.perform(post("/api/v1/tickets/" + java.util.UUID.randomUUID() + "/transitions")
            .contentType(MediaType.APPLICATION_JSON).content("{\"targetStatus\":\"OPEN\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void missingTargetStatusRejected() throws Exception {
    Ticket ticket = seedTicket(TicketStatus.OPEN);

    mockMvc.perform(post("/api/v1/tickets/" + ticket.getId() + "/transitions")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  static Stream<Arguments> validTransitions() {
    return Stream.of(
        Arguments.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS),
        Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
        Arguments.of(TicketStatus.RESOLVED, TicketStatus.CLOSED),
        Arguments.of(TicketStatus.OPEN, TicketStatus.CANCELLED),
        Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
  }

  static Stream<Arguments> invalidTransitions() {
    return EnumSet.allOf(TicketStatus.class).stream()
        .flatMap(from -> EnumSet.allOf(TicketStatus.class).stream()
            .filter(to -> !isValid(from, to))
            .map(to -> Arguments.of(from, to)));
  }

  private static boolean isValid(TicketStatus from, TicketStatus to) {
    return validTransitions().anyMatch(a -> a.get()[0] == from && a.get()[1] == to);
  }
}
