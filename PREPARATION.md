# SDUI Booking Ticket — Preparation Guide

> For Product, Merchant Ops, and Business teams. No coding needed — plain language summary of what we built, what merchants can do, and what guardrails we need before letting merchants build their own booking pages.

---

## 1. What We Built — In Plain Language

* **SDUI = Server-Driven UI:** The app's pages (seat selection, checkout, payment, confirmation) are not hard-coded. They are **JSON blueprints** that the server sends to the app. Changing the JSON changes the page instantly — no app update needed.
* **Two separate workspaces:**
  * **SDUI workspace** — the original 5 booking pages (`seat selection → checkout → checkout success → payment → confirmation`). Stable, used by the live app.
  * **PuckMUI workspace** — a new, isolated playground where merchants can drag-and-drop to build their own pages. It has its own storage and its own web addresses, so merchant experiments never break the live booking flow.
* **Two builders:**
  * **JSON Builder** — for developers: edit raw JSON.
  * **Puck Builder (drag-and-drop)** — for merchants: pick blocks like `Grid`, `Heading`, `Image`, `Button` from a palette and drop them on a canvas. What you see is what the customer will see.
* **Stress test:** We created a page with **999 mixed blocks** (images, headings, buttons, etc.) to see how much the system can handle. `100` blocks is smooth; `999` is heavy and needs limits.

**Goal:** Let every merchant (e.g., `merchant_1`) build their own pages and link them together (e.g., `Seat → Checkout → Payment`), while we keep control so they can't break the booking journey.

---

## 2. What a Merchant Can Do (Dynamics)

* **Build pages:** Create a new page (e.g., `Summer Concert`) and add blocks: `Heading` (title), `Text` (description), `Image` (poster), `Grid` (to arrange seats in columns), `Button` ("Buy Now").
* **Arrange layout:** Use `Grid` (columns) or `Flex` (row/column) to make the page look good on phone or desktop. Change background, padding, text color with simple dropdowns — no code.
* **Link pages (navigation):** Tell a `Button` "when clicked, go to `payment`" or a `Form` "when submitted, go to `confirmation`". The app will then move the customer to that next page.
* **Example merchant flow:**
  ```
  [My Seat Selection] --taps seat--> [My Checkout] --submits form--> [My Payment] --pays--> [My Confirmation]
  ```

---

## 3. What Can Go Wrong (Risks) — Booking Ticket Example

| Risk | Simple Example | What Happens Without Guardrails |
|---|---|---|
| **Button tries to call an API we don't have** | Merchant adds a `Button` with `Submit` that calls `https://evil.com/steal` or `DELETE_TICKET` | App shows error, or worse, tries to call a private admin API or leaks data. Our current demo has 62 buttons with free-form `actions` — any URL is possible. |
| **Navigation points nowhere** | Button says `go to "unknown_page"` or to a page that is still `Draft` (not published) | Customer taps and sees `404 Page not found` or `Not published`, or gets stuck in a loop `checkout → checkout`. |
| **Too many blocks** | Merchant adds `1000 images` (like our `999` test, `270KB`) or `Grid` with `100 columns` | Page becomes `270KB` (slow to load), app freezes, phone runs out of memory, layout breaks. |
| **One merchant sees another's pages** | `merchant_1` lists `merchant_2`'s pages | Privacy breach. |

---

## 4. Boundaries We Need — Let Merchants Be Creative, But Safe

We **want** merchants to be dynamic (change text, colors, order of blocks), but we **must** set clear boundaries:

