# Brand and websites

Status: draft · Last reviewed: 2026-09-30

Scope alignment updated: 2026-10-07. The [V1 scope](v1-scope.md) governs the current portfolio application. Brand direction applies to V1, but the expanded commercial sitemap and journeys below are V2+ context. They do not require a full storefront, real fulfilment or a recycling program before V1 acceptance.

## 1. Brand architecture

```
OneCreations                          creative studio and public portfolio
└── One3D by OneCreations             customer-facing manufacturing business
    ├── Custom figurines
    ├── Print your model
    ├── Batch orders
    ├── PrintBack                     collection, returns and rewards (later)
    └── PrintFlow                     internal operating software
```

| Name | Visibility |
|---|---|
| OneCreations | Main creative identity. It is the parent brand. |
| One3D by OneCreations | Commercial identity. Always shown with the "by OneCreations" endorsement at launch. |
| PrintBack | Public program name, once it actually operates |
| Print Credits | Explained as store rewards, with clear terms |
| PrintFlow | Operator interface, engineering articles, GitHub, interviews |
| RePrint | Postponed. The batch-preserving idea becomes a service inside PrintBack called "Make from your material". |

### Is One3D the right name?

It is short and relevant but not very distinctive, and another business already uses "One3D" for design and manufacturing. That is a potential conflict, not a legal conclusion.

- Keep One3D as the working name, and use "by OneCreations" consistently.
- Run trademark (IP India public search) and domain checks before spending much on branding.
- If clearance is a problem, run the commercial site as "OneCreations — Custom Manufacturing" while choosing a defensible product name.

Adding "by OneCreations" does not by itself resolve a trademark conflict.

## 2. Domains

| Host | Purpose |
|---|---|
| `onecreations.in` | OneCreations portfolio. This is the existing Next.js 16 app on DigitalOcean App Platform. |
| `one3d.store` | Intended One3D customer application and labelled V1 demonstration |
| `one3d.store/ops` | Operator screens in the same One3D application, protected by backend authorization |

OneCreations remains a separate website, repository and deployment. One3D has one responsive frontend for customer and operator routes, backed by the same Spring application. An `/ops` path does not grant permission; every protected API checks role and ownership. These addresses are intended destinations, not a claim of an existing deployment.

Use consistent canonical URLs and links when One3D is published. Don't publish the same story in full on both sites.

## 3. How the two sites relate

OneCreations links to One3D. It never reads One3D's data.

- **On OneCreations today**, after the umbrella repositioning:
  - the header has a "One3D" link;
  - the home page has a One3D section;
  - printed pieces have a "Create yours with One3D" link;
  - the footer carries the endorsement.
- **Where the link points:** all of these links come from one setting, `NEXT_PUBLIC_ONE3D_URL`. Set it to `https://one3d.store` when the site is available; until then, use the existing early-access email fallback.
- **Sharing message:** "Nothing sold, everything shared" stops working once the parent visibly owns a business, so it is replaced with:

> We share how we make things. One3D helps you make yours.

"Knowledge is shared; manufacturing is paid" is a coherent model, as long as what is shared is something we have the right to publish.

### Publishing a customer project

```
One3D order completed
→ customer publication permission recorded
→ editorial selection
→ sanitised photos and story prepared
→ public assets published
→ OneCreations content entry created (git-as-CMS / local admin console)
```

OneCreations receives approved publication assets only. The case study can live on OneCreations, while One3D shows a concise commercial example: what was made, which options were chosen, what the customer received, and what similar work costs. The two pages link to each other.

### Proof and metrics

Every published metric states the period measured, sample size, definition, last update, and whether it includes prototypes. Until there is enough data, show photographs and process evidence. A large counter filled with projections is worse than no counter.

## 4. OneCreations sitemap

### Current site (kept)

```
Home ─ About, index of pillars, print story, workshop carousel, One3D section, bento, footer
Creations ▾  3D Models · 3D Products (+ detail) · VFX · Drone · Astrophotography · Collector's Items
Projects (CSE case studies, + detail)
Tutorials (+ step-by-step detail)
One3D ↗
```

### Possible later simplification

Merge Creations and Projects into a single **Work** index. It would carry these metadata dimensions:

