"use client";

import { useRouter } from "next/navigation";
import { ModelSubmissionForm } from "@/components/ModelSubmissionForm";
import { useSession } from "@/components/Session";
import { Loading } from "@/components/States";
import { SIGN_IN_URL, api } from "@/lib/api";

/** A new custom part request: upload a model and say how it should be made. */
export default function NewRequestPage() {
  const { session } = useSession();
  const router = useRouter();

  return (
    <>
      <h1>Print your own model</h1>
      <p className="muted">
        An operator reviews your model and sends a quote. Nothing is charged until you accept it.
      </p>
      {session.status === "loading" && <Loading what="your account" />}
      {session.status === "signed-out" && (
        <p>
          <a className="button" href={SIGN_IN_URL}>
            Sign in to upload a model
          </a>
        </p>
      )}
      {session.status === "signed-in" && (
        <ModelSubmissionForm
          submitLabel="Send request"
          onSubmit={async (requirements) => {
            const created = await api.submitRequest(requirements);
            router.push(`/requests/${created.id}`);
          }}
        />
      )}
    </>
  );
}
