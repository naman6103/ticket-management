import { ErrorDisplay } from "@/components/errors/ErrorDisplay";
import type { Comment } from "@/types/comment";

interface CommentListProps {
  comments: Comment[];
}

/** Renders a ticket's comment history oldest → newest (spec.md User Story 3 Scenario 2). */
export function CommentList({ comments }: CommentListProps) {
  if (comments.length === 0) {
    return <ErrorDisplay messages={[{ kind: "banner", text: "No comments yet." }]} />;
  }

  const sorted = [...comments].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
  );

  return (
    <ul className="commentList">
      {sorted.map((comment) => (
        <li key={comment.id} className="commentItem">
          <p data-testid="comment-content">{comment.content}</p>
          <time data-testid="comment-timestamp" dateTime={comment.createdAt}>
            {comment.createdAt}
          </time>
        </li>
      ))}
    </ul>
  );
}
