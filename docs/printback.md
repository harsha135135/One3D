# PrintBack: collection, rewards and material recovery

Status: draft · not operating · Last reviewed: 2026-09-30

> **Deferred beyond V1.** Scope classification updated 2026-10-07. Collection software, rewards, credit ledgers and synthetic recycling demonstrations are not required for the [hiring-focused V1](v1-scope.md). This document preserves future business research, not current implementation instructions.

PrintBack is One3D's future take-back and material-recovery program. It launches publicly only once a real collection route, buyer and funded rewards budget exist. Its software has not been implemented or tested here. Any later prototype must use clearly labelled synthetic data until real operations and their evidence exist.

## 1. Start as a bounded program

The first version combines:

- segregation of our own known scrap (Numakers PLA+, Matte, PETG);
- one collection partner;
- one recycler or processor with an agreed specification;
- a narrow acceptance list;
- a small, funded rewards budget;
- documented pickups and recovery evidence.

It does **not** promise "any plastic → printable filament → your custom figurine".

### The hardest conflict

Premium figurines may contain paint, primer, filler, glue, metal pins, magnets, mixed polymers and resin detail parts. All of these make recovery harder. So the take-back promise is:

> Assessment for repair, refurbishment, component recovery and an appropriate recycling route.

Where practical, design future products with marked polymers, replaceable parts and separable components.

### Make from your material

"Make from your material" is the premium batch-preserving service, previously called "RePrint". It is a paid feasibility assessment followed by a segregated batch. It promises batch identity only when the process can actually preserve it. It discloses:

- minimum input;
- losses;
- virgin additions;
- colour variation;
- the fallback if the batch fails.

## 2. Setting credit rates

A ₹100 credit redeemed on an order that would have happened anyway costs about ₹100 of contribution. It is not cheap just because the plastic in the product cost ₹30. With Numakers PLA+ at ₹0.65/g, the polymer in a 300 g figurine costs about ₹195. The credit's cost is set by margin, not by material value.

Credits pay off only if they cause additional profitable purchases, or replace a more expensive acquisition channel.

For each accepted material stream:

```
Net recovery value per accepted kg =
    conservative realised output value
  − sorting − handling − transport − storage − processing − rejection/loss allowance

Maximum credits per kg =
  ( net recovery value + committed sponsor support + approved marketing subsidy
    − other unallocated costs − risk buffer )
  ÷ stress-tested redemption fraction
```

For the first pilot, stress-test at 100% redemption. Keep two customer-facing values separate:

- **Material reward:** funded by recoverable material value.
- **Participation or cleanup bonus:** funded by an explicit campaign budget.

If the economics don't support a positive reward, don't invent a base rate. There is no default positive reward for dirty mixed plastic without a committed downstream route.

Publish rates with:

- material description;
- cleanliness requirements;
- measurement basis;
- validity period;
- location;
- maximum eligible quantity;
- bonus conditions.

Change rates only prospectively, and keep the version applied to each intake.

The individual reward is small. At 20 credits per kg, 1 kg earns ₹20, and ₹1,000 of credit needs 50 kg. That points toward pooled school, apartment and corporate programs rather than individual figurine customers.

## 3. A percentage cap does not protect margin