- **Medium:** object, image, motion, software.
- **Discipline:** astrophotography, robotics, fabrication, animation.
- **Technique:** FDM, electronics, CAD, compositing.
- **Status:** finished, ongoing, experiment.

It would also add a **Journal** (workshop notes, experiments, failed experiments, and a "Building One3D" series), and 301-redirect the old routes. A robotics project can be both an object and software, so no new top-level navigation item is needed for each discipline. Do this only when the current pillar structure starts to hurt. Empty categories weaken the site.

## 5. One3D sitemap

### V1: the bounded portfolio application

- One product page and catalog-order action.
- Signed-in custom STL request, revision, manual quote and approval screens.
- Sandbox checkout and the customer's own order timeline.
- `/ops`: review/quote queue, release/assignment, attempts, simulator reconciliation, clearance, QC and manual handoff.
- Sign-in, useful error/empty/loading states and clear notices identifying demo data, test payments and simulated printing.

There is no requirement to build the expanded navigation below. Keep the V1 interface small and refer to [the scope](v1-scope.md) for exact limits.

### V2+: candidate commercial sitemap

Use descriptive navigation instead of CREATE / PRINT / PRODUCE, which first-time visitors won't understand: **Custom figurines · Print your model · Batch orders · How it works**. Add **Recycle & rewards** only when PrintBack really operates.

```
Home
Custom figurines ── styles and real examples · sizes, finishes, price guidance · photo requirements · start a request
Print your model ── upload · geometry review · material and configuration · quote
Batch orders ───── capabilities and examples · request a quote · sample and approval process
How it works ───── design approval · materials and finishes · quality standards · delivery · care and repairs
PrintBack (later) ─ accepted materials · rates and terms · drop-off/collection · organisation programs · verified results
Customer area ──── orders · approvals · files and designs · invoices and addresses · support · credits (later)
Operator app ───── work · production · materials · customers · finance and settings
Policies ───────── terms · privacy · cancellation and refunds · shipping · design and IP rights · rewards terms
```

The first home page, in order:

1. One finished product, photographed well.
2. A specific proposition.
3. Primary action: "Create a custom figurine".
4. Secondary action: "Print an existing model".
5. Real examples with size, material, finish and lead time.
6. The approval process.
7. Price guidance.
8. Delivery and support.
9. A quieter link for business batches.

## 6. Visual direction

### Chosen references — 2026-09-30

