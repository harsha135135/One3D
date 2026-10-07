# One3D business plan

Status: draft · Last reviewed: 2026-09-30

> **Future business strategy — V2 and later.** Scope classification updated 2026-10-07; the financial assumptions retain their original review date. Current software work follows the bounded [hiring-focused V1](v1-scope.md), whose completion does not depend on real sales, more printers, inventory, recycling or this plan's full workflow. See [target architecture](target-architecture.md) for later increments.

Build One3D as a focused custom-manufacturing business under OneCreations. Use PrintFlow to run it. Introduce PrintBack through recycling partners once the core business works. The existing Bambu X1C is enough to validate the business and to build a substantial engineering project.

With ₹3–4 lakh covering the whole first year, plan around 2–4 productive printers at first. Treat 10–12 printers as an expansion paid for by demonstrated demand and retained earnings. Buy more machines only once there is evidence that printing is the bottleneck.

The strongest version of this project has four priorities:

1. Sell a narrow range of products that customers actually want.
2. Measure the labour and cost of delivering them.
3. Build software that makes that operation more reliable.
4. Recover materials through routes that can be verified.

The technical depth comes from making those four things work together.

## 1. Executive assessment

### What is strong

The valuable work happens between a customer's idea and an acceptable physical object. That includes:

- interpreting references;
- agreeing on expectations;
- preparing geometry;
- choosing a process;
- managing revisions;
- producing, finishing, inspecting and delivering the parts.

AI generation addresses only part of this.

As a software project, One3D has a real advantage: the actual equipment can be operated, failures observed, measurements collected, and the system improved from real production data.

### What needs changing

| Initial assumption | Problem | Better approach |
|---|---|---|
| Photo → AI → figurine is distinctive | The category already exists | Differentiate through a recognisable style, reliable likeness, finish, approvals and delivery |
| More printers create more capacity | Design and painting can dominate labour | Measure capacity at every production stage |
| ₹5,000 is a healthy custom-product price | A few hours of sculpting and finishing can consume the margin | Bound the customisation or charge more |
| Any customer's plastic can become a product | Polymers, contamination, additives, coatings and processing differ | Publish a narrow acceptance list tied to real recovery routes |
| Credits cost less than face value because products have margin | A discount reduces contribution on an order that would have happened anyway | Evaluate incremental purchases and subtract redemption once |
| A 20–25% redemption cap protects the business | It can exceed the entire contribution margin | Set eligibility and caps from product economics |
| Offline printer → retry elsewhere | The original machine may still be printing | Reconcile before reassigning |
| Using many AWS services makes the project impressive | It can hide weak engineering decisions | Demonstrate correctness, recovery, measurement and cost control |

Photo-based custom figurines, including painted ones, are already sold by Indian businesses such as Figurines India and NATUWA3D. That validates the category. It does not establish demand for One3D's particular product, price or acquisition channel.

### Known versus uncertain

