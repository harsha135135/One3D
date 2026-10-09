import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { Header } from "@/components/Header";
import { SessionProvider } from "@/components/Session";
import "./globals.css";

export const metadata: Metadata = {
  title: "One3D",
  description: "Order a 3D-printed part and follow it through production. Demonstration: payments are simulated.",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1, colorScheme: "dark" };

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <SessionProvider>
          <Header />
          <main id="main" tabIndex={-1}>
            {children}
          </main>
          <footer className="site-footer muted">
            Demonstration system: payments are simulated and no real printer is controlled.
          </footer>
        </SessionProvider>
      </body>
    </html>
  );
}
