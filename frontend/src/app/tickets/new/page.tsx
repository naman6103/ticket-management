"use client";

import { useRouter } from "next/navigation";
import { TicketForm } from "@/components/tickets/TicketForm";

export default function NewTicketPage() {
  const router = useRouter();

  return (
    <main>
      <h1>Create ticket</h1>
      <div className="card">
        <TicketForm mode="create" onSuccess={(ticket) => router.push(`/tickets/${ticket.id}`)} />
      </div>
    </main>
  );
}
