# kzen-auto — AI agent guide

## Purpose

kzen-auto is a **robotic process automation / office-automation framework** built on top of kzen-lib. It hosts the user-facing reporting and pipeline UI (Kotlin/JS + React) backed by a Ktor JVM server, and exposes a public plugin SPI (`kzen-auto-plugin`) that third-party automation modules compile against.

Read [`../kzen-lib/docs/architecture.md`](../kzen-lib/docs/architecture.md) first for foundational kzen-lib concepts; then [`docs/architecture.md`](docs/architecture.md) for kzen-auto-specific patterns (paradigm system, client-server graph sync, Disruptor-based report pipeline, plugin SPI).

## Module layout

Five Gradle subprojects:

- **`kzen-auto-common`** — Kotlin Multiplatform shared code (`commonMain`/`jvmMain`/`jsMain`/`commonTest`). Models, paradigms (e.g. reporting), and shared services consumed by both client and server.
- **`kzen-auto-jvm`** — Ktor/Netty server. Hosts the backend, serves the JS bundle, owns server-side execution of automation tasks.
- **`kzen-auto-js`** — Kotlin/JS browser frontend. React + kotlin-wrappers DSL.
- **`kzen-auto-plugin`** — **the public SPI**. Downstream plugins (e.g. `../kzen-sample-plugin`) compile against this and only this. Pure JVM. Treat its API surface as a stable contract.
- **`kzen-auto-test`** — blackbox end-to-end self-test harness. JVM-only; spawns two kzen-auto JVMs (tester + SUT) and drives them through Chrome via the Script feature. Opt-in `selfTest` task, NOT bound to `check`. See [`kzen-auto-test/AGENTS.md`](kzen-auto-test/AGENTS.md).

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

End-to-end self-test (opt-in, opens Chrome, spawns two kzen-auto JVMs):

```powershell
./gradlew :kzen-auto-test:selfTest
```

See [`kzen-auto-test/AGENTS.md`](kzen-auto-test/AGENTS.md) for harness details, port pinning, and adding tests.

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
| `objects/document/` | React UIs per document type (e.g. `report/`). `custom/` is the raw-YAML editor for `CustomDocument` — saves via `SetDocumentObjectsCommand` (bulk-replace), no archetype/schema enforcement; see [`docs/architecture.md` § 6](docs/architecture.md#6-document-types-in-the-ui). |
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
- **kotlin-wrappers is at `2026.5.3`** (bumped 2026-05-11, in lockstep with kzen-launcher). The migration applied the kzen-launcher `wrap/React.kt` template verbatim. Three pieces of scaffolding in this `wrap/React.kt` now carry what `kotlin-react-legacy` used to provide: (1) `RPureComponent` re-implemented as `Component` + `shouldComponentUpdate` with shallow-compare props/state, because `react.PureComponent` was removed; (2) a `KClass<out Component<P, *>>.react: ComponentType<P>` extension that all 200+ `::class.react` call sites depend on; (3) a `createRef<T>()` top-level bridge, because the original `react.createRef` is gone and `useRef` is a hook (incompatible with class components). The actual migration surface was much larger than the prior inventory suggested — ~30 `key = String` sites (was documented as "2 files"), 60 files that needed an explicit `import tech.kzen.auto.client.wrap.setState` (was implicit before), 73 files needed `import react.react` swapped to `import tech.kzen.auto.client.wrap.react`. `ChangeEvent<HTMLInputElement, *>` requires `.currentTarget.checked` (not `.target.checked`) — the second type arg controls `target`, the first controls `currentTarget`. `useCommonJs()` is load-bearing: `useEsModules()` breaks `@mui/icons-material@7.3.11` (CommonJS-packaged — `createSvgIcon` has no ESM `default` export); confirmed in both kzen-launcher and kzen-auto.
- **`FormulaStep` type inference is coupled to Kotlin compiler diagnostic text.** `FormulaStep.definition()` (`kzen-auto-jvm/src/main/kotlin/tech/kzen/auto/server/objects/script/step/eval/FormulaStep.kt`) compiles the user's expression targeting `String`, then on the resulting compile error parses verbatim prefixes — `"actual '"`, `"The " + " literal "`, `"IntegerLiteralType["` — to recover the inferred type. These constants track Kotlin's internal diagnostic format, not a stable API. A Kotlin toolchain bump that reshapes diagnostic wording will silently regress inference (often to the wrong type, not a hard failure). `FormulaStepTest` (`kzen-auto-jvm/src/test/kotlin/tech/kzen/auto/server/objects/script/step/eval/FormulaStepTest.kt`) is the canary — if it fails after a Kotlin bump, update `parseInferredType` / `parseLiteralType` / `parseTypeMetadata` to match the new diagnostic format, don't just relax the assertion.
- **`logs/` and `work/` are runtime output dirs** under this root; they're `.gitignore`d. Logs from `KzenAutoMain` and dev mains land there.

## Pointers

- **kzen-auto-specific architecture** → [`docs/architecture.md`](docs/architecture.md) (paradigms, graph sync, REST surface, server composition root, report execution, plugin SPI, module registration).
- **JS client architecture** → [`docs/js-architecture.md`](docs/js-architecture.md) (Controller / Store / State / Observer patterns, document folder convention, React DSL wrapper).
- **Foundational concepts (kzen-lib)** → [`../kzen-lib/docs/architecture.md`](../kzen-lib/docs/architecture.md).
- **Composite build + toolchain rules** → [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **Plugin example** → `../kzen-sample-plugin/`.
- **Downstream consumer** → `../kzen-project/` (built on top of kzen-auto, not just kzen-lib).
