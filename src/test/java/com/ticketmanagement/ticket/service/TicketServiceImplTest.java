package com.ticketmanagement.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketmanagement.ticket.dto.TicketCreateRequest;
import com.ticketmanagement.ticket.dto.TicketUpdateRequest;
import com.ticketmanagement.ticket.entity.Priority;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.entity.TicketStatus;
import com.ticketmanagement.ticket.exception.InvalidTransitionException;
import com.ticketmanagement.ticket.exception.TicketNotFoundException;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TicketServiceImplTest {

  private TicketRepository ticketRepository;
  private TicketServiceImpl service;

  @BeforeEach
  void setUp() {
    ticketRepository = mock(TicketRepository.class);
    service = new TicketServiceImpl(ticketRepository);
    when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void createSetsOpenStatus() {
    TicketCreateRequest request = new TicketCreateRequest("Title", "Desc", Priority.HIGH, "jane", null);

    Ticket result = service.create(request);

    assertThat(result.getStatus()).isEqualTo(TicketStatus.OPEN);
    assertThat(result.getTitle()).isEqualTo("Title");
    verify(ticketRepository).save(any(Ticket.class));
  }

  @Test
  void updateAppliesOnlyPresentFields() {
    Ticket existing = new Ticket("Old title", "Old desc", Priority.LOW, "jane", null);
    UUID id = UUID.randomUUID();
    when(ticketRepository.findById(id)).thenReturn(Optional.of(existing));

    TicketUpdateRequest request = new TicketUpdateRequest(null, null, Priority.HIGH, null, null);
    Ticket result = service.update(id, request);

    assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
    assertThat(result.getTitle()).isEqualTo("Old title");
    assertThat(result.getDescription()).isEqualTo("Old desc");
    assertThat(result.getAssignee()).isEqualTo("jane");
  }

  @Test
  void updateOnMissingTicketThrowsNotFound() {
    UUID id = UUID.randomUUID();
    when(ticketRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(id, new TicketUpdateRequest(null, null, null, null, null)))
        .isInstanceOf(TicketNotFoundException.class);
  }

  @ParameterizedTest
  @MethodSource("validTransitions")
  void appliesValidTransition(TicketStatus from, TicketStatus target) {
    Ticket ticket = ticketWithStatus(from);
    UUID id = UUID.randomUUID();
    when(ticketRepository.findById(id)).thenReturn(Optional.of(ticket));

    Ticket result = service.transition(id, target);

    assertThat(result.getStatus()).isEqualTo(target);
    verify(ticketRepository).save(ticket);
  }

  @ParameterizedTest
  @MethodSource("invalidTransitions")
  void rejectsInvalidTransition(TicketStatus from, TicketStatus target) {
    Ticket ticket = ticketWithStatus(from);
    UUID id = UUID.randomUUID();
    when(ticketRepository.findById(id)).thenReturn(Optional.of(ticket));

    assertThatThrownBy(() -> service.transition(id, target)).isInstanceOf(InvalidTransitionException.class);

    assertThat(ticket.getStatus()).isEqualTo(from);
    verify(ticketRepository, never()).save(any());
  }

  @Test
  void transitionOnMissingTicketThrowsNotFound() {
    UUID id = UUID.randomUUID();
    when(ticketRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.transition(id, TicketStatus.IN_PROGRESS))
        .isInstanceOf(TicketNotFoundException.class);
  }

  private static Ticket ticketWithStatus(TicketStatus status) {
    Ticket ticket = new Ticket("t", "d", Priority.LOW, "a", null);
    ticket.setStatus(status);
    return ticket;
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
    // full cartesian product minus the 5 valid pairs = 20 invalid pairs (state-machine.md)
    return EnumSet.allOf(TicketStatus.class).stream()
        .flatMap(from -> EnumSet.allOf(TicketStatus.class).stream()
            .filter(to -> !isValid(from, to))
            .map(to -> Arguments.of(from, to)));
  }

  private static boolean isValid(TicketStatus from, TicketStatus to) {
    return validTransitions().anyMatch(a -> a.get()[0] == from && a.get()[1] == to);
  }

  @Test
  void listDistinctAssigneesReturnsRepositoryResult() {
    when(ticketRepository.findDistinctAssignees()).thenReturn(List.of("jane.doe", "john.smith"));

    List<String> result = service.listDistinctAssignees();

    assertThat(result).containsExactly("jane.doe", "john.smith");
  }

  @Test
  void listDistinctAssigneesReturnsEmptyListWhenNoTicketsHaveAssignees() {
    when(ticketRepository.findDistinctAssignees()).thenReturn(List.of());

    List<String> result = service.listDistinctAssignees();

    assertThat(result).isEmpty();
  }
}
