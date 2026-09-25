import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";

describe("ErrorDisplay", () => {
  it("renders a fullpage message with the fullpage testid", () => {
    render(<ErrorDisplay messages={[{ kind: "fullpage", text: "Ticket not found" }]} />);

    expect(screen.getByTestId("error-fullpage")).toHaveTextContent("Ticket not found");
  });

  it("renders a banner message with the banner testid", () => {
    render(<ErrorDisplay messages={[{ kind: "banner", text: "Something specific" }]} />);

    expect(screen.getByTestId("error-banner")).toHaveTextContent("Something specific");
  });

  it("renders a field message with a field-specific testid and data-field attribute", () => {
    render(
      <ErrorDisplay
        messages={[{ kind: "field", fieldName: "title", text: "must not be blank" }]}
      />,
    );

    const el = screen.getByTestId("error-field-title");
    expect(el).toHaveTextContent("must not be blank");
    expect(el).toHaveAttribute("data-field", "title");
  });

  it("renders nothing when given an empty messages array", () => {
    const { container } = render(<ErrorDisplay messages={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});
