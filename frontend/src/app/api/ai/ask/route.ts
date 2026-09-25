import type { NextRequest } from "next/server";
import { apiFetch } from "@/lib/api/client";
import { relay } from "@/lib/api/relay";
import type { AskAnswer } from "@/types/assistant";

export const dynamic = "force-dynamic";

export async function POST(request: NextRequest) {
  const body = await request.text();
  return relay(() =>
    apiFetch<AskAnswer>("/api/ai/ask", {
      method: "POST",
      body,
    }),
  );
}
