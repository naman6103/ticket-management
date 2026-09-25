import { browserFetch } from "./browserFetch";
import type { AskAnswer } from "@/types/assistant";

export function askAssistant(question: string): Promise<AskAnswer> {
  return browserFetch<AskAnswer>("/api/ai/ask", {
    method: "POST",
    body: JSON.stringify({ question }),
  });
}
