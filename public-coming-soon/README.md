# Public Coming Soon website

**Prepared locally; not published.** The production destination is `https://one3d.store/`. This is a static public brand page, separate from the private recruiter application at `demo.one3d.store`. Publishing, DNS changes and purchases require the owner's approval.

## What is included

- Buildless HTML and CSS in `dist/`, with no JavaScript, cookies, trackers, forms or backend requests.
- Vantablack colours from the established One3D direction. A static, original layer illustration; no fictitious product photograph, customer or delivery claim.
- The owner's existing One3D mark, copied from `packages/oc-ui/assets/brand/voxel/marks/3d.svg` in the separate OneCreations repository. CSS gives it a monochrome treatment; the favicon uses the same main silhouette.
- Explicit Coming Soon / orders-not-open copy, future service descriptions and links to OneCreations. There is no link to the recruiter application.
- Canonical URL, description, Open Graph/Twitter metadata, 1200×630 social PNG, favicon, sitemap and robots file.
- A 404 page, Netlify-compatible security headers and a `www` → apex redirect. Header/redirect behavior requires host verification; Python's preview server does not apply those files.

Only **`dist/`** is the publish directory. Do not upload `review/`, `checks/`, this README, the application repository, environment files or release evidence to public hosting. No part of this page is served by EC2.

## Local preview and checks

From the One3D repository root:

```sh
python3 -m http.server 4179 --bind 127.0.0.1 --directory public-coming-soon/dist
```

Open `http://127.0.0.1:4179/`. In another terminal, using the already installed frontend Playwright dependency:

```sh
node public-coming-soon/checks/browser.cjs
python3 public-coming-soon/checks/static.py
```

The browser check also regenerates `dist/assets/social.png` from the authored `social.svg`, and writes mobile/desktop screenshots and `review/browser-results.json`. The artwork's reproducible source is `checks/generate-art.py`; run it before the browser check if changing the illustration.

The recorded local checks cover Chromium at 320, 390, 780, 1024 and 1440 pixels; no horizontal overflow or missing images; keyboard skip navigation; 44-pixel link targets; reduced-motion behavior; no external page requests; and expected assets returning HTTP 200. These checks do not establish full WCAG conformance, screen-reader compatibility, field Core Web Vitals or successful public hosting.

## Hosting choice

**Proposed: Netlify Free, keeping Hostinger authoritative DNS.** This is the smallest DNS change for the independently hosted page. Its current Free plan includes custom domains/SSL and a hard monthly credit limit. It can be used for a commercial brand page, but availability is subject to the plan's limits; a free plan is not an uptime guarantee. No account or site has been provisioned, so an assigned `*.netlify.app` hostname is not available yet. See [Netlify pricing](https://www.netlify.com/pricing/) and [external DNS](https://docs.netlify.com/manage/domains/configure-domains/configure-external-dns/).

**Alternative: Cloudflare Pages**, especially if the owner separately approves moving the zone to Cloudflare for Access. An apex Pages domain requires Cloudflare authoritative nameservers; registering the domain at Hostinger does not prevent that. This expands the cutover to every DNS record and needs a complete zone export, DNSSEC review and explicit approval. A plain Hostinger apex CNAME is not a workaround for Pages' zone requirement. Adapt `_redirects` to the selected platform and verify its semantics before publication. See [Pages custom domains](https://developers.cloudflare.com/pages/configuration/custom-domains/) and [Free-plan limits](https://developers.cloudflare.com/pages/platform/limits/).

