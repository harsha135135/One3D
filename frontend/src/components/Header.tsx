"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { SIGN_IN_URL, api } from "@/lib/api";
import { isOperator, useSession } from "./Session";

export function Header() {
  const { session, refresh } = useSession();
  const router = useRouter();

  async function signOut() {
    await api.logout().catch(() => undefined);
    refresh();
    router.push("/");
  }

  return (
    <header className="site-header">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <Link href="/" className="brand">
        One3D <span className="brand-sub">by OneCreations</span>
      </Link>
      <nav aria-label="Main">
        <ul className="nav-list">
          <li>
            <Link href="/">Catalog</Link>
          </li>
          {session.status === "signed-in" && (
            <li>
              <Link href="/orders">My orders</Link>
            </li>
          )}
          {isOperator(session) && (
            <>
              <li>
                <Link href="/ops">Operations</Link>
              </li>
              <li>
                <Link href="/ops/printers">Printers</Link>
              </li>
            </>
          )}
        </ul>
      </nav>
      <div className="account">
        {session.status === "signed-in" && (
          <>
            <span className="muted" data-testid="signed-in-as">
              {session.me.email ?? "Signed in"}
            </span>
            <button type="button" className="button secondary" onClick={signOut}>
              Sign out
            </button>
          </>
        )}
        {session.status === "signed-out" && (
          <a className="button" href={SIGN_IN_URL}>
            Sign in
          </a>
        )}
      </div>
    </header>
  );
}
