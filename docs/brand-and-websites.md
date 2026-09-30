# Brand and websites

Status: draft · Last reviewed: 2026-09-30

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
| `make.onecreations.in` | One3D customer application |
| `ops.make.onecreations.in` | Restricted operator application |

This keeps two distinct websites without making an unverified domain purchase a prerequisite. A subdomain does not mean sharing a frontend, deployment, authentication or database. Google states that it has no ranking preference between subdomains and subdirectories.

If the commercial site later moves to its own domain (for example `one3d.in`, if available and cleared), use redirects, consistent canonical URLs and updated links. Don't publish the same story in full on both sites.

## 3. How the two sites relate

OneCreations links to One3D. It never reads One3D's data.

- **On OneCreations today**, after the umbrella repositioning:
  - the header has a "One3D" link;
  - the home page has a One3D section;
  - printed pieces have a "Create yours with One3D" link;
  - the footer carries the endorsement.
- **Where the link points:** all of these links come from one setting, `NEXT_PUBLIC_ONE3D_URL`. Until `make.onecreations.in` exists, they fall back to an early-access email.
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

| Element | OneCreations | One3D |
|---|---|---|
| Feeling | Creative workshop | Clear, capable manufacturing service |
| Layout | Large imagery, varied compositions | Consistent forms, cards and timelines |
| Typography | Expressive display type (Instrument Serif, Object Sans) with readable body text | Readable type with strong hierarchy |
| Motion | Selective (GSAP, R3F), optional | Functional feedback and progress |
| Photography | Process, atmosphere, experiments | Product detail, scale, finish, packaging |
| Colour | Flexible project accents on the dark base | Restrained brand accent plus semantic status colours |
| Main action | Explore the work | Configure, approve, pay, track |

The sites connect through related wordmarks, a shared typographic foundation, a small common palette, the same photography standards, and consistent material and process terms. They should not look like the same dashboard.

## 7. Customer journeys

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
- One3D allows guest browsing and a restricted guest request. Email must be verified before sensitive uploads or approvals.
- Cognito handles identity, with email and possibly Google sign-in. Phone OTP adds cost and abuse controls that aren't justified yet.
- A mobile-friendly website is enough for the MVP. A PWA can come later for operator scanning. A native app is not a priority.

## 8. Operator journeys and QR codes

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
