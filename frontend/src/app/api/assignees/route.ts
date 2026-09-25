import { apiFetch } from "@/lib/api/client";
import { relay } from "@/lib/api/relay";
import type { AssigneesResponse } from "@/types/assignee";

export const dynamic = "force-dynamic";

export async function GET() {
  return relay(() => apiFetch<AssigneesResponse>("/api/v1/assignees"));
}