| Boundary | In Plain Language | How We Will Enforce It |
|---|---|---|
| **Allowed Blocks Only** | Merchants can only use the 10 approved blocks: `Grid, Flex, Space, Heading, Text, Button, Card, Hero, Divider, Image` + 6 booking blocks (`SeatGrid, OrderSummary, Form...`). No `Video` or `Script` that could run harmful code. | When merchant saves, the server checks the page's block list against an `allowlist`. If it contains `Video`, the save is rejected with a clear message. |
| **Allowed Actions & APIs Only** | A `Button` can only call the 3 approved actions: `SEAT_SELECTION` (reserve seat), `CHECKOUT` (submit email/name), `PAYMENT` (pay). And it can only call our two approved web addresses: `/api/client/action/submit` or `/api/puckmui/client/action/submit`. No `https://evil.com`. | When merchant saves a page, the server checks every `Button`/`Form` `action` and `endpoint`. If it says `DELETE_TICKET` or `https://evil.com`, the save is blocked. The app's `CORS` setting also blocks outside addresses. |
| **Navigation Must Exist & Be Published** | A `Button` that says `go to "payment"` must point to a real page that the same merchant created **and** that is `Published` (not `Draft`). No `../admin` tricks, no empty links. | When merchant clicks `Publish`, the server collects all `go to` links and checks: “Does `payment` exist for `merchant_1` and is it `Published`?” If not, `Publish` fails and tells them which link is broken. The app's preview already warns if you try to go to a missing page. |
| **Size Limits** | No more than `200` blocks per page, page JSON smaller than `500KB`, `Image` must be from `https://picsum.photos` or the merchant's own image storage, `Grid` max `12` columns. Our test `100` blocks (`28KB`) is OK; `999` (`270KB`) needs pagination. | Server checks `number of blocks` and `JSON size` before saving. If a merchant tries `1000 images`, the server returns `Too many blocks (max 200)`. |
| **Merchant Isolation** | `merchant_1` can only see/edit `merchant_1`'s pages, never `merchant_2`'s. Each merchant's pages live in a separate folder (`data/puckmui/pages/merchant_1/`). | Future login will check `merchantId` in the web address matches the logged-in merchant. Already isolated at storage level. |
| **Publish Gate** | Saving is always `Draft` first. Only an explicit `Publish` button makes it visible to customers. You can't publish directly and you can't publish if navigation is broken. | `Draft` vs `Published` status. `Publish` runs all the checks above + style checks. |
| **Safe Preview** | What merchant sees while editing is a safe preview with fake data, not a real payment. | Preview calls the same APIs but in `mock` mode (returns `success` without charging). |

**Workshop needed:** Backend + Frontend + Product agree on the two allowlists (`blocks` and `actions/endpoints`) and the `500KB`/`200` limits, and write them down in `components.json` so both sides use the same list.

---

## 5. Database — Can We Use Oracle? Yes.

We currently store pages as **JSON files** on disk (`data/pages` and `data/puckmui/pages`). For production with many merchants, we need a real database.

**Our recommendation remains `Postgres` with `JSONB`** — it's cheap, fast for JSON, and supports `pageKey + merchantId` uniqueness and `Published` filtering. But **Oracle is absolutely possible** if your company (BNI) already uses Oracle:

* **Oracle 23c (newest):** Has a native `JSON` column type (`CHECK (layout IS JSON)`), plus `JSON Duality Views` (you see the data as both tables and JSON), and fast `SEARCH INDEX` to find pages by block type. `270KB` `mixed_999` fits easily. You would create: `CREATE TABLE page (page_id PK, page_key, merchant_id, status, version, updated_at, layout JSON)`.
* **Oracle 21c:** Same `JSON` type, `JSON_TABLE` to query.
* **Oracle 19c (older):** Stores JSON as `CLOB` with `CHECK (layout IS JSON)` — works but `4000` character limit means `mixed_999` must be `CLOB`, a bit slower.

**Trade-off:**
* `Postgres JSONB` `GIN` index is fastest for searching `layout->'components'`.
* `Oracle` costs more for licenses and needs `CLOB` tuning for `270KB`, but gives you `RAC/Data Guard` and fits an existing Oracle landscape.
* `MySQL JSON` is similar to Postgres but less powerful.
* `Mongo` is schemaless and shards well for `10,000` merchants, but you lose `JOIN` and strong `ACID` for `version` increments.

**For local development** we keep files on disk (`file + classpath fallback`) so you can run without any database.

---

## 6. How the JSON Blueprint Works — Simple View

Every page is one JSON file:

```json
{
  "pageId": "merchant_1-mixed_100",
  "pageKey": "mixed_100",
  "merchantId": "merchant_1",
  "status": "PUBLISHED",
  "layout": {
    "title": "My Page",
    "components": [
      { "type": "Heading", "id": "h1", "text": "Welcome", "size": "m", "align": "center" },
      { "type": "Button", "id": "b1", "label": "Go to Payment", "actions": { "onClick": {"type":"navigate", "target":"payment"} } }
    ]
  }
}
```