Use **Omarchy Matte Black for OneCreations** and **Omarchy Vantablack for One3D**. These names refer to the two themes shown in the supplied screenshots and checked on [Omarchy](https://omarchy.org/). This supersedes the earlier violet-accent direction.

This section records the direction for a future One3D implementation. The current change is documentation only; building the One3D application remains a separate task.

| Element | OneCreations | One3D |
|---|---|---|
| Reference | Omarchy Matte Black | Omarchy Vantablack |
| Feeling | Warm, tactile creative workshop | Precise, calm manufacturing service |
| Layout | Generous space, strong imagery, fine dividers | Consistent forms, cards, summaries and timelines |
| Typography | Readable sans body; restrained monospace labels and metadata; own wordmark | Readable sans for instructions and forms; monospace for dimensions, prices and order identifiers |
| Motion | One optional pixel-field effect in the home hero | Functional feedback and progress; any decorative effect confined to a future marketing hero |
| Photography | Process, atmosphere, experiments | Product detail, scale, finish, packaging |
| Colour | Charcoal surfaces, warm white text, restrained amber accent | Black and graphite surfaces, white text, silver accents; semantic status colours when needed |
| Controls | Mostly square geometry, visible borders, quiet hover changes | Clear boundaries and labels, strong focus states, comfortable touch targets |
| Main action | Explore the work | Configure, approve, pay, track |

The sites connect through related wordmarks, a shared typographic foundation, the same photography standards, and consistent material and process terms. Keep the original colours of product photographs, finishes and materials. The monochrome One3D interface must not desaturate the products it sells.

### Reference colours and future tokens

These values were observed in the [official Omarchy theme stylesheet](https://github.com/omacom/omarchy-site/blob/master/src/styles.css) on 2026-09-30. They are reference values, not a claim that every possible foreground/background pairing is accessible.

| Role | Matte Black reference | Vantablack reference |
|---|---|---|
| Page | `#121212` | `#000000` |
| Deeper region | `#0d0d0d` | `#000000` |
| Surfaces | `#181818`, `#1e1e1e` | `#0d0d0d`, `#1a1a1a` |
| Main text | `#eaeaea` | `#ffffff` |
| Secondary text | `#bebebe` | `#ffffff` |
| Muted text | `#8a8a8a` | `#a8a8a8` |
| Accent | `#e68e0d` | `#8d8d8d` |

For future implementation, define semantic tokens for page, surface, elevated surface, text, muted text, decorative border, control boundary, accent, text on accent, focus, success, warning and error. Preserve distinct surface levels instead of replacing all dark shades with one colour.

For One3D primary actions, start with a light neutral fill and dark text. Test silver buttons and all hover, disabled, error and focus states as complete pairs. Decorative separators can be subtle; boundaries needed to identify an input or control must remain clear. Use a label and icon alongside status colour, such as “Needs approval” or “Payment failed”.

Keep the matte treatment: flat surfaces, restrained shadows, fine dividers and limited corner rounding. Avoid glass panels, large glows, competing gradients and a full-page terminal aesthetic. Omarchy's pixel wordmark, desktop previews and icons remain its identity; create original OneCreations and One3D assets. Review the applicable licence before reusing any source, font or asset.

### Pixel animation

The current Omarchy background resembles cellular automata, but its [official implementation](https://github.com/omacom/omarchy-site/blob/master/src/components/HeroPixelField.tsx) uses animated noise, ordered dithering and pointer effects. It is not a Conway's Game of Life simulation.

For OneCreations, a genuine **Conway B3/S23** simulation is the recommended original interpretation: cells are born with three neighbours and survive with two or three. Keep the field sparse around the hero edges and leave the heading, actions and featured work visually quiet. Keep the readable brand name present throughout. Use a single decorative effect; avoid stacking the simulation with a shader, ambient video, animated grain and a moving divider.

Future implementation requirements:

- Use a small Canvas 2D grid with bounded resolution and a capped device-pixel ratio. A fixed simulation step around 8–12 updates per second is a starting point to profile, not an accessibility guarantee.
- Keep simulation buffers outside React rendering. Clean up animation loops, observers and listeners on unmount.
- Pause when offscreen or when the tab is hidden. Provide a visible pause/resume control and a static fallback when JavaScript is unavailable.
- Respect `prefers-reduced-motion` from first render and when it changes: show a static composition and stop simulation, video autoplay, parallax and decorative transitions. Do not merely hide a canvas while its loop continues running.
- Keep decorative canvas content out of the accessibility tree and away from pointer/keyboard interaction. Pointer effects must never obstruct links or require dragging on touch screens.
- Avoid large, bright, synchronised flashes. Keep essential content outside the effect and usable independently of it.
- Do not put continuous decorative animation behind One3D configuration, upload, checkout, approval or operator screens. A future marketing hero may use a quieter monochrome interpretation after performance and accessibility checks.

### Accessibility and performance acceptance

Target [WCAG 2.2 AA](https://www.w3.org/WAI/WCAG22/quickref/): normal text contrast of at least 4.5:1, large text at least 3:1, and the applicable 3:1 non-text contrast requirements for controls and states. Test keyboard access, visible focus, focus obscured by sticky headers, labelled form errors, screen-reader announcements, reflow at 320 CSS pixels, and text zoom. Aim for 44×44 CSS pixel interactive targets; the AA minimum target criterion is 24×24 with defined exceptions.

For nonessential animation that starts automatically and continues alongside content for more than five seconds, provide pause, stop or hide controls. Reduced-motion support complements this requirement; it does not replace the control. See [W3C Pause, Stop, Hide](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html).

Future media should use reserved dimensions, responsive images, lightweight posters, and user-triggered loading for heavy 3D viewers. Avoid autoplaying every work card. Target field Core Web Vitals of LCP ≤2.5 s, INP ≤200 ms and CLS ≤0.1 at the 75th percentile, measured separately for mobile and desktop. Lab checks guide development; they do not establish field results. See [Web Vitals](https://web.dev/articles/vitals).

## 7. Future commercial customer journeys (V2+)

These journeys describe the later business. V1 uses the narrower catalog/custom quote demonstration above; design fees, deposits, reference photos, batch orders, rewards and real shipment are not V1 requirements.

### A. Custom figurine

1. **Understand the offer.** Show styles, real physical examples, indicative price, revision allowance and lead time before asking for personal photos.
2. **Submit references.** Collect photos, style, size, pose, accessories, required date and permissions.
3. **Feasibility review.** Check photo suitability and whether the request fits a product tier.
4. **Agree scope and pay for design work.** Take a disclosed design/booking fee, credited toward the order, that covers the expected early work. Don't sculpt for hours before the customer commits.
5. **Approve the concept,** within a defined number of adjustments.
6. **Review the model** with views that reveal the geometry, not only flattering renders.
7. **Confirm the final quote.** For a standard product, the price should already be known. For an unusual commission, agree the range and change process before paid design starts.
8. **Approve the production revision and pay the balance.** This freezes the model revision, size, materials, finish, accessories, price and delivery commitment.
9. **Manufacture and finish,** showing understandable stages and any customer action needed.
10. **Final QC, dispatch and aftercare,** with care instructions and a repair/replacement route.

**Revision policy:** a customer changing the agreed scope is different from us fixing a defect. Fixes never consume a paid revision.

**Cancellation charges** follow the work actually committed:

- before work starts;
- after design begins;
- after design approval;
- after manufacturing starts.

Avoid an absolute "custom products are never refundable" rule. Defective or misdescribed goods carry separate obligations under the Consumer Protection (E-Commerce) Rules.

### B. Existing model

```
upload → security and geometry checks → confirm units and dimensions → material and finish
→ preparation/slicing review → quote → payment → production → QC → delivery
```

STL files don't reliably carry units, so always confirm the physical dimensions. Require human review for:

- thin features;
- tight tolerances;
- unusual materials;
- complex supports;
- functional loads;
- high-value orders.

### C. Business batch

```
requirements and rights → design review → sample quote → sample → written sample acceptance
→ batch quote and deposit → production release → batch QC → balance → delivery
```

Save an approved manufacturing profile for reorders. It covers sampling rules, acceptable variation, packaging, quantities, ownership, and what happens if the design changes. Don't offer long payment terms before they can be funded.

### D. Returning customer

Make these actions obvious:

- reorder an approved design;
- change the nameplate or another permitted feature;
- request a repair;
- download an invoice;
- reuse references with permission;
- view rewards;
- review retained data.

### Authentication

- OneCreations stays public with no login.
- V1 permits public product browsing, but requires an authenticated customer for requests, uploads, approvals and private records. The backend enforces customer/operator roles and record ownership.
- A restricted guest request flow is a later option, with identity verification before sensitive uploads or approvals.
- Cognito handles identity, with email and possibly Google sign-in. Phone OTP adds cost and abuse controls that aren't justified yet.
- A mobile-friendly website is enough for V1. A PWA can come later for operator scanning. A native app is not a priority.

## 8. Future operator journeys and QR codes (V2+)

V1 includes only the operator actions listed in section 5 and the scope document. QR scanning, material inventory, finishing/assembly workflows, recycling and credit adjustments below are deferred.

| Situation | Workflow |
|---|---|
| Incoming order | Check references, scope, due date, payment, missing info. Accept or ask for clarification. |
| Model preparation | Work on a new revision, attach prep results, request approval |
| Production release | Verify approval, payment, parts list, material and profiles |
| Failed print | Record evidence and consumed resources, diagnose, inspect the machine, authorise another attempt |
| QC failure | Identify the failed part and defect. Choose rework, replacement or rejection. |
| Finishing | Scan the tray, confirm parts, follow the recipe, record time, do final QC |
| Recycling batch | Inspect, tare, weigh, classify, record acceptance, label, route |
| Credit adjustment | Reason and evidence, permission limits, approval threshold |
| Dispatch | Verify items, packaging, address and the shipment record |

Start with three QR codes:

1. **Order/part tray:** identifies the work being handled.
2. **Material batch:** identifies inventory and its movements.
3. **Intake:** identifies a collection session.

Add a finished-product QR only when it carries useful care, repair or provenance information. A QR code is an identifier, not authorisation. Scanning one must never let an unauthenticated person issue credits or change production state.

A private object page can show:

- the approved design revision;
- material and declared recycled fraction;
- manufacturing date;
- finish;
- care instructions;
- replaceable parts;
- repair options.

Customer likenesses stay private by default. This doesn't need a blockchain.
