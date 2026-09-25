import { useState } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AssigneeCombobox } from "@/components/tickets/AssigneeCombobox";
import * as assigneesApi from "@/lib/api/assignees";

vi.mock("@/lib/api/assignees", () => ({
  listAssignees: vi.fn(),
}));

function ControlledHarness({ onChangeSpy }: { onChangeSpy: (value: string) => void }) {
  const [value, setValue] = useState("");
  return (
    <AssigneeCombobox
      label="Assignee"
      value={value}
      onChange={(next) => {
        setValue(next);
        onChangeSpy(next);
      }}
    />
  );
}

function renderWithClient(onChangeSpy: (value: string) => void) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ControlledHarness onChangeSpy={onChangeSpy} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(assigneesApi.listAssignees).mockReset();
});

describe("AssigneeCombobox", () => {
  it("shows the 'no assignees yet' hint and still accepts a typed value when the list is empty", async () => {
    vi.mocked(assigneesApi.listAssignees).mockResolvedValue({ assignees: [] });
    const onChange = vi.fn();

    renderWithClient(onChange);

    expect(await screen.findByText(/no assignees yet/i)).toBeInTheDocument();

    const input = screen.getByRole("combobox", { name: "Assignee" });
    await userEvent.type(input, "new.person");

    expect(onChange).toHaveBeenLastCalledWith("new.person");
  });

  it("filters existing assignees as the user types and selects one on click", async () => {
    vi.mocked(assigneesApi.listAssignees).mockResolvedValue({
      assignees: ["jane.doe", "john.smith"],
    });
    const onChange = vi.fn();

    renderWithClient(onChange);

    const input = screen.getByRole("combobox", { name: "Assignee" });
    await userEvent.click(input);

    await waitFor(() => expect(screen.getByText("jane.doe")).toBeInTheDocument());
    expect(screen.getByText("john.smith")).toBeInTheDocument();

    await userEvent.click(screen.getByText("jane.doe"));

    expect(onChange).toHaveBeenLastCalledWith("jane.doe");
  });
});
