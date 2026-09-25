"use client";

import { useId, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { listAssignees } from "@/lib/api/assignees";

interface AssigneeComboboxProps {
  id?: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
}

/**
 * Searchable picker of known assignees (FR-005a), backed by the new
 * GET /api/v1/assignees endpoint. Always accepts a typed value not yet in
 * the list — including when the list is empty (FR-005c) — so an empty
 * backend-derived list never blocks ticket creation/reassignment.
 */
export function AssigneeCombobox({ id, label, value, onChange }: AssigneeComboboxProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const listId = `${inputId}-listbox`;
  const [isOpen, setIsOpen] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["assignees"],
    queryFn: listAssignees,
  });

  const assignees = data?.assignees ?? [];
  const filtered = assignees.filter((assignee) =>
    assignee.toLowerCase().includes(value.toLowerCase()),
  );

  const showEmptyHint = !isLoading && assignees.length === 0;

  return (
    <div className="comboboxWrapper">
      <label htmlFor={inputId}>{label}</label>
      <input
        id={inputId}
        role="combobox"
        aria-expanded={isOpen}
        aria-controls={listId}
        aria-autocomplete="list"
        autoComplete="off"
        value={value}
        onChange={(event) => {
          onChange(event.target.value);
          setIsOpen(true);
        }}
        onFocus={() => setIsOpen(true)}
        onBlur={() => setIsOpen(false)}
      />
      {showEmptyHint && <p className="comboboxHint">No assignees yet — type a name to add one.</p>}
      {isOpen && filtered.length > 0 && (
        <ul id={listId} role="listbox" className="comboboxListbox">
          {filtered.map((assignee) => (
            <li key={assignee} role="option" aria-selected={assignee === value} className="comboboxOption">
              <button
                type="button"
                onMouseDown={(event) => {
                  event.preventDefault();
                  onChange(assignee);
                  setIsOpen(false);
                }}
              >
                {assignee}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