GitHub Pages is not the default recommendation for the future commercial site because its published terms restrict hosting online businesses and e-commerce. See [GitHub Pages terms](https://docs.github.com/en/site-policy/github-terms/github-terms-for-additional-products-and-features#pages). No additional AWS compute, S3 bucket or paid hosting plan is needed for this static page.

## DNS: observed state and proposed change

Read-only lookup during the release audit returned:

| Name | Type | Observed answer | Treatment |
|---|---|---|---|
| `one3d.store` | NS | `aster.dns-parking.com`, `helios.dns-parking.com` | Keep for the Netlify option |
| `one3d.store` | A | `2.57.91.91` | Replace only after target hosting is verified and approved |
| `www.one3d.store` | CNAME | `one3d.store` | Replace with the assigned hosting target when configured |
| `demo.one3d.store` | A | `13.234.148.122` | Do not change as part of this public-site cutover |
| Apex | AAAA / CAA | No answers returned | Recheck; this is not a full zone export |

The A/CNAME answers were also queried directly from `aster.dns-parking.com`. DNS is mutable. Re-run the following and export the complete Hostinger zone before any change:

```sh
dig +short NS one3d.store
dig @aster.dns-parking.com +noall +answer one3d.store A
dig @aster.dns-parking.com +noall +answer one3d.store AAAA
dig @aster.dns-parking.com +noall +answer one3d.store CAA
dig @aster.dns-parking.com +noall +answer www.one3d.store CNAME
dig @aster.dns-parking.com +noall +answer demo.one3d.store A
```

Do not infer the original Hostinger record type solely from an A response: an ALIAS can be flattened into an A answer. Inspect the dashboard to identify whether the current apex is an A record, ALIAS or hosting/CDN-managed record.

After approval to provision and publish:

1. Create the static hosting project, using only `public-coming-soon/dist`. Use no build command or application environment variables. Confirm the generated hosting URL serves this exact page and assets.
2. Add `one3d.store` and `www.one3d.store` as custom domains; choose `one3d.store` as primary. Record the actual assigned hostname and the provider's domain-verification instructions. Do not guess the hostname.
3. In Hostinger **Domains → DNS → one3d.store**, export the zone. Confirm these are still the authoritative nameservers. Inspect apex, `www`, existing CAA and all mail/verification records.
4. The proposed Netlify records below are conditional on its standard hosting network and must match the project's domain setup screen. Present the completed record set for the owner's explicit DNS approval.

| Type | Host | Proposed value |
|---|---|---|
| ALIAS, entered as apex CNAME in Hostinger | `@` | `apex-loadbalancer.netlify.com` |
| CNAME | `www` | The exact assigned `<site-name>.netlify.app` hostname — **pending provisioning** |

Hostinger documents apex ALIAS support through its CNAME editor. If unavailable for the actual account, Netlify's documented standard-network fallback is `A @ 75.2.60.5`; use **one** apex approach. Replace conflicting apex/`www` records only. Preserve `demo`, MX, TXT, mail records and unrelated verification entries. See [Hostinger ALIAS guidance](https://www.hostinger.com/support/10067986-how-to-manage-alias-records-at-hostinger/) and [zone export/edit guidance](https://www.hostinger.com/support/1583249-how-to-manage-dns-records-at-hostinger/).

5. Once approved records propagate, confirm the provider's automatically issued HTTPS certificate covers apex and `www`. Existing restrictive CAA records, if found, need review against the selected certificate issuer. See [Netlify HTTPS](https://docs.netlify.com/manage/domains/secure-domains-with-https/https-ssl/).
6. Check apex HTTP→HTTPS, `www`→apex, security headers, real 404 status, robots/sitemap and social-image loading. Confirm no page request reaches the demo/API. Verify it still works while EC2 is stopped during an already-authorized stop window; do not stop EC2 solely as an unapproved test.
7. Only then deploy the separate OneCreations link update. Record the static deployment ID and content commit independently from the recruiter release.

The complete final DNS target cannot be provided until a host has assigned it. Nothing in this README is a claim that DNS or HTTPS has been configured.

### Public-site rollback

Before cutover, save the actual Hostinger record types, values and TTLs, not only `dig` answers. Revert this site's hosting deployment to the recorded previous version, or restore only the changed apex/`www` records from that snapshot. Do not restore the entire zone over intervening demo/mail changes. Revert the OneCreations patch if the public URL becomes unavailable. Do not delete a hosting project while DNS still points to it. The private demo remains a separate access-control change.

## Separate OneCreations change

The OneCreations update is prepared in `/private/tmp/onecreations-coming-soon`, branch
`codex/one3d-coming-soon`, based on `ui/studio-themes` at
`9d3a2095f3128ca05534a3aabb5d7f7077b2e5c8`. The original checkout and its uncommitted
changes remain untouched. It will be reviewed through a separate PR.

Setting `NEXT_PUBLIC_ONE3D_URL` alone was insufficient: the old code equated a URL
with an open shop. The update fixes handoffs to `https://one3d.store`, changes related
labels to Coming Soon, preserves item/ref query parameters and removes the obsolete
URL override so it cannot point to the private demo. It touches only One3D integration
and its configuration/deployment notes.

Typecheck, lint and 172 existing tests passed. The production build passed using
webpack after the default Turbopack worker hit an environment permission error.
Publishing OneCreations remains a separate approval step after the public apex works.
Verify header, mobile navigation, footer, About and printed-work links at deployment.
