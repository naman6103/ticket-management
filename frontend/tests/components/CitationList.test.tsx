import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { CitationList } from "@/components/assistant/CitationList";

describe("CitationList", () => {
  it("renders each ticketId as a link opening /tickets/{id} in a new tab", () => {
    render(<CitationList ticketIds={["abc-123"]} />);

    const link = screen.getByRole("link", { name: "abc-123" });
    expect(link).toHaveAttribute("href", "/tickets/abc-123");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("gives each link its own matching href when multiple ticketIds are cited", () => {
    render(<CitationList ticketIds={["ticket-1", "ticket-2", "ticket-3"]} />);

    expect(screen.getByRole("link", { name: "ticket-1" })).toHaveAttribute(
      "href",
      "/tickets/ticket-1",
    );
    expect(screen.getByRole("link", { name: "ticket-2" })).toHaveAttribute(
      "href",
      "/tickets/ticket-2",
    );
    expect(screen.getByRole("link", { name: "ticket-3" })).toHaveAttribute(
      "href",
      "/tickets/ticket-3",
    );
  });
});
