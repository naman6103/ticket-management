import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { NavShell } from "@/components/layout/NavShell";

describe("NavShell", () => {
  it("renders a link to the AI Q&A panel alongside the tickets link", () => {
    render(<NavShell />);

    expect(screen.getByRole("link", { name: /tickets/i })).toHaveAttribute("href", "/tickets");
    expect(screen.getByRole("link", { name: /ask assistant/i })).toHaveAttribute(
      "href",
      "/assistant",
    );
  });
});
