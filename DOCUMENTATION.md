# SDUI Demo — Documentation

> Server-Driven UI (SDUI) demo with Spring Boot 4, dual JSON storage (SDUI `data/pages` + PuckMUI `data/puckmui/pages`), Tailwind CSS, and a Puck + MUI visual builder. Merchants define pages as JSON; the client renders them dynamically without redeploying the backend.

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [Tech Stack](#2-tech-stack)
3. [Project Structure](#3-project-structure)
4. [Prerequisites](#4-prerequisites)
5. [Getting Started](#5-getting-started)
6. [Configuration](#6-configuration)
7. [Architecture](#7-architecture)
8. [Backend — Deep Dive](#8-backend--deep-dive)
9. [API Reference](#9-api-reference)
10. [SDUI Data Model](#10-sdui-data-model)
11. [Component Catalog](#11-component-catalog)
12. [Action Handling](#12-action-handling)
13. [Frontend — Builders & Client Renderer](#13-frontend--builders--client-renderer)
14. [Styling](#14-styling)
15. [Sample Data & Navigation Flow](#15-sample-data--navigation-flow)
16. [Guide: Add a New Page / Merchant / Component](#16-guide-add-a-new-page--merchant--component)
17. [Build, Test & Deploy](#17-build-test--deploy)
18. [Troubleshooting & FAQ](#18-troubleshooting--faq)
19. [Roadmap Ideas](#19-roadmap-ideas)

---

## 1. Overview & Goals

This project demonstrates **Server-Driven UI** with **isolated PuckMUI** editing:

- **Backend** owns page definitions as JSON (`PageConfig` + `layout.components[]`). Two isolated stacks:
  - SDUI: `data/pages/{merchantId}/{pageKey}.json` via `service/JsonFileUtils.java:20` + `service/PageConfigService.java:15` → `/api/merchant` + `/api/client`
  - PuckMUI: `data/puckmui/pages/{merchantId}/{pageKey}.json` via `service/PuckMuiJsonFileUtils.java:14` + `service/PuckMuiPageConfigService.java:14` → `/api/puckmui/merchant` + `/api/puckmui/client`
- **Frontend** has two builders:
  - **JSON Visual Builder** (`src/main/resources/static/index.html` + `app.js`) — 3-column editor (pages list, JSON editor, published preview) with client-side SDUI router, talks to SDUI APIs.
  - **Puck + MUI Visual Builder** (`src/main/resources/static/puck-builder.html`) — **PuckMUI-only** (`apiBase="/api/puckmui"` `puck-builder.html:862`), drag-and-drop canvas backed by [`@measured/puck@0.20.2`](https://github.com/puckeditor/puck) (alias `@puckeditor/core`) and MUI 5, converts between SDUI `layout` and Puck `data` (`layoutToPuck` / `puckToLayout` `puck-builder.html:81`) with grouped `style` + `advanced` objects and recursive `slot` handling for `Grid/Flex/Hero`.
  - **Puck Minimal Test** (`static/puck-test.html:1`) — `React 18.2` isolated test for Puck rendering.
- **Client renderer** (`app.js:renderScreen` / `renderComponent`) interprets `layout` recursively and executes `actions` via `POST /api/client/action/submit` (SDUI) or `POST /api/puckmui/client/action/submit` (PuckMUI, same `handleAction`).
- **Storage** is filesystem JSON with classpath fallback (`src/main/resources/data/...`) so sample data ships inside the jar. `PuckMUI` is fully isolated — `GET /api/merchant/pages/merchant_1` never sees `data/puckmui/pages`.

Typical SDUI flow for `merchant_1`:

```
seat_selection (SeatGrid) --SEAT_SELECTION--> checkout (OrderSummary + Form)
  --CHECKOUT--> checkout_success (SuccessCard, {{fullName}}/{{email}} templating)
  --> payment (PaymentForm) --PAYMENT--> confirmation (ConfirmationCard) --> seat_selection
```

PuckMUI flow: `GET /api/puckmui/merchant/pages/merchant_1` → `["gallery_100_images","gallery_small","mixed_100"]` → `puck-builder.html` canvas (blank until `Page` dropdown pick `puck-builder.html:1011`, `key={pageKey}` `puck-builder.html:1046` forces remount, `openPage` `puck-builder.html:908` sets `pageKey`+`puckData` together after fetch to avoid off-by-one).

---

## 2. Tech Stack

| Layer | Technology | Version / Notes |
|---|---|---|
| Language | Java | 17 (toolchain `JavaLanguageVersion.of(17)` in `build.gradle:11`) |
| Framework | Spring Boot | `4.1.1` (`build.gradle:3`), `spring-boot-starter-web`, `spring-boot-starter-validation` |
| JSON | Jackson | `jackson-databind` + `jackson-datatype-jsr310` (`build.gradle:23-24`), custom `ObjectMapper` in `config/AppConfig.java:13` (JavaTimeModule, `INDENT_OUTPUT`) |
| Boilerplate | Lombok | `compileOnly` + `annotationProcessor` (`build.gradle:25-26`) for `PageConfig.java:11`, `ActionRequest.java:11` |
| Build | Gradle | Wrapper `gradlew`/`gradlew.bat`, `settings.gradle:8` rootProject `demo` |
| Frontend CSS | Tailwind CSS | `@tailwindcss/cli ^4.0.0` (`package.json:11`), input `static/tailwind.in.css:1`, output `static/tailwind.css` (gitignored) |
| Visual Builder | Puck + MUI | `@measured/puck@0.20.2` (alias `@puckeditor/core`) + `@mui/material@5.15.20` via `esm.sh` importmap in `puck-builder.html:15-30`, CSS `https://esm.sh/@measured/puck@0.20.2/puck.css` `puck-builder.html:10` + `unpkg` fallback (puckeditor/core CSS 500 removed) |
| Runtime JS | Vanilla + React 18/19 | `app.js` vanilla; `puck-builder.html` React 19, `puck-test.html:1` React 18.2 for minimal test |
| Data | JSON files | SDUI `data/pages/...` `service/JsonFileUtils.java:20`, PuckMUI `data/puckmui/pages/...` `service/PuckMuiJsonFileUtils.java:14` |
| CORS | Spring MVC | `config/CorsConfig.java:16` allows `/api/**` and `/api/puckmui/**` with `allowedOriginPatterns("*")` |

---

## 3. Project Structure

```
demo/
├── build.gradle                      # Spring Boot 4.1.1, Java 17, dependencies
├── settings.gradle                   # rootProject.name = 'demo'
├── gradle.properties                 # local overrides (commented out)
├── package.json                      # tailwindcss + onchange scripts
├── HELP.md                           # Spring Initializr help
├── DOCUMENTATION.md                  # ← this file
├── src/
│   ├── main/
│   │   ├── java/com/sdui/demo/
│   │   │   ├── DemoApplication.java          # @SpringBootApplication entry
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java            # ObjectMapper bean
│   │   │   │   └── CorsConfig.java           # CORS for /api/**
│   │   │   ├── controller/
│   │   │   │   ├── MerchantController.java   # /api/merchant/pages/...
│   │   │   │   ├── ClientController.java     # /api/client/page/... + /action/submit
│   │   │   │   ├── PuckMuiMerchantController.java # /api/puckmui/merchant/pages/...
│   │   │   │   └── PuckMuiClientController.java   # /api/puckmui/client/page/... + /action/submit
│   │   │   ├── model/
│   │   │   │   ├── PageConfig.java           # pageId, pageKey, merchantId, status, version, layout
│   │   │   │   └── ActionRequest.java        # action, endpoint, target, data, params
│   │   │   └── service/
│   │   │       ├── PageConfigService.java    # SDUI CRUD + publish + handleAction + cache (concurrent)
│   │   │       ├── JsonFileUtils.java        # SDUI data/pages read/write/list + classpath fallback
│   │   │       ├── PuckMuiPageConfigService.java # PuckMUI CRUD (fresh read, no stale cache) PuckMuiPageConfigService.java:37
│   │   │       └── PuckMuiJsonFileUtils.java # PuckMUI data/puckmui/pages
│   │   └── resources/
│   │       ├── application.properties        # spring.application.name=demo
│   │       ├── data/
│   │       │   ├── pages/merchant_1/        # SDUI sample JSON (5 pages + .bak)
│   │       │   │   ├── seat_selection.json
│   │       │   │   ├── checkout.json
│   │       │   │   ├── checkout_success.json
│   │       │   │   ├── payment.json
│   │       │   │   └── confirmation.json
│   │       │   └── puckmui/pages/merchant_1/ # PuckMUI isolated (3 pages)
│   │       │       ├── gallery_100_images.json # 102 comps: Hero+100×Image+Flex (picsum seed mixed)
│   │       │       ├── gallery_small.json      # 8 comps: Heading+Text+5×Image+Button
│   │       │       └── mixed_100.json          # 100 mixed: Grid/Flex/Space/Heading/Text/Button/Card/Hero/Divider/Image/SeatGrid/OrderSummary/Form/PaymentForm/ConfirmationCard/SuccessCard 28501b
│   │       └── static/
│   │           ├── index.html                # JSON Visual Builder (3-col, Tailwind) → SDUI API
│   │           ├── app.js                    # editor, list, SDUI renderer + router
│   │           ├── puck-builder.html         # PuckMUI builder (React, importmap, categories, slots, style grouping)
│   │           ├── puck-test.html            # minimal Puck test (React 18.2)
│   │           ├── puck-client.js            # styled renderer helpers (getStyleSourceStyled, buildInlineStyleStyled)
│   │           ├── tailwind.in.css           # @source + CSS vars + @import tailwind
│   │           ├── tailwind.css              # generated (gitignored)
│   │           └── styles.css                # legacy CSS (kept for reference)
│   └── test/java/com/sdui/demo/
│       └── DemoApplicationTests.java         # contextLoads
└── gradle/wrapper/
```

---

## 4. Prerequisites

- **Java 17+** (Gradle toolchain will enforce 17; JDK 17 or 21 works)
- **Node.js 18+** + npm (only for Tailwind build / watch)
- PowerShell 5.1 or bash (Windows `gradlew.bat` is included)
- No database required — pages are JSON files (dual stores).

Verify:

```powershell
java -version
node -v; npm -v
.\gradlew --version
```

---

## 5. Getting Started

### 5.1 Clone & install Node deps

```powershell
npm install
```

### 5.2 Build Tailwind CSS

One-off build:

```powershell
npm run build:css
```

Or watch mode (rebuilds on `*.json` / `tailwind.in.css` changes):

```powershell
npm run watch:css   # in one terminal
npm run watch:json  # uses onchange to re-trigger build:css on JSON edits (optional)
```

Output is `src/main/resources/static/tailwind.css` (gitignored, generated).

### 5.3 Run the backend

```powershell
.\gradlew bootRun
# or: .\gradlew build; java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

Default port `8080` (`application.properties` does not override `server.port`).

### 5.4 Open the builders

- **JSON Builder (SDUI)**: http://localhost:8080/index.html → talks to `/api/merchant` + `/api/client`
- **Puck Builder (PuckMUI-only)**: http://localhost:8080/puck-builder.html → `apiBase="/api/puckmui"` `puck-builder.html:862` → `/api/puckmui/merchant` + `/api/puckmui/client`, blank until `Load` → pick `Page` dropdown (`gallery_small`, `gallery_100_images`, `mixed_100`) → `openPage()` `puck-builder.html:908` `GET /api/puckmui/merchant/pages/merchant_1/{key}` → `Puck` `key={pageKey}` `puck-builder.html:1046` remounts. No auto-open on refresh (per request).
- **Puck Test (minimal)**: http://localhost:8080/puck-test.html → `React 18.2` `2` comps `Heading`+`Text` — if this renders but `puck-builder.html` blank, check `categories/slot` config or `React 19` mismatch.

Workflow:

1. Enter `Merchant` (default `merchant_1`) → **Load** `puck-builder.html:884` `GET /api/puckmui/merchant/pages/merchant_1` → `pages:['gallery_100_images','gallery_small','mixed_100']`.
2. Pick `Page` dropdown `puck-builder.html:1011` → `openPage()` `GET /api/puckmui/merchant/pages/merchant_1/{key}` → `Puck` canvas shows `Grid/Flex/Heading/Text/...` + debug bar `Puck data: N` `puck-builder.html:1042` + `Fallback view:` `puck-builder.html:1056`.
3. Drag from left palette (categories: Layout `Grid,Flex,Space`, Typography `Heading,Text`, Actions `Button`, Content `Card,Hero,Divider,Image`, SDUI `SeatGrid...`) → **Save** `POST /api/puckmui/merchant/pages/{mid}/{key}` `puck-builder.html:913` → **Publish** `POST /api/puckmui/merchant/pages/{mid}/{key}/publish` `puck-builder.html:914` (sets `PUBLISHED`).

> Tip: `GET /api/puckmui/client/page/...` `PuckMuiClientController.java:24` only returns `PUBLISHED` pages (same gate `PuckMuiPageConfigService.java:96`).

---

## 6. Configuration

| Property | Default | Where | Notes |
|---|---|---|---|
| `spring.application.name` | `demo` | `resources/application.properties:1` | App name |
| `server.port` | `8080` | Spring default | Add `server.port=8081` to `application.properties` to change |
| `sdui.pages.dir` | *(auto-resolved)* | `@Value("${sdui.pages.dir:}")` in `service/JsonFileUtils.java:26` | SDUI filesystem base dir. If blank, resolves via `ClassLoader.getResource("data/pages")` → falls back to `Paths.get("data","pages")`. Set e.g. `sdui.pages.dir=C:/data/pages` to persist outside the jar. |
| `sdui.puckmui.dir` | *(auto-resolved)* | `@Value("${sdui.puckmui.dir:}")` in `service/PuckMuiJsonFileUtils.java:14` | PuckMUI base dir. If blank, `data/puckmui/pages` via classpath or `data/puckmui/pages` cwd. Set e.g. `sdui.puckmui.dir=C:/data/puckmui/pages` |

Example `application.properties`:

```properties
spring.application.name=demo
server.port=8080
sdui.pages.dir=C:/sdui-data/pages
sdui.puckmui.dir=C:/sdui-data/puckmui/pages
```

CORS is wide-open for `/api/**` and `/api/puckmui/**` in `config/CorsConfig.java:16` (`allowedOriginPatterns("*")`, `allowCredentials(true)`). Tighten for production.

---

## 7. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser                                                        │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │ index.html      │  │ puck-builder     │  │ puck-test    │  │
│  │ JSON Builder    │  │ PuckMUI builder  │  │ minimal      │  │
│  │ app.js (SDUI)   │  │ puck-builder.html│  │ puck-test.html│
│  └────────┬────────┘  └────────┬─────────┘  └──────┬───────┘  │
│           │                    │                   │           │
└───────────┼────────────────────┼───────────────────┼───────────┘
            │  /api/merchant/**  │ /api/puckmui/**   │ /api/client/**
            ▼                    ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot (DemoApplication.java)                             │
│  ┌─────────────────────┐  ┌────────────────────┐  ┌─────────────────┐
│  │ MerchantController  │  │ PuckMuiMerchant    │  │ ClientControllers│
│  │ list/get/save/      │  │ Controller         │  │ getPublishedPage/│
│  │ publish/delete      │  │ (same but puckmui) │  │ submitAction    │
│  └─────────┬───────────┘  └─────────┬──────────┘  └─────────┬────────┘
│            └──────────┬─────────────┴─────────────┬──────────┘
│                       ▼                           ▼
│            ┌─────────────────────┐  ┌──────────────────────────┐
│            │ PageConfigService   │  │ PuckMuiPageConfigService │  cache: ConcurrentHashMap
│            │ - save/publish/     │  │ - same, fresh read       │  key = puckmui::merchant::pageKey
│            │   getPublished/     │  │   (no stale cache)       │  PuckMuiPageConfigService.java:37
│            │   handleAction()    │  │   handleAction()         │
│            └─────────┬───────────┘  └─────────┬────────────────┘
│                      ▼                        ▼
│            ┌─────────────────────┐  ┌──────────────────────────┐
│            │ JsonFileUtils       │  │ PuckMuiJsonFileUtils     │  filesystem + classpath
│            │ resolvePath()       │  │ resolvePath()            │  fallback
│            │ read/write/delete/  │  │ read/write/delete/       │
│            │ listPages()         │  │ listPages()              │
│            └─────────────────────┘  └──────────────────────────┘
└─────────────────────────────────────────────────────────────────┘
            ▼                        ▼
     data/pages/{mid}/{key}.json   data/puckmui/pages/{mid}/{key}.json
     src/main/resources/data/pages/  src/main/resources/data/puckmui/pages/
     merchant_1/*.json (5)           merchant_1/gallery_100_images.json (102: Hero+100×Image+Flex)
                                     gallery_small.json (8), mixed_100.json (100 mixed)
```

**Key design choices:**

- **Dual stores** isolated — `GET /api/merchant/pages/merchant_1` never lists `data/puckmui/pages`.
- **File + classpath duality** (`service/JsonFileUtils.java:57-72`, `service/PuckMuiJsonFileUtils.java:19`): reads prefer filesystem, falls back to `ClassPathResource` so the packaged jar still serves sample data read-only.
- **Cache**: SDUI `service/PageConfigService.java:20` `ConcurrentHashMap` `merchant::page`; PuckMUI `service/PuckMuiPageConfigService.java:37` always `readFile()` then `cache.put()` (no stale swap `gallery_small` ↔ `100`).
- **Status gate**: `getPublishedPage()` returns `null` unless `status == PUBLISHED` (`service/PageConfigService.java:102`, `PuckMuiPageConfigService.java:96`).
- **Versioning**: `savePage()` auto-increments `version` and sets `createdAt`/`updatedAt` (`service/PageConfigService.java:48-72`).
- **Puck data sync**: `puck-builder.html:908` `openPage()` sets `pageKey` + `puckData` together after `fetch` (fixes off-by-one blank→small→100), `Puck` `key={pageKey}` `puck-builder.html:1046` forces remount, `layoutToPuck`/`puckToLayout` `puck-builder.html:81` handle `slot` recursively `puckSlotToSdui`/`sduiToPuckSlot` `puck-builder.html:89`.

---

## 8. Backend — Deep Dive

### 8.1 Entry Point

- `DemoApplication.java:6` — `@SpringBootApplication`, standard `SpringApplication.run`.

### 8.2 Config

- `config/AppConfig.java:13` — `ObjectMapper` with `JavaTimeModule`, `WRITE_DATES_AS_TIMESTAMPS=false`, `INDENT_OUTPUT=true`. Injected into both `JsonFileUtils`.
- `config/CorsConfig.java:12` — `WebMvcConfigurer` for `/api/**` and `/api/puckmui/**`. Allowed methods `GET,POST,PUT,DELETE,OPTIONS`.

### 8.3 Models

**`model/PageConfig.java:15`**
```java
private String pageId;        // e.g. merchant_1-checkout
private String pageKey;       // e.g. checkout
private String merchantId;    // e.g. merchant_1
private String status;        // DRAFT / PUBLISHED / ARCHIVED
private Long version;         // auto-incremented on save
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
private Map<String,Object> layout; // SDUI tree: {type,title,subtitle,components:[]}
```
`@JsonInclude(NON_NULL)` keeps JSON tidy. `layout` is a free-form map to allow any component schema without a rigid DTO.

**`model/ActionRequest.java:15`**
```java
private String action;   // e.g. SEAT_SELECTION, CHECKOUT, PAYMENT
private String endpoint; // optional override
private String target;   // navigation target (rarely used server-side)
private Map<String,Object> data;
private Map<String,Object> params;
```

### 8.4 Service

**`service/JsonFileUtils.java:20`** — SDUI filesystem abstraction:
- `fileSystemBaseDir()` — respects `sdui.pages.dir` else resolves `data/pages` on classpath or `data/pages` cwd.
- `resolvePath(merchantId,pageKey)` → `baseDir/merchantId/pageKey.json`
- `readFile()` — filesystem first, classpath second, else `null`.
- `writeFile()` — `Files.createDirectories` + `writerWithDefaultPrettyPrinter`.
- `deleteFile()` — `Files.deleteIfExists`.
- `listPages()` — lists filesystem dir + merges classpath entries, deduplicates, sorted.

**`service/PuckMuiJsonFileUtils.java:14`** — identical but for `data/puckmui/pages` and `sdui.puckmui.dir`.

**`service/PageConfigService.java:15`** — SDUI business logic:
- `listPages`, `getPage` (cache-through), `savePage` (create vs update, version bump), `publishPage` (sets `PUBLISHED`), `deletePage`, `getPublishedPage` (status check), `clearCache`, `handleAction`.

**`service/PuckMuiPageConfigService.java:14`** — PuckMUI, same but `getPage()` always fresh read `PuckMuiPageConfigService.java:37` to avoid swapped `gallery_small`/`100` stale cache.

### 8.5 Controllers

See [API Reference](#9-api-reference) below. Both SDUI and PuckMUI controllers wrap logic in `try/catch` and return `ResponseEntity` with `Map.of("error",...)` on failure. PuckMUI adds `source:puckmui` in list responses.

---

## 9. API Reference

Base URL: `http://localhost:8080`

### 9.1 SDUI Merchant API — `controller/MerchantController.java:14` (`/api/merchant`) — `data/pages`

| Method | Path | Description | Source |
|---|---|---|---|
| `GET` | `/api/merchant/pages/{merchantId}` | List page keys for a merchant | `MerchantController.java:24` |
| `GET` | `/api/merchant/pages/{merchantId}/{pageKey}` | Get raw `PageConfig` (any status) | `MerchantController.java:39` |
| `POST` | `/api/merchant/pages/{merchantId}/{pageKey}` | Create/update page (upserts `layout`, bumps `version`) | `MerchantController.java:55` |
| `POST` | `/api/merchant/pages/{merchantId}/{pageKey}/publish` | Set `status=PUBLISHED` | `MerchantController.java:72` |
| `DELETE` | `/api/merchant/pages/{merchantId}/{pageKey}` | Delete JSON file + evict cache | `MerchantController.java:89` |

**Examples:**

```bash
# List SDUI pages
curl http://localhost:8080/api/merchant/pages/merchant_1
# → {"merchantId":"merchant_1","pages":["checkout","checkout_success","confirmation","payment","seat_selection"],"count":5}

# Get a page (draft or published)
curl http://localhost:8080/api/merchant/pages/merchant_1/checkout

# Save (create) a page
curl -X POST http://localhost:8080/api/merchant/pages/merchant_1/my_page \
  -H "Content-Type: application/json" \
  -d '{
    "pageId":"merchant_1-my_page","pageKey":"my_page","merchantId":"merchant_1",
    "status":"DRAFT","layout":{"type":"Screen","title":"My Page","components":[]}
  }'
# → {"message":"Page saved","page":{...,"version":1}}

# Publish
curl -X POST http://localhost:8080/api/merchant/pages/merchant_1/my_page/publish
# → {"message":"Page published","page":{...,"status":"PUBLISHED"}}

# Delete
curl -X DELETE http://localhost:8080/api/merchant/pages/merchant_1/my_page
```

### 9.2 SDUI Client API — `controller/ClientController.java:14` (`/api/client`) — `data/pages`

| Method | Path | Description | Source |
|---|---|---|---|
| `GET` | `/api/client/page/{merchantId}/{pageKey}` | Get **published** page only (404 if `DRAFT`) | `ClientController.java:24` |
| `POST` | `/api/client/action/submit` | Submit an action; returns `{success, message, ...}` | `ClientController.java:42` |

**Action examples:**

```bash
# Fetch published page for rendering
curl http://localhost:8080/api/client/page/merchant_1/checkout

# SEAT_SELECTION
curl -X POST http://localhost:8080/api/client/action/submit \
  -H "Content-Type: application/json" \
  -d '{"action":"SEAT_SELECTION","data":{"seatId":"sec-a","section":"Section A (Front)"}}'
# → {"success":true,"seatId":"sec-a","section":"Section A (Front)","message":"Seat sec-a reserved successfully",...}

# CHECKOUT (requires email + fullName)
curl -X POST http://localhost:8080/api/client/action/submit \
  -H "Content-Type: application/json" \
  -d '{"action":"CHECKOUT","data":{"email":"a@b.com","fullName":"Ada"}}'

# PAYMENT (requires amount + method)
curl -X POST http://localhost:8080/api/client/action/submit \
  -H "Content-Type: application/json" \
  -d '{"action":"PAYMENT","data":{"amount":129.5,"method":"CARD"}}'

# Generic action (always success)
curl -X POST http://localhost:8080/api/client/action/submit \
  -H "Content-Type: application/json" \
  -d '{"action":"MY_CUSTOM_ACTION","data":{"foo":"bar"}}'
```

Validation & status codes (`controller/ClientController.java:42-64`):
- `400` if body missing, `action` blank, or `handleAction` returns `success=false` (e.g. missing `seatId`, `email`, `amount`).
- `200` on success.
- `500` on unexpected errors (exception message in `error` field).

### 9.3 PuckMUI Merchant API — `controller/PuckMuiMerchantController.java:14` (`/api/puckmui/merchant`) — `data/puckmui/pages`

Isolated from SDUI, same contract but `source:puckmui`:

| Method | Path | Description | Source |
|---|---|---|---|
| `GET` | `/api/puckmui/merchant/pages/{merchantId}` | List PuckMUI page keys | `PuckMuiMerchantController.java:16` |
| `GET` | `/api/puckmui/merchant/pages/{merchantId}/{pageKey}` | Get raw PuckMUI `PageConfig` | `PuckMuiMerchantController.java:26` |
| `POST` | `/api/puckmui/merchant/pages/{merchantId}/{pageKey}` | Create/update PuckMUI page | `PuckMuiMerchantController.java:40` |
| `POST` | `/api/puckmui/merchant/pages/{merchantId}/{pageKey}/publish` | Publish PuckMUI page | `PuckMuiMerchantController.java:58` |
| `DELETE` | `/api/puckmui/merchant/pages/{merchantId}/{pageKey}` | Delete PuckMUI page | `PuckMuiMerchantController.java:76` |

```bash
curl http://localhost:8080/api/puckmui/merchant/pages/merchant_1
# → {"merchantId":"merchant_1","pages":["gallery_100_images","gallery_small","mixed_100"],"count":3,"source":"puckmui"}

curl http://localhost:8080/api/puckmui/merchant/pages/merchant_1/gallery_small
curl -X POST http://localhost:8080/api/puckmui/merchant/pages/merchant_1/mixed_100 -H "Content-Type: application/json" -d @mixed_100.json
```

### 9.4 PuckMUI Client API — `controller/PuckMuiClientController.java:14` (`/api/puckmui/client`) — `data/puckmui/pages`

| Method | Path | Description | Source |
|---|---|---|---|
| `GET` | `/api/puckmui/client/page/{merchantId}/{pageKey}` | Get **published** PuckMUI page only | `PuckMuiClientController.java:24` |
| `POST` | `/api/puckmui/client/action/submit` | Submit PuckMUI action | `PuckMuiClientController.java:38` |

```bash
curl http://localhost:8080/api/puckmui/client/page/merchant_1/gallery_small
curl -X POST http://localhost:8080/api/puckmui/client/action/submit -H "Content-Type: application/json" -d '{"action":"SEAT_SELECTION","data":{"seatId":"sec-a"}}'
```

---

## 10. SDUI Data Model

### 10.1 PageConfig

```json
{
  "pageId": "merchant_1-checkout",
  "pageKey": "checkout",
  "merchantId": "merchant_1",
  "status": "PUBLISHED",
  "version": 1,
  "createdAt": "2026-08-27T10:05:00",
  "updatedAt": "2026-08-27T10:05:00",
  "layout": { }
}
```

- `pageId` is conventionally `{merchantId}-{pageKey}` but editable.
- `status` values: `DRAFT` (authoring only), `PUBLISHED` (client-visible), `ARCHIVED` (not used in sample data but supported).
- `layout` is the SDUI tree (see below).

### 10.2 Layout

```json
{
  "type": "Screen",
  "title": "Checkout",
  "subtitle": "Review your order",
  "components": [ /* Component[] */ ]
}
```

- `type` is typically `Screen` (others like `Modal`/`Drawer` are supported in `puck-builder.html:216`).
- `title`/`subtitle` rendered by `app.js:renderScreen` and `puck-builder.html` root fields.

### 10.3 Component (generic shape)

```json
{
  "type": "Button",
  "id": "continueBtn",
  "label": "Continue to Checkout",
  "class": "inline-flex ...",
  "actions": { "onClick": { "type": "navigate", "target": "checkout" } }
}
```

- `type` dispatches to `app.js:renderComponent` (`Button`, `SeatGrid`, `OrderSummary`, `Form`, `PaymentForm`, `ConfirmationCard`, `SuccessCard` explicitly handled; unknown types render a generic `comp` wrapper).
- `id` used for selection in Puck builder.
- `class` / `className` — Tailwind utilities (see [Styling](#14-styling)).
- `actions` — see below.
- **PuckMUI** components also support `style` object (`height,width,background,textColor...` `puck-builder.html:342`) + `advanced` (`className`,`customCssJson`) and `slot` (`items`/`content` for `Grid/Flex/Hero`).

### 10.4 Actions & Navigation

Components declare actions like:

```json
"actions": {
  "onSelect": {
    "type": "validateAndReserve",
    "endpoint": "/api/client/action/submit",
    "action": "SEAT_SELECTION",
    "success": { "type": "navigate", "target": "checkout", "message": "Seats reserved!" },
    "error": { "type": "alert", "message": "Unable to reserve." }
  },
  "onSubmit": {
    "type": "submit",
    "endpoint": "/api/client/action/submit",
    "action": "CHECKOUT",
    "success": { "type": "navigate", "target": "checkout_success" }
  },
  "onClick": { "type": "navigate", "target": "payment" }
}
```

Supported `success.type` / `onClick.type` in `app.js`:
- `navigate` — `navigateTo(target)` pushes onto `navStack` and re-renders.
- `back` — `goBack()` pops the stack (`app.js:171`).
- (Puck variant `type: "back"` also handled in `puck-builder.html`).

Templating: `SuccessCard` supports `{{fullName}}` / `{{email}}` via `app.js:tpl` which interpolates `lastFormData` (captured on successful `submitForm`).

---

## 11. Component Catalog

### SDUI Booking Blocks

| Type | Sample File | Props | Render |
|---|---|---|---|
| **Button** | `seat_selection.json:14`, `checkout_success.json:24` | `label`, `class`/`className`, `actions.onClick` (`navigate`/`back`) | `app.js:318-331` |
| **SeatGrid** | `seat_selection.json:22` | `sections[]` (`id,name,price,available,rows`), `eventId`, `currency`, `actions.onSelect` | `app.js:274-284`, `puck-builder.html:328` |
| **OrderSummary** | `checkout.json:14` | `lines[]` (`label,amount`), `total`, `currency` | `app.js:286-289`, `puck-builder.html:302` |
| **Form** | `checkout.json:24` | `fields[]` (`name,type,label,required`), `actions.onSubmit` | `app.js:333-348`, `puck-builder.html:364` |
| **PaymentForm** | `payment.json:22` | `amount`, `currency`, `methods[]`, `fields[]`, `actions.onSubmit` | `app.js:333-348` (shared with Form), `puck-builder.html:399` |
| **ConfirmationCard** | `confirmation.json:21` | `orderId`, `event`, `seats[]`, `total` | `app.js:291-296`, `puck-builder.html:441` |
| **SuccessCard** | `checkout_success.json:14` | `message`, `fullName` (`{{fullName}}`), `email` (`{{email}}`), `icon` | `app.js:298-316`, `puck-builder.html:468` |

### Puck Editor Blocks (copied from https://github.com/puckeditor/puck `apps/demo/config/blocks`)

| Type | Puck Demo Source | Props (Puck `fields`) | Render |
|---|---|---|---|
| **Grid** | `Grid/index.tsx:18` | `numColumns:number(1-12)`, `gap:select(spacingOptions)`, `items:slot`, `style`+`advanced` | `display:grid` `gridTemplateColumns:repeat(numColumns,1fr)` `gap` + `<Items/>` DropZone `puck-builder.html:131` |
| **Flex** | `Flex/index.tsx:18` | `direction:radio(row/column)`, `justifyContent:radio(start/center/end)`, `gap:select`, `wrap:radio(wrap/nowrap)`, `items:slot` | `display:flex` `puck-builder.html:147` |
| **Space** | `Space/index.tsx:12` | `size:select(spacingOptions)`, `direction:radio(vertical/horizontal/"")`, `inline:true` `puck.dragRef` | `height/width: size` `puck-builder.html:164` |
| **Heading** | `Heading/index.tsx:20` | `text:textarea(contentEditable)`, `size:select(xxxl→xs)`, `level:select(1-6)`, `align:radio` | `sizeMap→fontSize` `Tag=h{level}` `puck-builder.html:182` |
| **Text** | `Text/index.tsx:8` | `text:textarea(contentEditable)`, `size:select(s/m/l)`, `align:radio`, `color:radio(default/muted)`, `maxWidth:text` | `puck-builder.html:207` |
| **Button** | `Button/index.tsx:10` | `label:text(contentEditable)`, `href:text`, `variant:radio(primary/secondary)`, `size:select`, `actionsJson:textarea` | `MUI Button` `variant primary→contained` `puck-builder.html:232` |
| **Card** | `Card/index.tsx:18` | `title:text`, `description:textarea`, `icon:select(star/sparkles/target/rocket)`, `mode:radio(flat/card)` | `Card` `elevation` `puck-builder.html:265` |
| **Hero** | `Hero` (demo) | `title:text`, `description:textarea`, `align:radio`, `content:slot` | `bgcolor:#f8fafc` + `<Content/>` `puck-builder.html:295` |
| **Divider** | — | `label:text` | `Divider` `puck-builder.html:495` |
| **Image** | — | `src:text`, `alt:text`, `caption:text` | `img` `maxWidth:100%` `puck-builder.html:507` |

**Categories** `puck-builder.html:400` `categories: {layout:[Grid,Flex,Space], typography:[Heading,Text], actions:[Button], content:[Card,Hero,Divider,Image], sdui:[SeatGrid,OrderSummary,Form,PaymentForm,ConfirmationCard,SuccessCard]}` mirrors `apps/demo/config/index.tsx:25`.

**Styling props (all Puck components):** grouped `style:object` (`height,width,background,textColor,fontSize,fontWeight,fontFamily,padding,margin,border,borderRadius,textAlign` `puck-builder.html:342`) + `advanced:object(className,customCssJson)` `puck-builder.html:360` — fixes prior 13 loose selects. `buildStyleSx()` `puck-builder.html:316` merges `style` + legacy flat + `advanced.customCssJson` (via `parseSxJson`), `getClassName()` `puck-builder.html:338` reads `advanced.className`. Also usable in raw JSON `class`/`sxJson` `puck-client.js:11`.

---

## 12. Action Handling

Server: `service/PageConfigService.java:112` and `service/PuckMuiPageConfigService.java:111` `handleAction(String action, Map data, Map params)` (same logic, isolated):

| Action aliases | Required `data` | Validation | Success response |
|---|---|---|---|
| `SEAT_SELECTION` / `selectSeat` | `seatId` (non-blank), `section` (optional) | `400` if `seatId` missing | `{success:true, seatId, section, message:"Seat ... reserved"}` |
| `CHECKOUT` / `submitCheckout` | `email` + `fullName` (both non-blank) | `success=false` if missing | `{success:true, message:"Checkout submitted", data}` |
| `PAYMENT` / `submitPayment` | `amount` + `method` | `success=false` if missing | `{success:true, message:"Payment of ... via ... processed"}` |
| *any other* | — | — | `{success:true, message:"Action '...' processed", data, params}` |

Client: `app.js:189-239`:
- `submitForm(btn)` collects inputs inside `.comp`, posts to `action.endpoint || "/api/client/action/submit"` (or `"/api/puckmui/client/action/submit"` for PuckMUI), stores `lastFormData` on success, then honors `action.success.type === "navigate"` or shows message.
- `selectSeat(el)` similar for seat rows.
- Both respect `action.error.message` on failure.

---

## 13. Frontend — Builders & Client Renderer

### 13.1 JSON Visual Builder — `static/index.html` + `static/app.js` (SDUI)

- **Layout** (`index.html:25`): CSS grid `260px 1fr 420px`, header with merchant input, 3 columns (Pages / Editor / Client Preview).
- **Styling**: 100% Tailwind utilities (`tailwind.css` + CSS vars `--bg`, `--panel`, etc.). No external JS framework.
- **`app.js` responsibilities**:
  - `loadPages()` — `GET /api/merchant/pages/{mid}` → renders `.page-item` list (`app.js:34`).
  - `openPage(key)` — `GET /api/merchant/pages/...` → fills `textarea#editor` with formatted JSON.
  - `newPage()` / `savePage()` / `publishPage()` / `deletePage()` / `formatJson()` — CRUD via SDUI merchant API.
  - `renderClientPage(key)` — `GET /api/client/page/...` → `renderScreen` → `#preview`.
  - **Router**: `navStack[]`, `navigateTo`, `goBack`, `updateNavBar` (`app.js:147-182`).
  - **Renderer**: `renderScreen(layout)` + `renderComponent(c)` (see §11), `esc`/`tpl`/`cx` helpers. Handles 100+ comps `O(n)` string `innerHTML` — see §18 for many-components note.

### 13.2 Puck + MUI Visual Builder — `static/puck-builder.html` (PuckMUI-only)

- **Stack**: React 19, Puck 0.20.2, MUI 5, Emotion, `es-module-shims` + importmap (`puck-builder.html:15`). `puck-test.html:1` uses React 18.2 minimal to isolate `React 19` mismatch.
- **PuckMUI-only**: `apiBase="/api/puckmui"` `puck-builder.html:862` → `GET /api/puckmui/merchant/pages/merchant_1` → `["gallery_100_images","gallery_small","mixed_100"]`. No auto-open on refresh (per request) — blank `Chip:no page selected` `puck-builder.html:1033` + `Fallback view` `puck-builder.html:1056` until `Page` dropdown pick `puck-builder.html:1011` → `openPage()` `puck-builder.html:908` `GET /api/puckmui/merchant/pages/{mid}/{key}` → `setPageKey`+`setPuckData` together after fetch (fixes off-by-one `key={pageKey}` `puck-builder.html:1046` remount), `Puck data: N` debug bar `puck-builder.html:1042`.
- **Theme**: Dark palette (`#0f1220` bg, `#6c8cff` primary, `#3ad29f` secondary) via `createTheme` (`puck-builder.html:62`).
- **Conversion** (`puck-builder.html:81`):
  - `STYLE_KEYS` `puck-builder.html:85` + `SLOT_KEYS=["items","content"]` `puck-builder.html:88` + `puckSlotToSdui`/`sduiToPuckSlot` `puck-builder.html:89` recursively handle `Grid/Flex/Hero` slots.
  - `layoutToPuck(layout)` — if `hasNested` (any `items`/`content` with `type`) uses `sduiToPuckSlot`, else flat map with `style` grouping + `advanced`, and `array` fields (`lines`, `sections`...) directly (no `*Json` textarea).
  - `puckToLayout(data)` — if `hasSlot` in `data.content` uses `puckSlotToSdui`, else flat + `flatten style` back to top-level for `app.js` compat.
  - `buildStyleSx`/`getStyleProps`/`getClassName` `puck-builder.html:308` merge `style` + legacy flat + `advanced.customCssJson`.
- **Puck config** (`puck-builder.html:391`): `categories` + `root` + 16 components (8 Puck editor `Grid,Flex,Space,Heading,Text,Button,Card,Hero,Divider,Image` + 6 SDUI `SeatGrid,OrderSummary,Form,PaymentForm,ConfirmationCard,SuccessCard`), each `fields` (Puck `text/textarea/select/radio/number/array/slot/object`), `defaultProps: {style:{}, advanced:{...}}`, `render` (MUI + `Selectable` `puck-builder.html:279` click-to-JSON, `puck.dragRef` for `Space` `inline:true`).
- **Right drawer JSON panel** (420px, collapsible `jsonOpen`) shows `selected` component JSON or full `pageKey+layout` `puck-builder.html:913`, **Copy** + **Show Page**.
- **AppBar**: Merchant input, `Load`, `Page` `Select` `puck-builder.html:1011` (`MenuItem key={k} value={k}` `puck-builder.html:1017` + `Select change` log `puck-builder.html:1015`), `+ New Page`, `Save` `POST /api/puckmui/merchant/pages/...` `puck-builder.html:913`, `Publish` `POST .../publish` `puck-builder.html:914`.
- **CSS**: `https://esm.sh/@measured/puck@0.20.2/puck.css` `puck-builder.html:10` + `unpkg` fallback (removed failing `@puckeditor/core` `500`), `window.addEventListener('error',...)` suppressor.

### 13.3 Styled Renderer Helpers — `static/puck-client.js`

`getStyleSourceStyled`, `buildInlineStyleStyled`, `buildComponentClassStyled`, `buildLayoutStyleStyled` `puck-client.js:1` — now handles `c.style` + `c.advanced.customCssJson` with legacy flat fallback, used by Puck preview.

---

## 14. Styling

- **Tailwind input**: `static/tailwind.in.css:1` — `@layer theme, base, components, utilities`, `@source` for `index.html` + `app.js` + `data/pages/**/*.json` + `data/puckmui/pages/**/*.json` + inline safelist, CSS vars in `:root`, then `@import "tailwindcss/theme.css"` / `utilities.css`.
- **Generated output**: `static/tailwind.css` (gitignored, build via `npm run build:css`).
- **Legacy**: `static/styles.css` — original non-Tailwind stylesheet (kept for reference; not loaded by `index.html` anymore).
- **CSS vars** (`tailwind.in.css:9`, `styles.css:1`): `--bg:#0f1220`, `--panel:#1a1f35`, `--panel-2:#232a45`, `--accent:#6c8cff`, `--accent-2:#3ad29f`, `--danger:#ff6b6b`, `--text:#e6e9f5`, `--muted:#9aa3c7`, `--border:#2c3354`, plus `--black/--white/--success/--primary` for Tailwind `bg-[var(--...)]`.
- **Puck overrides**: `.Puck{--puck-color-background:#0f1220; --puck-color-border:#2c3354}` `puck-builder.html:37`, canvas `bgcolor:#ffffff` `border:2px solid #6c8cff` `minHeight:600` `puck-builder.html:1044` + debug `Puck data: N` + fallback list.

---

## 15. Sample Data & Navigation Flow

### SDUI `data/pages/merchant_1` (5 `PUBLISHED`):

| Key | File | Purpose |
|---|---|---|
| `seat_selection` | `seat_selection.json` | Entry screen: 3 sections (Front/Middle/Rear), seat rows clickable → `SEAT_SELECTION` → `checkout` |
| `checkout` | `checkout.json` | Order summary + email/fullName form → `CHECKOUT` → `checkout_success` |
| `checkout_success` | `checkout_success.json` | `SuccessCard` with `{{fullName}}`/`{{email}}` templating + buttons to Back / Payment |
| `payment` | `payment.json` | `PaymentForm` (CARD/PAYPAL/APPLE_PAY) → `PAYMENT` → `confirmation` |
| `confirmation` | `confirmation.json` | `ConfirmationCard` + navigation back to `seat_selection` |

Navigation diagram:

```
[seat_selection] --(select seat / Continue)--> [checkout] --(Submit)--> [checkout_success]
                                                                     --(Continue to Payment)--> [payment]
                                                                                               --(Pay)--> [confirmation]
                                                                                                            --(Back to Seat Selection)--> [seat_selection]
```

### PuckMUI `data/puckmui/pages/merchant_1` (3 `PUBLISHED`, isolated):

| Key | File | Comps | Purpose |
|---|---|---|---|
| `gallery_100_images` | `gallery_100_images.json` | `102`: `Hero` + `100×Image` (`https://picsum.photos/seed/puckmui001/400/300`…) + `Flex` footer (`Button`+`Text` slot) | Stress-test 100 `Image` flat |
| `gallery_small` | `gallery_small.json` | `8`: `Heading`+`Text`+`5×Image`+`Button` | Small gallery (guaranteed Puck render) |
| `mixed_100` | `mixed_100.json` | `100` mixed: `Heading×6, Text×7, Button×7, Card×7, Hero×7, Grid×6, Flex×6, Space×6, Divider×6, Image×6, SeatGrid×6, OrderSummary×6, Form×6, PaymentForm×6, ConfirmationCard×6, SuccessCard×6` `28501b` | Stress-test all blocks + `style` every 5th |

All PuckMUI via `GET /api/puckmui/merchant/pages/merchant_1` `PuckMuiMerchantController.java:16` and `GET /api/puckmui/client/page/...` `PuckMuiClientController.java:24`.

---

## 16. Guide: Add a New Page / Merchant / Component

### 16.1 New merchant

No registration needed — `merchantId` is a path segment. Just use a new id (e.g. `merchant_2`):

```bash
# SDUI
curl -X POST http://localhost:8080/api/merchant/pages/merchant_2/welcome \
  -H "Content-Type: application/json" \
  -d '{"layout":{"type":"Screen","title":"Welcome","subtitle":"Hello merchant_2","components":[{"type":"Button","label":"Go","actions":{"onClick":{"type":"navigate","target":"seat_selection"}}}]}}'
curl -X POST http://localhost:8080/api/merchant/pages/merchant_2/welcome/publish
# PuckMUI
curl -X POST http://localhost:8080/api/puckmui/merchant/pages/merchant_2/welcome \
  -H "Content-Type: application/json" \
  -d '{"layout":{"type":"Screen","title":"Welcome PuckMUI","components":[{"type":"Heading","text":"Hi"}]}}'
curl -X POST http://localhost:8080/api/puckmui/merchant/pages/merchant_2/welcome/publish
```

Directory `data/pages/merchant_2/` or `data/puckmui/pages/merchant_2/` will be created automatically (`service/JsonFileUtils.java:75`, `service/PuckMuiJsonFileUtils.java:22`).

### 16.2 New page

Via UI: **puck-builder.html** `+ New Page` `puck-builder.html:928` → enter key → drag from palette (categories) → **Save** `puck-builder.html:913` → **Publish** `puck-builder.html:914` (no auto-open on refresh per request — blank `no page selected` `puck-builder.html:1033` until `Page` dropdown pick).

Via API: `POST /api/puckmui/merchant/pages/{mid}/{key}` with `layout` (see §10). Remember to publish for `GET /api/puckmui/client/page/...` visibility.

### 16.3 New component type

1. **Backend**: no change needed if `layout` is just data. For custom server validation, extend `service/PageConfigService.java:112` or `service/PuckMuiPageConfigService.java:111` `handleAction`.
2. **Client renderer**: add a branch in `static/app.js:264` `renderComponent(c)` (check `c.type === "MyType"`), plus in `static/puck-builder.html:391` `puckConfig.components` (add `fields` with Puck `text/select/radio/array/slot/object`, `defaultProps: {style:{}, advanced:{}}`, `render` with `Selectable` + `buildStyleSx`/`getClassName` + `puck.dragRef` if `inline:true`, and category entry).
3. **Tailwind**: if using new utility classes in JSON `class`, ensure `static/tailwind.in.css:3` `@source` covers that file or add to safelist (`@source inline(...)`).

Example minimal component in JSON:

```json
{ "type": "MyBanner", "id": "banner1", "text": "Hello SDUI", "style": {"background":"#f7f8ff","padding":"12px"}, "advanced": {"className":"rounded-lg border text-center"} }
```

---

## 17. Build, Test & Deploy

### Build

```powershell
.\gradlew build          # runs tests, produces build/libs/demo-0.0.1-SNAPSHOT.jar
.\gradlew bootJar
```

The jar includes `src/main/resources/data/pages/**`, `data/puckmui/pages/**` and `static/**`. Pages written at runtime go to `fileSystemBaseDir()` (SDUI `data/pages` or `sdui.pages.dir`, PuckMUI `data/puckmui/pages` or `sdui.puckmui.dir`).

Tailwind must be built before jar if `static/tailwind.css` is needed: `npm run build:css` (copies to `build/resources/main/static/` too).

### Test

```powershell
.\gradlew test           # runs src/test/java/com/sdui/demo/DemoApplicationTests.java:7 contextLoads
```

Single test `contextLoads` verifies Spring context starts (now with dual `PuckMui*` beans). Add MockMvc tests for both `/api/merchant` and `/api/puckmui/merchant` as needed. `GET http://localhost:8080/api/puckmui/merchant/pages/merchant_1` should return `mixed_100` etc.

### Run the jar

```powershell
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
# with external data dirs:
java -Dsdui.pages.dir=C:/sdui-data/pages -Dsdui.puckmui.dir=C:/sdui-data/puckmui/pages -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

### Docker (example)

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

```powershell
docker build -t sdui-demo .
docker run -p 8080:8080 -v C:/sdui-data/pages:/app/data/pages -v C:/sdui-data/puckmui/pages:/app/data/puckmui/pages sdui-demo
```

---

## 18. Troubleshooting & FAQ

| Symptom | Cause / Fix |
|---|---|
| `tailwind.css` 404 | Run `npm run build:css` — file is gitignored and must be generated before first run. Copy to `build/resources/main/static/` for jar. |
| `https://esm.sh/@puckeditor/core@0.20.2/puck.css 500` | `esm.sh` not publishing CSS for new `@puckeditor/core` name — removed `puck-builder.html:10`, keep `https://esm.sh/@measured/puck@0.20.2/puck.css` `puck-builder.html:10` + `unpkg` fallback. Hard-reload `Ctrl+F5`. |
| `Puck` canvas blank but `Load` `200` `pages:['gallery_100_images']` `puck-builder.html:888` | `puck-builder.html` is PuckMUI-only `apiBase="/api/puckmui"` `puck-builder.html:862` — blank until `Page` dropdown pick `puck-builder.html:1011` → `openPage()` `puck-builder.html:908` `setPageKey`+`setPuckData` after `fetch` (fixes off-by-one `key={pageKey}` `puck-builder.html:1046` remount). Check `Console` `openPage response pageKey=...` `puck-builder.html:916` + `Puck data: N` `puck-builder.html:1042` + `Fallback view:` `puck-builder.html:1056`. Try `puck-test.html` `React 18.2` minimal — if that renders but `puck-builder.html` not, `categories/slot` config invalid. |
| `Gallery small` shows `100` / `100` shows `small` (swapped) | Was `PuckMuiPageConfigService.java:37` stale `cache.get()` without re-read + `MenuItem` missing `key` `puck-builder.html:1017` + `openPage` `setPageKey` before `fetch` off-by-one. Fixed `PuckMuiPageConfigService.java:37` to always `readFile()` then `put`, `puck-builder.html:1015` `Select change` log + `MISMATCH` warn `puck-builder.html:913`, `key={k} value={k}`. Restart `bootRun` to clear `ConcurrentHashMap`. |
| `Load` reverts `small` → `100` | Was `loadPages()` `if(!pageKey) auto-open first` `puck-builder.html:897` with stale closure. Now only `setPages()` `puck-builder.html:892` — no auto-switch (per request: refresh shows `no page selected` `puck-builder.html:1033`). |
| Refresh shows `no page selected` | Intended `puck-builder.html:892` — no auto-open on refresh per request. Pick `Page` dropdown. |
| `Failed to load PuckMUI pages: ... is backend running?` `puck-builder.html:889` | Backend not running or wrong `merchantId`. `GET http://localhost:8080/api/puckmui/merchant/pages/merchant_1` `PuckMuiMerchantController.java:16` should `200` `source:puckmui`. Re-run `.\gradlew bootRun`. |
| `No PuckMUI pages for merchant` | `fileSystemBaseDir` `PuckMuiJsonFileUtils.java:19` `data/puckmui/pages/{mid}` empty. Check `src/main/resources/data/puckmui/pages/merchant_1/` exists and `build/resources/main/...` copied. |
| Preview shows `Not published` / 404 on `/api/client/page/...` or `/api/puckmui/client/page/...` | Page status is `DRAFT`. Click **Publish** `puck-builder.html:914` in editor. Check `PuckMuiPageConfigService.java:96` gate. |
| `Failed to list pages` / empty list | `fileSystemBaseDir` may point elsewhere. Check `sdui.pages.dir` / `sdui.puckmui.dir`, or verify `src/main/resources/data/...` exists on classpath. `JsonFileUtils.java:90` creates parent dirs. |
| `seatId is required` on seat select | `SEAT_SELECTION` `data.seatId` missing. Ensure `SeatGrid` `actions.onSelect` is configured and row has `data-seat-id`. See `PageConfigService.java:117` / `PuckMuiPageConfigService.java:116`. |
| `email and fullName are required` on checkout | `CHECKOUT` validation strict (`PageConfigService.java:135`). Fill both fields in the `Form`. |
| `amount and method are required` on payment | `PAYMENT` expects both (`PageConfigService.java:146`). |
| CORS errors when calling from another origin | `CorsConfig.java:16` allows `/api/**` and `/api/puckmui/**` with `allowedOriginPatterns("*")`. Ensure frontend uses same origin or adjust config for `allowCredentials`. |
| Changes not reflected after save | `PuckMuiPageConfigService.java:37` now fresh reads, but SDUI `PageConfigService.java:20` still caches — `savePage`/`publish` evicts correctly, but if you edit files directly on disk, restart or call `clearCache()` (expose an endpoint if needed). Copy edited `src/...` to `build/...` for jar. |
| Many components (100) blank / slow | `app.js:255` `renderScreen` does `O(n)` `innerHTML` string + single `DOM` parse; 100 `Image` `src` will load 100 `picsum` images. `puck-builder.html` `Grid` `items:slot` `puck-builder.html:131` with `Puck` also `O(n)`. Use pagination/virtualization or `Grid` to chunk, or test `gallery_small` (8) vs `mixed_100` (100 mixed) `data/puckmui/pages/...`. |

---

## 19. Roadmap Ideas

- Persist pages in a database (JPA / Mongo) instead of JSON files; keep dual stores as option (already `sdui.pages.dir` / `sdui.puckmui.dir`).
- Add auth/roles: merchant users can only mutate their own `merchantId` (separate for SDUI/PuckMUI).
- Version history / rollback (keep `*.json.bak` pattern but formalize).
- Draft → Review → Published workflow + `ARCHIVED` handling.
- Schema validation for `layout` (JSON Schema) on `POST /api/merchant/pages/...` and `/api/puckmui/merchant/pages/...`.
- WebSocket / SSE to push published updates to connected clients.
- Expand `handleAction` into a pluggable handler registry (already duplicated for PuckMUI).
- Add MockMvc / Testcontainers integration tests for both `/api/merchant` and `/api/puckmui/merchant` + `puck-test.html`.
- Puck `categories` + `slot` + `object` style grouping already done — add `MUI` `ThemeProvider` per merchant.

---

## References

- Spring Boot 4.1.1: https://docs.spring.io/spring-boot/4.1.1/
- Gradle: https://docs.gradle.org
- Tailwind CSS 4: https://tailwindcss.com
- Puck: https://puckeditor.com / https://github.com/puckeditor/puck (`apps/demo/config/blocks/*`, `categories`, `slot`, `object` fields, `withLayout`)
- MUI 5: https://mui.com

*Generated for `demo` at `C:\sdui\demo` — covers `build.gradle:1`, `src/main/java/**`, `src/main/resources/**` (dual `data/pages` + `data/puckmui/pages`), `package.json:1`, `puck-builder.html:10` (PuckMUI-only, `React 19` + `React 18.2` test).*
