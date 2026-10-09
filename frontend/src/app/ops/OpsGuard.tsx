"use client";

import type { ReactNode } from "react";
import { isOperator, useSession } from "@/components/Session";
import { ErrorMessage, Loading } from "@/components/States";
import { SIGN_IN_URL } from "@/lib/api";

/**
 * Shows operator pages only to operators. This is a convenience: the backend
 * refuses every /api/ops call from anyone else, whatever the UI shows.
 */
export function OpsGuard({ children }: { children: ReactNode }) {
  const { session } = useSession();
  if (session.status === "loading") {
    return <Loading what="your account" />;
  }
  if (session.status === "signed-out") {
    return (
      <p>
        <a className="button" href={SIGN_IN_URL}>
          Sign in as an operator
        </a>
      </p>
    );
  }
  if (!isOperator(session)) {
    return <ErrorMessage message="This page is for operators." />;
  }
  return <>{children}</>;
}