The illustrative ₹5,000 custom order has about **₹952** of contribution before acquisition and credits, using Numakers pricing (see [business plan §8](business-plan.md#8-unit-economics-illustrative)). A 20% redemption is ₹1,000 and a 25% redemption is ₹1,250. Both exceed that contribution before any paid acquisition.

The internal rule is:

```
Allowed redemption ≤ min( available credit balance,
                          published eligible-product cap,
                          contribution before credits − required contribution floor )
```

Acquisition and other incremental costs are already inside that contribution figure. The public rules are:

- clearly identified eligible products;
- a visible rupee or percentage cap;
- no surprise restrictions at checkout;
- limited stacking;
- clear expiry and refund treatment.

For low-margin custom commissions, a small fixed reward is safer than a large percentage. Recycled feedstock doesn't make labour, printing, finishing or shipping free.

## 4. Test acquisition economics by cohort

All inputs here are experimental, not market facts.

| Item | Assumption |
|---|---|
| Participants | 100 |
| Accepted material | 1,000 kg |
| Recycler proceeds | ₹18,000 |
| Collection, sorting, storage, logistics | ₹13,000 |
| Campaign/admin | ₹5,000 |
| Credits issued | ₹15,000 |
| Credits redeemed at 60% | ₹9,000 |
| Redeeming customers | 60 |
| Actually incremental customers | 30 |

```
Program acquisition cost = 9,000 redemption + 13,000 ops + 5,000 campaign − 18,000 proceeds = ₹9,000
Incremental acquisition cost = 9,000 / 30 = ₹300 per incremental customer
Incremental contribution ≈ 30 × ₹952 − ₹9,000 ≈ ₹19,560
```

The result is sensitive to how many customers are actually incremental:

- **10 incremental customers:** acquisition cost rises to ₹900 each.
- **Zero incremental customers:** the program loses ₹9,000.
- **100% redemption:** the program costs ₹15,000 and needs about 16 incremental custom orders to break even.
- **Outstanding credits:** the other ₹6,000 of credits stays outstanding until it is redeemed or expires.

**Avoid double counting.** Either use pre-discount contribution and subtract redemptions once, or use post-discount contribution and don't subtract them again. Don't add an abstract "marketing value" on top. For each kilogram, count either actual external sale proceeds or a justified internal-substitution value, never both.

## 5. Credit ledger and fraud controls

A `creditBalance` field alone is not enough. The states are:

```
ISSUED · AVAILABLE · RESERVED · REDEEMED · RELEASED · REINSTATED · REVERSED · EXPIRED
```

- **Ledger entries.** They are immutable and use integer units, with atomic balance updates.
- **Intake acceptance.** Each acceptance has a unique transaction key, so repeating the request never issues credits twice.
- **Checkout.** Checkout reserves credits atomically. A confirmed payment settles the reservation. A cancellation or timeout releases it.
- **Refunds.**
  - Partial refunds use the tender allocation recorded at sale.
  - Cash refunds never exceed the cash actually paid.
  - Credits are reinstated under the published policy.
- **Expiry and adjustments.**
  - Expiry is evaluated explicitly. Database TTL is only for cleanup.
  - Manual adjustments need a reason, the right permissions and an approval threshold.
- **Reserve.** Keep a management reserve for expected and stressed redemption. This is not a claim that regulation requires an escrow.

| Abuse | Control |
|---|---|
| Water, sand or metal added for weight | Tare, draining, inspection, sample checks, rejected-weight records |
| Duplicate intake | Unique receipt, event and batch IDs |
| Fake cleanup photos | Organiser-confirmed events. Photos are only supporting evidence. |
| Buying material to farm bonuses | Limited campaign budgets, participant and event caps |
| Employee manipulation | Operator identity, audit history, reconciliation, approval thresholds |
| Multiple accounts | Bonus limits tied to verified participation |
| Credit resale | No transfer or cash-out. Organisation pooling via explicit roles. |
| Refund exploitation | Original tender allocation, idempotent refunds |
| Inflated recovery claims | Partner receipts, inventory reconciliation, evidence status |

Fraud cannot be eliminated. Limit the financial exposure even when verification fails.

How each type of group participates:

- **Schools:** adult-managed pooled accounts that reward participation and learning, never unsafe collection competitions.
- **Apartments:** scheduled collection and shared rewards. No bin goes out without a pickup and storage plan.
- **Companies:** agreed feedstock specs, service fees where needed, and separately priced production.
- **Cleanups:** supervised events, capped bonuses and excluded waste categories.

Work with existing waste collectors. Credits are not necessarily better than the cash they already earn from scrap.

## 6. Material streams and routes

| Stream | Initial policy | Later route |
|---|---|---|
| Own clean PLA/PLA+/Matte scrap | Segregate by known formulation and source (Numakers lot) | Contract recycling, then qualified trials |
| Own clean PETG scrap | Keep separate from PLA and from PET | Dedicated PETG trials |
| Customer PLA/PETG prints | Conditional acceptance with provenance | Qualified partner or controlled processing |
| PET bottles | Partner collection | Established PET recycling |
| HDPE and PP rigids | Separate, buyer-approved streams | Partner processing, later molding |
| Film and bags | Only named streams with a buyer | Specialist film processor |
| ABS/ASA, nylon, composites | Excluded from the first internal process | Material-specific partner |
| Painted or glued products | Assess and segregate | Repair, disassembly, qualified recovery |
| Cured photopolymer resin | Excluded from thermoplastic recycling | Appropriate local waste route |
| Unknown mixtures, PVC, multilayer, heavily contaminated waste | No acceptance promise and no credit | Refer to suitable routes |

PET and PETG are different streams, so a bottle never goes into a PETG batch. Controlled industrial waste is a more defensible starting feedstock than arbitrary consumer plastic.

| Route | Strength | Main burden | Recommendation |
|---|---|---|---|
| Recycler partnership | Low equipment commitment | Transport, specs, evidence | **Start here** |
| Filament extrusion | Works with existing FDM | Drying, filtration, diameter, ovality, winding | Controlled later experiment |
| Pellet printing | No filament step | Feeding, drying, flow control | Separate capability to validate |
| Compression molding | Bases, panels, simple objects | Tooling, cooling, warp | Promising for specific products |
| Sheet pressing | Panels and cut parts | Thickness, flatness | Evaluate with a buyer/product |
| Injection molding | Repeat production | Tooling cost | When quantities justify it |

Recycled filament also has to compete on price: Numakers virgin PLA+ is ₹650/kg. In-house recycled filament has to beat that after drying, filtration, rejects and labour, or offer a verified claim customers will pay for.

**Hardware now:**

- drying and sealed storage;
- spare nozzles and plates;
- calipers and scales;
- finishing tools with extraction;
- QC reference samples;
- labelled trays;
- reliable networking;
- energy measurement;
- tested packaging.

**Collection pilot:**

- verified scale;
- tare containers;
- separate labelled storage;
- intake labels;
- pickup records.

**Recycling later:**

- a filament line needs shredding, screening, drying, blending, extrusion, filtration, cooling, pulling, winding, diameter measurement, ventilation and qualification equipment;
- don't buy a shredder just because the software ledger is ready.

Printing, resin handling, sanding, painting and shredding each need different exposure controls. NIOSH's small-business guidance is a technical reference, not an Indian permit.

## 7. Environmental accounting

Keep these quantities separate:

1. collected;
2. accepted;
3. stored;
4. shipped to a partner;
5. received by the partner;
6. processed;
7. qualified output;
8. incorporated into products;
9. estimated virgin material displaced.

A shipment is not proof of recycling, and recycled pellets in storage have not displaced anything yet.

```
opening inventory + inputs = qualified outputs + residues/losses + closing inventory
```

Record:

- gross and tare weights;
- accepted and rejected weights;
- moisture basis;
- polymer confidence;
- source and operator;
- scale and rate version;
- transfers;
- virgin additions;
- output lots;
- losses and unexplained discrepancies.

Never count the same internal scrap again as new collection.

A clearly illustrative example:

```
10.0 kg received ─┬─ 1.0 kg rejected
                  └─ 9.0 kg accepted ─┬─ 7.2 kg qualified output ─┬─ 5.5 kg in products
                                      ├─ 1.3 kg residues/losses   └─ 1.7 kg inventory
                                      └─ 0.5 kg work in progress
```

If those products also contain 2.5 kg of virgin polymer, recycled content is 5.5 / (5.5 + 2.5) = 68.75% of polymer mass. That is not 68.75% of total product mass if paint or metal is present.

```
Estimated displacement = verified recovered polymer incorporated × justified substitution factor
```

If the program causes extra products to be made, the displaced amount may be lower or unknown. Don't convert kilograms collected into avoided CO₂ without a lifecycle boundary, energy and transport data, losses and a defensible baseline.

Prefer rewards for repairs, replacement parts, educational use, durable functional objects and verified recovered content. Avoid "Zero-Waste Maker" badges; participation milestones are easier to defend.

## 8. Regulatory boundaries to verify

- **Credits (RBI).** Closed-system instruments usable only with the issuer are treated differently from broader payment instruments. At first, allow no cash-out, credit purchase, transfer or third-party settlement, and confirm the legal treatment.
- **GST.** Material received in exchange for credits may be non-cash consideration. A CA should classify it.
- **EPR.** Collecting material does not authorise us to generate EPR certificates. Verify processor registration, state consents, the material stream and contractual ownership of environmental attributes on the CPCB Common EPR portal.
- **Recycled claims.** Review the 2026 Plastic Waste Management amendment (G.S.R. 237(E)) and IS 14534:2023 before making recycled-product claims.
- **Advertising.** CCPA's greenwashing guidelines make broad, unsupported environmental claims a material risk. Use precise claims with accessible evidence.
