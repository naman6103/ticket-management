import Link from "next/link";

/**
 * Persistent top-level navigation, mounted once in app/layout.tsx so every
 * screen shares it (FR-015). Deliberately has no AI-panel entry yet — that
 * slot is reserved by this being the one place any future nav item gets
 * added, not by rendering a disabled placeholder (FR-016).
 */
export function NavShell() {
  return (
    <nav aria-label="Main navigation" className="navShell">
      <Link href="/tickets">Tickets</Link>
    </nav>
  );
}
