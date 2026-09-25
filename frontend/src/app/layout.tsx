import type { ReactNode } from "react";
import { NavShell } from "@/components/layout/NavShell";
import { Providers } from "@/components/layout/Providers";
import { PageErrorBoundary } from "@/components/layout/PageErrorBoundary";
import "./globals.css";

export const metadata = {
  title: "Ticket Management",
  description: "Ticket Management Web UI",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <NavShell />
          <div className="page">
            <PageErrorBoundary>{children}</PageErrorBoundary>
          </div>
        </Providers>
      </body>
    </html>
  );
}
