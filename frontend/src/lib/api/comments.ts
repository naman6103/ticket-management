import { browserFetch } from "./browserFetch";
import type { Comment } from "@/types/comment";

export function addComment(ticketId: string, content: string): Promise<Comment> {
  return browserFetch<Comment>(`/api/tickets/${ticketId}/comments`, {
    method: "POST",
    body: JSON.stringify({ content }),
  });
}
