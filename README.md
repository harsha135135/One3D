# One3D by OneCreations

**Custom collectibles, made from your references and finished by hand.**
From an approved design to a finished object.

One3D is the custom-manufacturing business of [OneCreations](https://github.com/harsha135135/OneCreations), a creative studio from Bangalore. This repository holds One3D's plans and architecture. Its operating software is **PrintFlow**, and its collection and rewards program is **PrintBack**.

Status: planning · Last reviewed: 2026-09-30

## Brand structure

```
OneCreations                     creative studio and public portfolio (separate repo and site)
└── One3D by OneCreations        customer-facing manufacturing business
    ├── Custom figurines
    ├── Print your model
    ├── Batch orders
    ├── PrintBack                collection, returns and rewards (later)
    │   └── Make from your material   premium batch-preserving service (later)
    └── PrintFlow                internal operating software
```

| Name | Who sees it |
|---|---|
| OneCreations | Main creative identity |
| One3D by OneCreations | Commercial identity, always endorsed at launch |
| PrintBack / Print Credits | Public, once a collection route actually operates |
| PrintFlow | Operator UI, engineering write-ups, GitHub, interviews |
| RePrint | Postponed as a separate brand |

## Starting point

- One Bambu Lab X1C (no AMS). Additional printers are bought only after the [purchase gate](docs/business-plan.md#printer-purchase-gate) is met.
- Filament comes from Numakers: PLA+ ₹650/kg, PLA Matte ₹700/kg, specials ₹900–1,000/kg, PETG ₹650/kg.
- ₹3–4 lakh covers the whole first year: equipment, working capital, infrastructure and contingency.

## Planned hosts

| Host | Purpose |
|---|---|
| `onecreations.in` | OneCreations portfolio (existing Next.js app) |
| `make.onecreations.in` | One3D customer application |
| `ops.make.onecreations.in` | Restricted operator application |

Trademark and domain checks for "One3D" are still open. See [brand and websites](docs/brand-and-websites.md#is-one3d-the-right-name).

## Documents

| Document | Contents |
|---|---|
| [docs/business-plan.md](docs/business-plan.md) | Assessment, positioning, budget, purchase gate, segments, product spec, the role of AI, unit economics with Numakers pricing, capacity and break-even |
| [docs/brand-and-websites.md](docs/brand-and-websites.md) | Naming, domains, sitemaps for both sites, visual direction, publication flow, customer and operator journeys |
| [docs/printback.md](docs/printback.md) | Collection pilot, credit economics, ledger and fraud controls, material routes, mass balance and claims |
| [ARCHITECTURE.md](ARCHITECTURE.md) | PrintFlow system architecture, domain model, dispatch protocol, data design, security, cost |
| [docs/roadmap.md](docs/roadmap.md) | MVP boundary, 12-week roadmap, experiments with stop/go rules, DVA mapping, demo script |

## The first milestone

> A customer pays for a defined product, receives something they approve of, and we can explain exactly what it cost, how it was made, what failed, and what we improved.

Repeat that reliably. Then expand the farm and the material program.
