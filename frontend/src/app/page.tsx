"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useSession } from "@/components/Session";
import { Empty, ErrorMessage, Loading } from "@/components/States";
import { SIGN_IN_URL, api, describeError } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import type { CatalogItem } from "@/lib/types";

/** The catalog: one demo item in V1. Anyone can browse; ordering needs sign-in. */
export default function CatalogPage() {
  const { session } = useSession();
  const router = useRouter();
  const [items, setItems] = useState<CatalogItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [orderingId, setOrderingId] = useState<string | null>(null);

  useEffect(() => {
    api.catalog().then(setItems).catch((e: unknown) => setError(describeError(e)));
  }, []);

  async function order(item: CatalogItem) {
    setOrderingId(item.configurationId);
    setError(null);
    try {
      const placed = await api.placeOrder(item.configurationId);
      router.push(`/orders/${placed.id}`);
    } catch (e) {
      setError(describeError(e));
      setOrderingId(null);
    }
  }

  return (
    <>
      <h1>Catalog</h1>
      {error && <ErrorMessage message={error} />}
      {!items && !error && <Loading what="the catalog" />}
      {items && items.length === 0 && <Empty>Nothing can be ordered right now.</Empty>}
      {items && items.length > 0 && (
        <ul className="cards">
          {items.map((item) => (
            <li key={item.configurationId} className="card">
              <h2>{item.productName}</h2>
              <p className="muted">{item.description}</p>
              <p className="price">{formatMoney(item.price)}</p>
              {session.status === "signed-in" ? (
                <button
                  type="button"
                  className="button"
                  onClick={() => order(item)}
                  disabled={orderingId !== null}
                  aria-label={`Order ${item.productName}`}
                >
                  {orderingId === item.configurationId ? "Placing order…" : "Order"}
                </button>
              ) : (
                <a className="button" href={SIGN_IN_URL}>
                  Sign in to order
                </a>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
