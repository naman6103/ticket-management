"use client";

import { useState } from "react";
import Link from "next/link";
import { SearchFilterBar } from "@/components/tickets/SearchFilterBar";
import { TicketList } from "@/components/tickets/TicketList";
import type { TicketStatus } from "@/types/ticket";

export default function TicketsPage() {
  const [filters, setFilters] = useState<{ q: string; status: TicketStatus | undefined }>({
    q: "",
    status: undefined,
  });

  return (
    <main>
      <div className="pageHeader">
        <h1>Tickets</h1>
        <Link href="/tickets/new" className="button">
          Create ticket
        </Link>
      </div>
      <SearchFilterBar q={filters.q} status={filters.status} onChange={setFilters} />
      <TicketList q={filters.q} status={filters.status} />
    </main>
  );
}
