import { browserFetch } from "./browserFetch";
import type { AssigneesResponse } from "@/types/assignee";

export function listAssignees(): Promise<AssigneesResponse> {
  return browserFetch<AssigneesResponse>("/api/assignees");
}
