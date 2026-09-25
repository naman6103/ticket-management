package com.ticketmanagement.rag.eval;

import java.util.List;

/**
 * One labeled question → expected-ticket(s) pair from {@code rag-eval/golden-set.json}
 * (evaluation-strategy.md §1). {@code expectedTicketKeys} references the symbolic seed-ticket
 * keys defined in {@link EvalTicketFixtures}, resolved to real UUIDs at evaluation time — a golden
 * set keyed on literal database IDs would silently show 0% recall against any other database.
 */
public record GoldenSetItem(String question, List<String> expectedTicketKeys, String type) {}
