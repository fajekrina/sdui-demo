# SDUI Demo — How to Run

> Dual SDUI (`data/pages`) + PuckMUI (`data/puckmui/pages`) booking demo. Spring Boot 4 + Gradle + Tailwind + Puck `0.20.2` + MUI 5. No database — JSON files.

---

## Prerequisites

* **Java 17+** (`build.gradle:11` `JavaLanguageVersion.of(17)`) — `java -version`
* **Node 18+** + `npm` — `node -v; npm -v` (only for Tailwind `static/tailwind.css:1`)
* **PowerShell 5.1** (Windows) — `gradlew.bat` included

---

## 1. Install & Build CSS

```powershell
npm install
npm run build:css      # one-off: tailwind.in.css:1 → tailwind.css (gitignored)
# or watch mode:
npm run watch:css      # rebuilds on src/main/resources/static/index.html + app.js + data/pages/**/*.json
```

`tailwind.css` must exist before first run — `index.html:7` loads it, `.gitignore:41` ignores generated file.

---

## 2. Run Backend

```powershell
.\gradlew bootRun
# → http://localhost:8080  (application.properties:1 spring.application.name=demo, default port 8080)
# or build jar:
.\gradlew build
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
# with external data dirs:
java -Dsdui.pages.dir=C:/data/pages -Dsdui.puckmui.dir=C:/data/puckmui/pages -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

* SDUI store: `service/JsonFileUtils.java:20` `data/pages` `sdui.pages.dir` `JsonFileUtils.java:26`
* PuckMUI store: `service/PuckMuiJsonFileUtils.java:14` `data/puckmui/pages` `sdui.puckmui.dir` `PuckMuiJsonFileUtils.java:14` (isolated, `PuckMuiPageConfigService.java:37` fresh read)

---

## 3. Open Builders

| Builder | URL | Talks to |
|---|---|---|
| **JSON Builder (SDUI)** | http://localhost:8080/index.html | `GET /api/merchant/pages/{mid}` `MerchantController.java:24` + `GET /api/client/page/{mid}/{key}` `ClientController.java:24` |
| **Puck Builder (PuckMUI-only)** | http://localhost:8080/puck-builder.html | `apiBase="/api/puckmui"` `puck-builder.html:862` → `GET /api/puckmui/merchant/pages/{mid}` `PuckMuiMerchantController.java:16` |
| **Puck Minimal Test** | http://localhost:8080/puck-test.html | `React 18.2` `2` comps `Heading+Text` — proves Puck works if builder blank |

**Puck Builder flow (no auto-open on refresh per request):**
1. `Merchant` input `merchant_1` → **Load** `puck-builder.html:884` → `pages:['gallery_100_images','gallery_small','mixed_100','mixed_999']` `PuckMuiJsonFileUtils.java:90`
2. `Page` dropdown `puck-builder.html:1011` → pick `gallery_small` (8 comps) or `mixed_100` (100) or `mixed_999` (999) → `openPage()` `puck-builder.html:908` `GET /api/puckmui/merchant/pages/merchant_1/{key}` → `Puck` `key={pageKey}` `puck-builder.html:1046` shows `Puck data: N` `puck-builder.html:1042` + `Fallback view` `puck-builder.html:1056`
3. Drag from palette `categories: {layout:[Grid,Flex,Space], typography:[Heading,Text], actions:[Button], content:[Card,Hero,Divider,Image]}` `puck-builder.html:400` (`Heading` `text/size/level/align` `puck-builder.html:552` per Puck repo) → **Save** `POST /api/puckmui/merchant/pages/{mid}/{key}` `puck-builder.html:913` → **Publish** `POST .../publish` `PuckMuiMerchantController.java:58` (sets `PUBLISHED` `PuckMuiPageConfigService.java:78`)

**JSON Builder flow:**
1. `Merchant` `merchant_1` → **Load** `app.js:34` → `GET /api/merchant/pages/merchant_1` → `["checkout","payment","seat_selection",...]` `data/pages/merchant_1/` (5)
2. Click page → `GET /api/merchant/pages/merchant_1/{key}` `MerchantController.java:39` → JSON in `textarea#editor`
3. **Save** `POST /api/merchant/pages/{mid}/{key}` `MerchantController.java:55` → **Publish** `POST .../publish` `MerchantController.java:72` → **Preview** `GET /api/client/page/{mid}/{key}` `ClientController.java:24` (`PUBLISHED` only `PageConfigService.java:102`) → `app.js:161` `renderScreen`

