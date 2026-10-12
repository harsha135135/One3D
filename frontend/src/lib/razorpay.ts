// Razorpay Checkout (test mode) in the browser. The script comes from
// Razorpay's own domain; card or UPI details are entered in Razorpay's window,
// never on this site. What Checkout reports back is not trusted: the page then
// asks the backend, which checks with Razorpay itself.

import type { Checkout } from "./types";

type RazorpayOptions = {
  key: string;
  amount: number;
  currency: string;
  order_id: string;
  name: string;
  description: string;
  timeout: number;
  prefill?: { email?: string };
  theme?: { color?: string };
  handler: () => void;
  modal?: { ondismiss?: () => void };
};

type RazorpayInstance = { open: () => void; on: (event: string, callback: () => void) => void };

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance;
  }
}

const SCRIPT_URL = "https://checkout.razorpay.com/v1/checkout.js";
let loading: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if (window.Razorpay) {
    return Promise.resolve();
  }
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      loading = null;
      reject(new Error("Razorpay Checkout could not be loaded."));
    };
    document.head.appendChild(script);
  });
  return loading;
}

/** Opens Checkout; resolves "paid" when Checkout reports success, "closed" if the customer closes it. */
export async function openRazorpay(checkout: Checkout, email: string | null): Promise<"paid" | "closed"> {
  await loadScript();
  const Razorpay = window.Razorpay;
  if (!Razorpay) {
    throw new Error("Razorpay Checkout could not be loaded.");
  }
  const secondsLeft = Math.max(60, Math.floor((new Date(checkout.expiresAt).getTime() - Date.now()) / 1000));
  return new Promise((resolve) => {
    const instance = new Razorpay({
      key: checkout.keyId,
      amount: checkout.amountMinor,
      currency: checkout.currency,
      order_id: checkout.providerOrderId,
      name: "One3D (test mode)",
      description: checkout.itemName,
      timeout: secondsLeft,
      prefill: email ? { email } : undefined,
      theme: { color: "#000000" },
      handler: () => resolve("paid"),
      modal: { ondismiss: () => resolve("closed") },
    });
    instance.open();
  });
}
