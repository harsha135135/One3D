"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { ApiError, api } from "@/lib/api";
import type { Me } from "@/lib/types";

type SessionState =
  | { status: "loading" }
  | { status: "signed-out" }
  | { status: "signed-in"; me: Me }
  | { status: "error" };

const SessionContext = createContext<{ session: SessionState; refresh: () => void }>({
  session: { status: "loading" },
  refresh: () => {},
});

/** Asks the backend who is signed in (GET /api/me) and shares the answer with all pages. */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionState>({ status: "loading" });

  const refresh = useCallback(() => {
    api
      .me()
      .then((me) => setSession({ status: "signed-in", me }))
      .catch((error: unknown) =>
        setSession(error instanceof ApiError && error.status === 401 ? { status: "signed-out" } : { status: "error" }),
      );
  }, []);

  useEffect(refresh, [refresh]);

  return <SessionContext.Provider value={{ session, refresh }}>{children}</SessionContext.Provider>;
}

export function useSession() {
  return useContext(SessionContext);
}

export function isOperator(session: SessionState): boolean {
  return session.status === "signed-in" && session.me.roles.includes("OPERATOR");
}
