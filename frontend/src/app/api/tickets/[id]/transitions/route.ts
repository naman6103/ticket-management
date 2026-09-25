import type { NextRequest } from "next/server";
import { apiFetch } from "@/lib/api/client";
import { relay } from "@/lib/api/relay";
import type { Ticket } from "@/types/ticket";

export const dynamic = "force-dynamic";

export async function POST(request: NextRequest, { params }: { params: { id: string } }) {
  const body = await request.text();
  return relay(() =>
    apiFetch<Ticket>(`/api/v1/tickets/${params.id}/transitions`, {
      method: "POST",
      body,
    }),
  );
}
