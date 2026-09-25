package com.ticketmanagement.rag.eval;

import com.ticketmanagement.ticket.entity.Priority;
import java.util.List;

/**
 * The known, seeded test tickets the golden set (rag-eval/golden-set.json) is written against
 * (evaluation-strategy.md §1). Each fixture's symbolic {@code key} (e.g. {@code "T1"}) is what the
 * golden set references; {@link RagRetrievalEvaluationRunner} seeds these fresh on each run and
 * resolves keys to the real generated ticket UUIDs.
 */
record EvalTicketFixtures(
    String key,
    String title,
    String description,
    Priority priority,
    String assignee,
    String category,
    List<String> comments) {

  static List<EvalTicketFixtures> all() {
    return List.of(
        new EvalTicketFixtures(
            "T1",
            "Payment gateway timeout",
            "Checkout fails when the payment gateway times out during peak load. Customer sees a"
                + " spinner that never resolves.",
            Priority.HIGH,
            "alice",
            "billing",
            List.of("Resolved by adding retry logic with exponential backoff on the gateway call.")),
        new EvalTicketFixtures(
            "T2",
            "Declined card spike",
            "A sudden spike in declined credit cards was traced to a stale fraud-rule configuration"
                + " blocking valid transactions.",
            Priority.HIGH,
            "bob",
            "billing",
            List.of("Resolved by rolling back the fraud-rule config to the previous known-good version.")),
        new EvalTicketFixtures(
            "T3",
            "Refund processing delay",
            "Refunds for cancelled orders were stuck in a pending state due to a queue worker crash"
                + " in the payment service.",
            Priority.MEDIUM,
            "carol",
            "billing",
            List.of()),
        new EvalTicketFixtures(
            "T4",
            "Tracking number not updating",
            "Shipment tracking numbers stopped updating because the carrier webhook endpoint was"
                + " returning 500 errors.",
            Priority.MEDIUM,
            "dave",
            "logistics",
            List.of("Resolved by adding a dead-letter retry queue for the carrier webhook.")),
        new EvalTicketFixtures(
            "T5",
            "Package marked delivered but not received",
            "Several packages were marked delivered by the carrier scan but customers never received"
                + " them, traced to a mis-scanned batch at the depot.",
            Priority.LOW,
            "erin",
            "logistics",
            List.of()));
  }
}
