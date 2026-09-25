"use client";

import { useCallback, useRef } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { listTickets } from "@/lib/api/tickets";
import { TicketRow } from "./TicketRow";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";
import type { TicketStatus } from "@/types/ticket";

const PAGE_SIZE = 20;

interface TicketListProps {
  q: string;
  status: TicketStatus | undefined;
}

/**
 * Infinite-scroll ticket list (FR-004a). Query key includes q/status so
 * changing either resets to a fresh page-0 fetch (FR-004).
 */
export function TicketList({ q, status }: TicketListProps) {
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    isLoading,
    isError,
  } = useInfiniteQuery({
    queryKey: ["tickets", { q, status }],
    queryFn: ({ pageParam }) => listTickets({ q, status, page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });

  // Latest-value refs so the observer callback (created once per DOM node,
  // via the callback ref below) never closes over stale hasNextPage /
  // isFetchingNextPage / fetchNextPage values.
  const hasNextPageRef = useRef(hasNextPage);
  hasNextPageRef.current = hasNextPage;
  const isFetchingNextPageRef = useRef(isFetchingNextPage);
  isFetchingNextPageRef.current = isFetchingNextPage;
  const fetchNextPageRef = useRef(fetchNextPage);
  fetchNextPageRef.current = fetchNextPage;

  const observerRef = useRef<IntersectionObserver | null>(null);

  // A callback ref fires exactly when the sentinel <div> mounts/unmounts —
  // unlike a plain ref read inside a useEffect gated by unrelated
  // dependencies, this can't miss a mount that happens without any of
  // those dependencies changing (e.g. a single-page result where
  // hasNextPage is `false` from the very first render).
  const sentinelRef = useCallback((node: HTMLDivElement | null) => {
    observerRef.current?.disconnect();
    observerRef.current = null;

    if (!node) return;

    observerRef.current = new IntersectionObserver((entries) => {
      if (
        entries[0]?.isIntersecting &&
        hasNextPageRef.current &&
        !isFetchingNextPageRef.current
      ) {
        fetchNextPageRef.current();
      }
    });
    observerRef.current.observe(node);
  }, []);

  if (isLoading) {
    return <p>Loading tickets…</p>;
  }

  if (isError) {
    return (
      <ErrorDisplay
        messages={[{ kind: "fullpage", text: "Couldn't reach the server. Please retry." }]}
      />
    );
  }

  const tickets = data?.pages.flatMap((page) => page.content) ?? [];

  if (tickets.length === 0) {
    return <ErrorDisplay messages={[{ kind: "banner", text: "No tickets found." }]} />;
  }

  return (
    <>
      <ul className="ticketList">
        {tickets.map((ticket) => (
          <TicketRow key={ticket.id} ticket={ticket} />
        ))}
      </ul>
      {isFetchNextPageError && (
        <ErrorDisplay
          messages={[{ kind: "banner", text: "Couldn't load more — retry scrolling." }]}
        />
      )}
      <div ref={sentinelRef} data-testid="ticket-list-sentinel" />
    </>
  );
}