---

## 4. Verify APIs (PowerShell)

```powershell
# SDUI
curl http://localhost:8080/api/merchant/pages/merchant_1
curl http://localhost:8080/api/client/page/merchant_1/checkout
curl -X POST http://localhost:8080/api/client/action/submit -H "Content-Type: application/json" -d '{"action":"SEAT_SELECTION","data":{"seatId":"sec-a"}}'

# PuckMUI (isolated)
curl http://localhost:8080/api/puckmui/merchant/pages/merchant_1
# → {"pages":["gallery_100_images","gallery_small","mixed_100","mixed_999"],"count":4,"source":"puckmui"}
curl http://localhost:8080/api/puckmui/client/page/merchant_1/gallery_small
curl -X POST http://localhost:8080/api/puckmui/client/action/submit -H "Content-Type: application/json" -d '{"action":"CHECKOUT","data":{"email":"a@b.com","fullName":"Ada"}}'

# Browser direct:
# http://localhost:8080/api/puckmui/merchant/pages/merchant_1/gallery_small
```

---

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| `tailwind.css 404` | `npm run build:css` — `static/tailwind.css` gitignored `.gitignore:41` |
| `https://esm.sh/@puckeditor/core@0.20.2/puck.css 500` | Removed `puck-builder.html:10`, keep `https://esm.sh/@measured/puck@0.20.2/puck.css` + `unpkg` fallback. `Ctrl+F5` |
| `Puck` blank but `Load 200` `pages:['gallery_100_images']` `puck-builder.html:888` | Blank until `Page` dropdown pick `puck-builder.html:1011` → `openPage` `puck-builder.html:908` `key={pageKey}` `puck-builder.html:1046` (off-by-one fixed). Check `F12 → Console` `openPage response` `puck-builder.html:910` + `Puck data: N` `puck-builder.html:1042`, `Elements → div.Puck height>0`. Try `puck-test.html` `React 18.2` minimal. |
| `No PuckMUI pages` / `Failed to load` `puck-builder.html:889` | Backend not running or wrong `merchantId`. `GET http://localhost:8080/api/puckmui/merchant/pages/merchant_1` should `200` `PuckMuiMerchantController.java:16`. Re-run `.\gradlew bootRun`, `Ctrl+F5`. `build/resources/main/data/puckmui/pages` copied on `gradlew build`. |
| `Not published` `404` on `/api/client/page/...` | `status` is `DRAFT` `PageConfig.java:15`. Click **Publish** `MerchantController.java:72` / `PuckMuiMerchantController.java:58` before `GET /api/client/page` `ClientController.java:24` `getPublishedPage` `PageConfigService.java:102`. |
| `Load` reverts `small→100` | Fixed `PuckMuiPageConfigService.java:37` fresh `readFile` (was stale `cache.get` swap) + `puck-builder.html:914` `setPageKey` after `fetch` + `MenuItem key={k} value={k}` `puck-builder.html:1017` |
| `Heading align` not working | Fixed `puck-builder.html:562` `align = props.align` + `Box sx={textAlign:align, display:block, width:100%}` per `apps/demo/config/blocks/Heading/index.tsx:20` `align` `radio` `puck-builder.html:552` |

---

## 6. Stop & Rebuild

```powershell
# stop: Ctrl+C in bootRun terminal
.\gradlew build   # also copies src/main/resources/data/puckmui/pages/merchant_1/*.json (4) + static/* to build/resources
```

Docs: `DOCUMENTATION.md:1` (developer), `PREPARATION.md:1` (business, Oracle `JSON` `PREPARATION.md:31`, boundaries `PREPARATION.md:90`), `PUCK_UNIFIED_PROMPT.md:1` (strict `baseStrictFields` `puck-builder.html:381` `validatePuckFields` `puck-builder.html:399` + `UniformWrapper` `puck-builder.html:389`).

*Project `C:\sdui\demo` — `build.gradle:11` Java 17, `DemoApplication.java:6`.*
