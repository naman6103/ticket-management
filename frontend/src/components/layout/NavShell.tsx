import Link from "next/link";

/**
 * Persistent top-level navigation, mounted once in app/layout.tsx so every
 * screen shares it (FR-015).
 */
export function NavShell() {
  return (
    <nav aria-label="Main navigation" className="navShell">
      <Link href="/tickets">Tickets</Link>
      <Link href="/assistant">Ask Assistant</Link>
    </nav>
  );
}