* `type` must be from the allowlist.
* `id` is auto-generated.
* `style` and `advanced` are the same for every block (background, padding, etc.) — this is why the right-side edit panel looks identical for all blocks (per Puck's design).

We validate this with `JSON Schema` before saving.

---

## 7. What Happens When a Customer Taps

1. **SeatGrid** `onSelect` → app calls `POST /api/puckmui/client/action/submit` `{"action":"SEAT_SELECTION","data":{"seatId":"sec-a"}}` → server replies `Seat reserved` → app `navigateTo("checkout")`.
2. **Form** `onSubmit` → `POST ... {"action":"CHECKOUT","data":{"email":"a@b.com","fullName":"Ada"}}` → server checks `email+fullName` required → `Checkout submitted` → `navigateTo("checkout_success")` + shows `{{fullName}}` templating.
3. **PaymentForm** → `POST ... {"action":"PAYMENT","data":{"amount":129.5,"method":"CARD"}}` → `Payment processed` → `navigateTo("confirmation")`.

If merchant set an allowlisted `target` that doesn't exist, the app shows `Published page not found` instead of crashing.

---

## 8. Performance — What 100 vs 999 Taught Us

* `100 mixed` (`28KB`) → drag-and-drop `10ms`, `Puck` `10` components per type `Counter 6-7 each`, smooth.
* `999 mixed` (`270KB`) → `O(n)` string building + `100` image loads + `Puck` `slot` recursion `Grid/Flex` `items` — browser scroll heavy, needs `gzip` and pagination (`Grid` `items` as chunks) or virtualized list. Recommend `≤200` per page.

---

## 9. How to Try It

```powershell
.\gradlew bootRun
# SDUI live pages
http://localhost:8080/api/merchant/pages/merchant_1           # 5 pages
# PuckMUI merchant playground
http://localhost:8080/api/puckmui/merchant/pages/merchant_1    # 4 pages: gallery_100_images (102), gallery_small (8), mixed_100 (100), mixed_999 (999)
http://localhost:8080/puck-builder.html  # Load → Page dropdown → mixed_999 → Puck data: 999
http://localhost:8080/puck-test.html      # minimal Heading+Text test (React 18.2)
```

---

## 10. Roadmap (Next Steps)

* Move from files to `Postgres JSONB` (or `Oracle JSON` if required) keeping file fallback.
* Add login so `merchant_1` cannot edit `merchant_2`.
* Add `JSON Schema` validation on save for both `SDUI` and `PuckMUI`.
* Keep `Draft → Published` gate with navigation/style checks.
* Add real-time preview push (`WebSocket`) on `Publish`.
* Add automated tests for `999` render.

---

## Technical Appendix (For Developers)

* **Dual stores:** `service/JsonFileUtils.java:20` `data/pages` `sdui.pages.dir` `JsonFileUtils.java:26` vs `service/PuckMuiJsonFileUtils.java:14` `data/puckmui/pages` `sdui.puckmui.dir` `PuckMuiJsonFileUtils.java:14` — `PuckMuiPageConfigService.java:37` fresh read avoids stale `gallery_small↔100` swap.
* **Puck config:** `puck-builder.html:391` `categories: {layout:[Grid,Flex,Space], typography:[Heading,Text], actions:[Button], content:[Card,Hero,Divider,Image]}` from `https://github.com/puckeditor/puck` `apps/demo/config/blocks/*`, `puck-builder.html:342` `styleObjectField` + `advancedField`, `baseStrictFields` `puck-builder.html:381` `title/text, description/textarea, image/text, count/number, variant/select` + `validatePuckFields` `puck-builder.html:399` `PUCK_CORE_FIELD_TYPES` + `UniformWrapper` `puck-builder.html:389`.
* **Puck data sync:** `layoutToPuck`/`puckToLayout` `puck-builder.html:81` `SLOT_KEYS=["items","content"]` `puckSlotToSdui`/`sduiToPuckSlot` `puck-builder.html:89`, `key={pageKey}` `puck-builder.html:1046` + `setPageKey` after `fetch` `puck-builder.html:914` fixes off-by-one, `MenuItem key={k} value={k}` `puck-builder.html:1017`.
* **Styling:** `tailwind.in.css:9` `CSS vars --bg --panel --accent`, `Puck` overrides `puck-builder.html:37`, `puck.css` `https://esm.sh/@measured/puck@0.20.2/puck.css` `puck-builder.html:10` + `unpkg` fallback (removed `500` `@puckeditor/core`).
* **Prompt:** `PUCK_UNIFIED_PROMPT.md:1` strict `baseStrictFields` + `UniformWrapper` + `validatePuckFields` + `fallback`.

*Generated from `C:\sdui\demo` exploration — `DOCUMENTATION.md:1` has full developer details, `PREPARATION.md` is the business-friendly version.*
