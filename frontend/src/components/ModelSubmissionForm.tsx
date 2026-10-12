"use client";

import { useState, type FormEvent } from "react";
import { ApiError, api, describeError, putToStorage } from "@/lib/api";
import type { Requirements } from "@/lib/types";
import { ErrorMessage } from "./States";

/** V1 accepts one STL file of at most 20 MiB. The backend checks this again. */
export const MAX_FILE_BYTES = 20 * 1024 * 1024;
const MATERIALS = ["PLA", "PLA Matte", "PETG"];
const DEFAULT_MATERIAL = "PLA";
const CHECK_POLL_MS = 1000;
const CHECK_TIMEOUT_MS = 90_000;

type Step = "idle" | "uploading" | "checking" | "submitting";

const STEP_TEXT: Record<Step, string> = {
  idle: "",
  uploading: "Uploading your file…",
  checking: "Checking the file…",
  submitting: "Sending your request…",
};

/**
 * Uploads one STL file straight to private storage, waits for the backend's
 * check, then sends the requirements. Used for a new request and for a new
 * revision of an existing one.
 */
export function ModelSubmissionForm({
  submitLabel,
  onSubmit,
}: {
  submitLabel: string;
  onSubmit: (requirements: Requirements) => Promise<void>;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [material, setMaterial] = useState(DEFAULT_MATERIAL);
  const [colour, setColour] = useState("");
  const [notes, setNotes] = useState("");
  const [step, setStep] = useState<Step>("idle");
  const [error, setError] = useState<string | null>(null);

  function chooseFile(chosen: File | null) {
    setError(null);
    if (chosen && !chosen.name.toLowerCase().endsWith(".stl")) {
      setError("Choose an .stl file.");
      setFile(null);
      return;
    }
    if (chosen && chosen.size > MAX_FILE_BYTES) {
      setError("The file is larger than 20 MB.");
      setFile(null);
      return;
    }
    setFile(chosen);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      setError("Choose an .stl file.");
      return;
    }
    setError(null);
    try {
      setStep("uploading");
      const started = await api.startUpload(file.name, file.size);
      await putToStorage(started.upload, file);
      await api.finalizeUpload(started.artifact.id);

      setStep("checking");
      const checked = await waitForCheck(started.artifact.id);
      if (checked.status === "REJECTED") {
        setError(`The file was not accepted: ${checked.rejectionReason ?? "it is not a valid STL model."}`);
        setStep("idle");
        return;
      }

      setStep("submitting");
      await onSubmit({ artifactId: started.artifact.id, material, colour, notes });
      setStep("idle");
    } catch (e) {
      setError(e instanceof ApiError && e.code === "CHECK_TIMEOUT" ? e.message : describeError(e));
      setStep("idle");
    }
  }

  const busy = step !== "idle";

  return (
    <form className="form" onSubmit={submit} aria-describedby="model-rules">
      <p id="model-rules" className="muted">
        One STL file, up to 20 MB. It is stored privately and only you and our operators can download it.
      </p>
      <label>
        Model file (.stl)
        <input
          type="file"
          accept=".stl,model/stl,application/sla"
          required
          disabled={busy}
          onChange={(e) => chooseFile(e.target.files?.[0] ?? null)}
        />
      </label>
      <label>
        Material
        <select value={material} disabled={busy} onChange={(e) => setMaterial(e.target.value)}>
          {MATERIALS.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
      </label>
      <label>
        Colour
        <input
          type="text"
          required
          maxLength={80}
          value={colour}
          disabled={busy}
          onChange={(e) => setColour(e.target.value)}
        />
      </label>
      <label>
        Notes for the operator
        <textarea
          maxLength={1000}
          rows={3}
          value={notes}
          disabled={busy}
          onChange={(e) => setNotes(e.target.value)}
        />
      </label>
      {error && <ErrorMessage message={error} />}
      <p role="status" aria-live="polite">
        {STEP_TEXT[step]}
      </p>
      <div className="actions">
        <button type="submit" className="button" disabled={busy}>
          {busy ? "Please wait…" : submitLabel}
        </button>
      </div>
    </form>
  );
}

async function waitForCheck(artifactId: string) {
  const deadline = Date.now() + CHECK_TIMEOUT_MS;
  for (;;) {
    const artifact = await api.file(artifactId);
    if (artifact.status === "VERIFIED" || artifact.status === "REJECTED") {
      return artifact;
    }
    if (Date.now() > deadline) {
      throw new ApiError(0, "CHECK_TIMEOUT", "The file check is taking longer than expected. Try again later.");
    }
    await new Promise((resolve) => setTimeout(resolve, CHECK_POLL_MS));
  }
}