| Status | Items |
|---|---|
| Known | One X1C (no AMS). OneCreations stays the separate parent portfolio. First-year funding is about ₹3–4 lakh. Filament comes from Numakers at the prices in [§7](#7-material-rate-card-numakers). |
| Planning assumptions | Existing workspace and computer. The founder does most of the work. Customer prices include tax unless stated. |
| Needs experiments | Willingness to pay, likeness acceptance, finishing time, failure rate, repeat purchases, credit redemption, recycler yields |
| Needs quotations | Additional printers, shipping, insurance, software, recycling transport and processing |
| Needs professional verification | GST classification, credits received for waste, product safety, privacy obligations, waste-processing permissions, environmental claims |

None of the financial figures below are actual operating results.

## 2. What the company is

**One3D by OneCreations:** custom collectibles and small production runs, with a clear approval process and documented manufacturing.

Start with:

- one recognisable style of personalised desk collectible;
- a small number of sizes and finishes;
- a reviewed upload-to-print service;
- selected repeat batches for businesses and creators.

The public promise describes the result:

> Custom collectibles, made from your references and finished by hand.

For the wider platform:

> From an approved design to a finished object.

"Make it real" can stay as a headline, but it needs a concrete explanation directly below it.

### What differentiates One3D

| Against | Differentiator |
|---|---|
| Print shops | Customers can start from an idea. Quotes cover design, preparation, finishing and delivery. Approved versions and manufacturing specs are kept. |
| Gift businesses | A recognisable product style, evidence of likeness and finish, repairable parts and repeatable production |
| Figurine businesses | Better execution, which has to be shown through samples, customer outcomes and reliability. An AWS backend is not a customer benefit in itself. |
| Recycling businesses | Useful products, educational programs and verified material records. Collection economics still have to stand on their own. |
| Print-management software | The link between customer approval, design revision, parts, production, finishing, cost and material history. Printer monitoring alone is already well served: Bambu Farm Manager offers local monitoring, queuing and batch control, including for the X1C. |

## 3. Budget and expansion

Spending ₹3–4 lakh entirely on 9–11 more printers would allow about ₹27,000–44,000 per printer. That would leave nothing for materials, finishing, packaging, working capital or infrastructure.

| Use | ₹3 lakh plan | ₹4 lakh plan |
|---|---|---|
| Additional printers, bought in stages | ₹1,20,000 | ₹1,70,000 |
| Working capital: filament, shipping, replacements | ₹60,000 | ₹80,000 |
| Farm infrastructure, electrical, storage, controller | ₹35,000 | ₹45,000 |
| Finishing, QC, photography and packaging equipment | ₹30,000 | ₹40,000 |
| Cloud, software and AI experiments | ₹15,000 | ₹20,000 |
| Compliance advice and customer validation | ₹10,000 | ₹15,000 |
| Contingency | ₹30,000 | ₹30,000 |
| **Total** | **₹3,00,000** | **₹4,00,000** |

These are spending limits, not supplier quotes. They assume the premises and computer already exist. If rent, deposits or a salary have to come out of this money, reduce the equipment line.

Filament is a small share of working capital. At Numakers' PLA+ price, ₹6,500 buys 10 kg, which is enough polymer for roughly 33 custom orders at 300 g each. Shipping, packaging, paint and replacement parts will use more of the working-capital line than filament does.

### Buying sequence

1. Use the X1C to establish the product and a measured baseline.
2. Add one machine when a measured printing bottleneck appears.
3. Prefer a small number of repeatable printer configurations.
4. Buy a different or flagship machine only for a specific capability that generates revenue.
5. Expand again once finishing, sales and working capital can absorb the extra output.

A mix of different flagship printers means more profiles, spare parts, maintenance procedures and integration work.

### Printer purchase gate

Before buying another printer, all of these must hold:

- several weeks of paid demand above qualified available capacity;
- positive contribution after valuing founder labour;
- acceptable quality and delivery performance;
- evidence that printing, not sculpting or finishing, is limiting output;
- enough cash to fund the new printer's materials and work in progress;
- a payback calculation based on extra products that can actually be sold and finished.

Ten to twelve printers should be a consequence of demand. The printer count is not the main success metric.

## 4. Customer segments and revenue

| Priority | Segment | Why it is promising | Main difficulty |
|---|---|---|---|
| 1 | Personalised stylised desk collectibles | Clear product, visual marketing, bounded options | Likeness and finishing time |
| 2 | Small corporate or creator batches | Shared design cost, possible repeat orders | Sales effort, samples, deadlines |
| 3 | Pet collectibles | Strong emotional value | Fur, markings, anatomy, expectations |
| 4 | Makers and startups with existing models | Useful operational baseline, less artistic work | Price pressure, technical requirements |
| 5 | Wedding and couple commissions | High emotional value | Deadline risk, clothing detail, revisions |
| 6 | Schools and colleges | Educational products and workshops fit well | Budgets, procurement, product safety |
| 7 | Apartment recycling programs | Aggregation improves collection economics | Coordination, storage, weak immediate demand |
| 8 | Open public cleanup rewards | Community value | Fraud, logistics, safety, uncertain economics |

Complex wedding commissions should not be the first production workflow. A missed wedding deadline costs far more than a late desk collectible.

### Revenue streams

- **Initially:**
  - personalised products;
  - design/setup fees;
  - extra revisions outside the agreed scope;
  - premium finishing;
  - reviewed printing of customer models;
  - B2B samples and batches;
  - replacement parts and repairs.
- **After validation:**
  - creator drops with licensed designs;
  - repeat manufacturing from approved profiles;
  - workshops;
  - paid collection programs for organisations;
  - sponsored educational products;
  - "Make from your material" commissions.
- **Later:**
  - PrintFlow subscriptions;
  - enterprise support;
  - qualified partner manufacturing;
  - distributed production.

EPR certificates, sponsorships, subscriptions and marketplace commissions stay out of the base forecast until there is an actual agreement or a paying customer.

## 5. First product: constrain the work before automating it

A stylised bust or desk collectible with:

- one visual style;
- two sizes;
- a small set of poses or body templates;
- one standard base;
- a limited accessory menu;
- a defined paint finish;
- explicit revision limits;
- a visible delivery window.

Offer customisation where customers notice it most: face, hairstyle, clothing cues, pet markings, nameplate and a few meaningful accessories. Don't treat every order as a new sculpting project.

| Tier | Viable interpretation | What breaks the economics |
|---|---|---|
| ₹1,500–3,000 | Small template-based product, restrained finish, limited revisions | Bespoke anatomy, clothing and pose |
| ₹3,500–7,000 | Bounded personalisation with measured labour | Unlimited likeness revisions or elaborate hand painting |
| ₹8,000–20,000+ | Quoted commission with paid design work and premium finish | Collector-level effort at the bottom of the range |

The product specification sets the price. Size alone is a poor basis for pricing.

### FDM, resin and no AMS

The X1C suits stylised products, larger forms, bases and functional parts. For small realistic faces, jewellery, fingers or delicate details, compare an outsourced resin sample against the FDM result. Resin gives finer detail but still needs washing, curing, cleanup and careful handling.

Having no AMS is manageable for a painted product. Splitting a model does have costs, though:

- it creates assembly work;
- more joints add fit and transit risk;
- painting remains skilled labour;
- fine layers raise machine time;
- a good render can hide poor printable geometry.

A three-part design that finishes reliably beats an eight-part design spread across a farm.

## 6. What AI should do

Use AI to reduce specific work, then measure whether it did.

| Step | Initial approach | Automation potential |
|---|---|---|
| Reference intake | Guided photos and clear requirements | Detect missing views, blur, bad crops |
| Concept development | AI-assisted, human-selected | High for variations, lower for judging likeness |
| Base mesh generation | Provider or manual sculpt | Useful start, variable cleanup |
| Geometry validation | Deterministic checks | High |
| Face, clothing, anatomy correction | Human review | Partial |
| Splitting and connectors | Operator decisions plus reusable tools | Good for standardised products |
| Orientation and supports | Validated profiles plus operator review | Partial |
| Slicing | Pinned software, controlled presets | High for qualified inputs |
| Defect detection | Operator first | Later, as an alerting aid |
| Final QC | Human acceptance | Assistance, not autonomous approval |

A successful response from an image-to-3D API doesn't prove likeness, manufacturability or commercial suitability. For every provider call, record:

- provider and model version;
- input hashes;
- prompt-template version;
- task ID;
- cost and elapsed time;
- output version;
- human acceptance;
- repair minutes.

On a timeout, query the existing provider task before paying for another generation.

Audja and similar prep tools are interchangeable desktop tools. No supported unattended API has been verified. Keep this chain in neutral formats, so it survives any vendor change:

```
original asset → prepared geometry → approved revision → individual parts → slicer project + profile versions → approved machine artifact
```

## 7. Material rate card (Numakers)

This is the first material rate-card version, as of 2026-09-30:

| Filament | ₹/kg spool | ₹/g |
|---|---|---|
| PLA+ | 650 | 0.65 |
| PLA Matte | 700 | 0.70 |
| Specials (silk, etc.) | 900–1,000 | 0.90–1.00 |
| PETG | 650 | 0.65 |

- These are purchase prices, treated as GST-inclusive, and no input tax credit is assumed. If One3D registers for GST and can claim input credit, PLA+ costs about ₹551/kg net. Confirm with a CA.
- PETG costs the same as PLA+ but needs drying and dry storage. Keep PETG scrap separate from PLA, and never mix it with PET bottle stock. See [PrintBack](printback.md#6-material-streams-and-routes).
- Future material accounting would store versioned paise-per-kg rates on material lots so an order's recorded cost uses the applicable rate. This is unimplemented and outside V1; see [target architecture](target-architecture.md).

## 8. Unit economics (illustrative)

Real contribution is unknown until real orders are timed. This model makes the assumptions visible.

**Assumptions:**

- Customer prices include an illustrative 18% GST. That is not a claim about the classification of every supply.
- Polymer is priced from the Numakers PLA+ price (₹0.65/g). The B2B mascots use PLA Matte (₹0.70/g).
- Material per order is 120 g, 300 g and 600 g, including normal supports.
- Machine time is 8, 20 and 40 hours.
- Machine depreciation and maintenance is ₹10/hour. That represents an illustrative production machine, not a valuation of the X1C. Replace it with (replacement cost − residual value) / productive lifetime hours + maintenance per productive hour.
- Electricity is 0.2 kW × ₹10/kWh = ₹2/hour.
- Design labour is ₹300/hour. Finishing, assembly, QC and packing labour is ₹200/hour. Founder labour is charged even when the founder does the work.
- Payment fees are 2%, which is Razorpay's standard rate plus GST.

The B2B example is 100 identical, simply finished 100 g mascots at ₹1,500 each, including shared design work. It is not 100 unique personalised figures.

| Item | ₹2,000 template | ₹5,000 custom | ₹10,000 premium | B2B: 100 units |
|---|---|---|---|---|
| Customer payment | 2,000 | 5,000 | 10,000 | 1,50,000 |
| Revenue excl. assumed GST | 1,695 | 4,237 | 8,475 | 1,27,119 |
| Polymer (Numakers) | 78 | 195 | 390 | 7,000 |
| Machine depreciation and maintenance | 80 | 200 | 400 | 6,000 |
| Electricity | 16 | 40 | 80 | 1,200 |
| Failed attempts and rework | 40 | 100 | 200 | 2,000 |
| Design labour | 150 | 900 | 1,800 | 12,000 |
| Finishing, assembly, QC, packing labour | 200 | 900 | 1,800 | 12,000 |
| Paint and finishing consumables | 60 | 150 | 300 | 3,000 |
| Packaging | 110 | 200 | 350 | 8,000 |
| Shipping | 100 | 150 | 200 | 3,000 |
| Payment fees | 40 | 100 | 200 | 3,000 |
| AI, software, cloud (variable) | 30 | 75 | 150 | 1,500 |
| Support | 70 | 125 | 200 | 2,500 |
| Post-dispatch replacement reserve | 60 | 150 | 300 | 3,000 |
| **Operating cost** | **1,034** | **3,285** | **6,370** | **64,200** |
| Contribution before acquisition and credits | 661 | 952 | 2,105 | 62,919 |
| Acquisition cost | 250 | 450 | 800 | 12,000 |
| Expected credit/program allowance | 40 | 100 | 200 | 0 |
| **Remaining contribution** | **₹371** | **₹402** | **₹1,105** | **₹50,919** |
| As % of net sales | 21.9% | 9.5% | 13.0% | 40.1% |

- The credit/program allowance is an expected cost, counted once.
- Fixed subscriptions, rent, general administration, insurance and income tax are excluded.
- The only change from the earlier ₹1,000/kg placeholder model is the polymer line. It was 120, 300, 600 and 10,000.

### What this shows

- **Filament is not the problem.** On the ₹5,000 custom order, polymer is about 4.6% of net sales. Printing it in a ₹1,000/kg special filament adds only about ₹105.
- **Labour decides the margin.** The custom order assumes 3 hours of design and 4.5 hours of finishing, assembly, QC and packing. Another 90 minutes of design costs ₹450 and turns the ₹402 remaining contribution into **−₹48**.
- **Price needed for a 25% contribution,** holding every other assumption constant: about **₹6,070** for the custom product and **₹11,650** for the premium one, including assumed GST. These are model outputs, not proof that customers will pay those prices.

The practical responses are:

- raise the price;
- reduce the scope;
- use templates;
- reduce revisions;
- change the manufacturing process;
- improve finishing methods;
- sell a different product.

Buying another printer does not fix this margin.

### Why batches work better

The B2B model benefits from shared design and a simple repeated finish. If every piece needs a different face or hours of painting, that advantage disappears. Quote batches as:

```
design/setup + sample + quantity × unit price + packaging + delivery + applicable tax
```

State what a repeat order reuses, and which changes trigger a new setup fee.

## 9. Revenue is not profit, and printer count is not capacity

At a ₹4,500 average customer payment:

| Orders/month | Annual collections | Net sales (price includes 18% GST) |
|---|---|---|
| 30 | ₹16.2 lakh | ₹13.73 lakh |
| 100 | ₹54 lakh | ₹45.76 lakh |
| 200 | ₹1.08 crore | ₹91.53 lakh |
| 500 | ₹2.70 crore | ₹2.29 crore |

About 219 orders a month gives ₹1 crore of annual net sales under this tax assumption, before refunds and discounts.

### Capacity has several constraints

```
Deliverable orders = min(printer capacity, design capacity, finishing capacity,
                         QC and packing capacity, demand, working capital)
```

The printing-only scenario below assumes:

- 16 scheduled hours a day;
- 26 days a month;
- 70% effective availability;
- 20 machine-hours per order;
- 15% extra time for failed attempts.

| Printers | Orders/month from printing alone |
|---|---|
| 1 (today's X1C) | 13 |
| 4 | 51 |
| 12 | 152 |

Labour is the tighter limit. At 7.5 direct hours per bespoke order, 20 orders need 150 hours and 100 orders need 750 hours. A person with 120 productive hours a month can handle about 16 such orders, before support and management.

### Break-even

```
Break-even orders = monthly fixed operating cost / remaining contribution per order
```

At an illustrative ₹20,000 a month in fixed costs, this needs about 54 template orders, 50 custom orders or 19 premium orders. This is algebra, not a staffing plan. If reaching that volume needs more people or premises, the fixed costs change.

### North-star metric

> Contribution earned per constrained human hour, while meeting quality and delivery commitments.

Track human minutes per order alongside it.
