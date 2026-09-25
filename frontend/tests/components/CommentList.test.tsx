import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { CommentList } from "@/components/comments/CommentList";
import type { Comment } from "@/types/comment";

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: "c1",
    ticketId: "t1",
    content: "A comment",
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("CommentList", () => {
  it("renders 'no comments yet' when comments is empty", () => {
    render(<CommentList comments={[]} />);

    expect(screen.getByText(/no comments yet/i)).toBeInTheDocument();
  });

  it("renders comments in chronological order regardless of input order", () => {
    const older = makeComment({ id: "c1", content: "First", createdAt: "2026-01-01T00:00:00Z" });
    const newer = makeComment({ id: "c2", content: "Second", createdAt: "2026-01-02T00:00:00Z" });

    render(<CommentList comments={[newer, older]} />);

    const contents = screen.getAllByTestId("comment-content").map((el) => el.textContent);
    expect(contents).toEqual(["First", "Second"]);
  });
});
