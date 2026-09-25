import type { NextRequest } from "next/server";
import { apiFetch } from "@/lib/api/client";
import { relay } from "@/lib/api/relay";
import type { Ticket } from "@/types/ticket";
import type { TicketPage } from "@/types/page";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const query = request.nextUrl.search;
  return relay(() => apiFetch<TicketPage<Ticket>>(`/api/v1/tickets${query}`));
}

export async function POST(request: NextRequest) {
  const body = await request.text();
  return relay(() =>
    apiFetch<Ticket>("/api/v1/tickets", {
      method: "POST",
      body,
    }),
  );
}
