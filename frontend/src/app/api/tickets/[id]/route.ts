import type { NextRequest } from "next/server";
import { apiFetch } from "@/lib/api/client";
import { relay } from "@/lib/api/relay";
import type { Ticket } from "@/types/ticket";

export const dynamic = "force-dynamic";

export async function GET(_request: NextRequest, { params }: { params: { id: string } }) {
  return relay(() => apiFetch<Ticket>(`/api/v1/tickets/${params.id}`));
}

export async function PATCH(request: NextRequest, { params }: { params: { id: string } }) {
  const body = await request.text();
  return relay(() =>
    apiFetch<Ticket>(`/api/v1/tickets/${params.id}`, {
      method: "PATCH",
      body,
    }),
  );
}
