package com.ticketmanagement.ticket.repository;

import com.ticketmanagement.ticket.entity.Ticket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

  @Query("SELECT DISTINCT t.assignee FROM Ticket t WHERE t.assignee IS NOT NULL ORDER BY t.assignee ASC")
  List<String> findDistinctAssignees();
}
