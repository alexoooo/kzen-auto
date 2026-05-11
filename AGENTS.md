# kzen-auto — AI agent guide

## Purpose

kzen-auto is a **robotic process automation / office-automation framework** built on top of kzen-lib. It hosts the user-facing reporting and pipeline UI (Kotlin/JS + React) backed by a Ktor JVM server, and exposes a public plugin SPI (`kzen-auto-plugin`) that third-party automation modules compile against.

Read [`../kzen-lib/docs/architecture.md`](../kzen-lib/docs/architecture.md) first for foundational kzen-lib concepts; then [`docs/architecture.md`](docs/architecture.md) for kzen-auto-specific patterns (paradigm system, client-server graph sync, Disruptor-based report pipeline, plugin SPI).

## Module layout

Four Gradle subprojects:

- **`kzen-auto-common`** — Kotlin Multiplatform shared code (`commonMain`/`jvmMain`/`jsMain`/`commonTest`). Models, paradigms (e.g. reporting), and shared services consumed by both client and server.
- **`kzen-auto-jvm`** — Ktor/Netty server. Hosts the backend, serves the JS bundle, owns server-side execution of automation tasks.
- **`kzen-auto-js`** — Kotlin/JS browser frontend. React + kotlin-wrappers DSL.
- **`kzen-auto-plugin`** — **the public SPI**. Downstream plugins (e.g. `../kzen-sample-plugin`) compile against this and only this. Pure JVM. Treat its API surface as a stable contract.

## Entry points

| Class | Module | Purpose |
|----|----|----|
| `tech.kzen.auto.server.KzenAutoMain` | kzen-auto-jvm | Production server entry point. Run via `./gradlew jar` → `java -jar kzen-auto-jvm/build/libs/kzen-auto-jvm-*.jar`. |
| `tech.kzen.auto.server.dev.BackendDevelopment` | kzen-auto-jvm | IDE-launched dev backend; pairs with continuous JVM compile. |
| `tech.kzen.auto.server.dev.FrontendDevelopment` | kzen-auto-jvm | IDE-launched dev backend wired for the live-rebuild JS bundle. |
| `tech.kzen.auto.client.Main` | kzen-auto-js | JS entry point; bundled by webpack. |

## Dev loop

Two-terminal pattern. **Open kzen-auto as its OWN IntelliJ project**, not via the umbrella — the umbrella's `includeBuild` of kzen-lib breaks IDE run/debug of KMP-consuming JVM mains (Provided-scope bug; see umbrella AGENTS.md).

```powershell
# Backend dev:
# Terminal 1 — IDE: run tech.kzen.auto.server.dev.BackendDevelopment
# Terminal 2:
./gradlew -t :kzen-auto-jvm:classes

# Frontend dev:
# Terminal 1 — IDE: run tech.kzen.auto.server.dev.FrontendDevelopment
# Terminal 2:
./gradlew -t :kzen-auto-js:build -x test -PjsWatch
```

## Key directories

**`kzen-auto-common`** (`src/commonMain/kotlin/tech/kzen/auto/common/`):

| Path | What lives here |
|----|----|
| `api/` | Shared SPI types between client and server |
| `paradigm/` | Domain "paradigms" — reactive reporting, task execution patterns, etc. |
| `objects/` | Notation objects (definers/creators registered against kzen-lib SPI) |
| `service/` | Cross-cutting services usable from common code |

**`kzen-auto-jvm`** (`src/main/kotlin/tech/kzen/auto/server/`):

| Path | What lives here |
|----|----|
| `KzenAutoMain.kt` | Server entry point |
| `dev/` | `BackendDevelopment`, `FrontendDevelopment` |
| `backend/` | Ktor routes, backend services |
| `context/` | Server-side graph context wiring |
| `paradigm/` | Server-side paradigm implementations |
| `service/` | Server services (execution, persistence) |

**`kzen-auto-js`** (`src/jsMain/kotlin/tech/kzen/auto/client/`):

| Path | What lives here |
|----|----|
| `Main.kt` | JS entry point |
| `objects/document/` | React UIs per document type (e.g. `report/`) |
| `wrap/` | kotlin-wrappers / React DSL glue |
| `service/` | Client services (graph store, mirror, etc.) |

**`kzen-auto-plugin`** (`src/main/kotlin/tech/kzen/auto/plugin/`):

| Path | What lives here |
|----|----|
| `api/` | Public SPI — entry points for plugins |
| `definition/`, `spec/`, `model/` | Public data types |
| `helper/` | Helpers downstream plugins can use |

## Gotchas

- **Plugin publish order.** `kzen-auto-plugin` must be `:publishToMavenLocal`'d before any non-composite consumer (third-party plugins compiled outside the umbrella, *and* any standalone build of `../kzen-project`) can pick up new bytecode. Inside the umbrella Gradle substitutes the artifact so the published copy can lag, but the moment you leave the umbrella you need a fresh publish. Run after any change to `kzen-auto-plugin`:
  ```powershell
  ./gradlew :kzen-auto-plugin:publishToMavenLocal
  ```
- **Composite NPM coordination broken under umbrella.** Running `:kzen-auto:kotlinNpmInstall` from the umbrella fails because kzen-auto's `settings.gradle.kts` deliberately doesn't `includeBuild("../kzen-lib")` (would break IDE run/debug). Workaround: `cd ../kzen-auto && ./gradlew kotlinNpmInstall`.
- **kotlin-wrappers ceiling is `2025.12.11`.** Bumping to `2026.2.x` requires a coordinated React DSL touch-up: wrappers `2026.2.20` removes `kotlin-react-legacy` (`RBuilder`/`RClass`/`RComponent`), and `2026.2.11` changes the React `key` attribute type and `ChangeEvent` signature. Many jsMain files use these. Don't bump the catalog without planning the source migration.
- **`logs/` and `work/` are runtime output dirs** under this root; they're `.gitignore`d. Logs from `KzenAutoMain` and dev mains land there.

## Pointers

- **kzen-auto-specific architecture** → [`docs/architecture.md`](docs/architecture.md) (paradigms, graph sync, report execution, plugin SPI).
- **Foundational concepts (kzen-lib)** → [`../kzen-lib/docs/architecture.md`](../kzen-lib/docs/architecture.md).
- **Composite build + toolchain rules** → [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **Plugin example** → `../kzen-sample-plugin/`.
- **Downstream consumer** → `../kzen-project/` (built on top of kzen-auto, not just kzen-lib).
