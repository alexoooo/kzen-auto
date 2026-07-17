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

## File safety

`kzen-auto-jvm/src/main/resources/notation/main/` (and any `notation/main/` a run writes into) holds the **user's own working documents and run artifacts** — Scripts, Reports, screenshots — frequently `git add`ed but not yet committed. **Never delete, move, or overwrite files there (or anywhere) that aren't part of your task, even when uncommitted, untracked, or gitignored.** Scope deletions to paths you created this session or to `build/` output; if a file seems in the way, surface it and ask. See the umbrella [`AGENTS.md`](../AGENTS.md) "File safety" rule.

## Git hygiene

**Stage every new file you create as soon as it's written** — `git add <explicit path>`, **stage only, never commit** unless the user asks. New source/test/notation/doc files otherwise linger as untracked `??` entries that are easy to overlook (edited tracked files already show in the diff, so they need no action). Note that changes here often span **two repos** (kzen-auto + the sibling kzen-lib it consumes from mavenLocal), so `git status` and stage in each. Never `git add -A` / `git add .` / `git add <dir>` — a blanket add sweeps up the user's unrelated WIP and untracked working documents (see *File safety*). Full rule: umbrella [`../kzen/AGENTS.md`](../kzen/AGENTS.md) "Stage new files you create".

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

Single-command alternative (no IDE run config; rebuilds the JS bundle first, then runs the server):

```powershell
./gradlew :kzen-auto-jvm:frontendDevelopment -PjsWatch
```

`-PjsWatch` bundles the unminified dev executable (symbols preserved, faster); omit it for the minified production bundle. This is the deterministic "always get the latest UI" action — `classes` → `processResources` → `jsEsbuildBundle` rebuilds the bundle before binding, and FrontendDevelopment's JS route sends `Cache-Control: no-store` so a single browser refresh shows it. See `FrontendDevelopment.kt`.

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
- **kotlin-wrappers is at `2026.7.1`** (declared in `settings.gradle.kts`; bumped 2026-07-07 to `2026.7.1` alongside the JVM 26 / Gradle 9.6.1 update, previously 2026-06-21 to `2026.6.5` alongside the Kotlin 2.4.0 toolchain update; originally migrated off `kotlin-react-legacy` on 2026-05-11, in lockstep with kzen-launcher). The migration applied the kzen-launcher `wrap/React.kt` template verbatim. Three pieces of scaffolding in this `wrap/React.kt` now carry what `kotlin-react-legacy` used to provide: (1) `RPureComponent` re-implemented as `Component` + `shouldComponentUpdate` with shallow-compare props/state, because `react.PureComponent` was removed; (2) a `KClass<out Component<P, *>>.react: ComponentType<P>` extension that all 200+ `::class.react` call sites depend on; (3) a `createRef<T>()` top-level bridge, because the original `react.createRef` is gone and `useRef` is a hook (incompatible with class components). The actual migration surface was much larger than the prior inventory suggested — ~30 `key = String` sites (was documented as "2 files"), 60 files that needed an explicit `import tech.kzen.auto.client.wrap.setState` (was implicit before), 73 files needed `import react.react` swapped to `import tech.kzen.auto.client.wrap.react`. `ChangeEvent<HTMLInputElement, *>` requires `.currentTarget.checked` (not `.target.checked`) — the second type arg controls `target`, the first controls `currentTarget`. `useCommonJs()` is load-bearing: `useEsModules()` breaks `@mui/icons-material` (CommonJS-packaged — `createSvgIcon` has no ESM `default` export); confirmed in both kzen-launcher and kzen-auto.
- **`FormulaStep` type inference reads the compiler's inferred `KType` via kotlin-reflect.** `FormulaStep.definition()` (`kzen-auto-jvm/src/main/kotlin/tech/kzen/auto/server/objects/script/step/eval/FormulaStep.kt`) compiles the user's expression once as an inference class (`StepExpressionCompiler.generateInferenceCode` emits a `probe()` member with no declared return type, and `evaluate` delegating to it — one content signature serves both validation and execution) and `StepReturnTypeInference` reflects `probe`'s return `KType`, mapping it to `TypeMetadata` behind a registry-visibility filter (builtin whitelist ∪ `objectRegistryScan.classNames`, by full `ClassName`; outside → `Any`, nullability preserved). No diagnostic-text parsing. `FormulaStepTest` (`.../step/eval/FormulaStepTest.kt`) is still the canary — a Kotlin inference change surfaces here as a *wrong inferred type*, not a hard build failure. **Load-bearing coupling: `ScriptKotlinCompiler` must stay `open`.** The scripting compiler emits every generated class as a nested member of a script facade `class __ extends ScriptKotlinCompiler`; the kotlin-reflect call resolves and *loads* `__`, so a `final` base throws `IncompatibleClassChangeError: class __ cannot inherit from final class ScriptKotlinCompiler`. (This only bites on a **cold** compile — formula jars persist content-addressed under `<workdir>/code-cache`, so a warm cache masks it. Clear that cache when changing `StepExpressionCompiler`'s generated shape.) The Report calculated-column path (`CalculatedColumnEval`) uses only explicit return types, so it never reflects the generated class and is unaffected.
- **`@Reflect` / KSP runs over `src/main` only.** Each module runs the `kzen-lib-reflect-ksp` processor on its main source set under a single global `kzen.reflect.moduleClassName`; there is **no `kspTest`** (a second module under that name would collide). So any class the graph instantiates by reflection — a `Logic`, an `AttributeDefiner`/`AttributeCreator`, a `DocumentController`, or a notation-instantiated **test fixture** — must live in `src/main` to be registered. Tests reference it from `src/test` and drive it through the real notation→graph path; this is why Flow's example vertices and Job's synthetic workers live in `src/main`, not `src/test`.
- **A Worker's progress is an opaque `Map<String, Any?>` on both sides — keep it that way.** Server-side, `WorkerBase.progress(snapshot): Map<String, Any?>?` lets each Worker emit its own arbitrary keys, and `JobControl.publishProgress` / the trace transport never interpret them. The client mirror `JobWorkerProgress` (`kzen-auto-js/.../objects/document/job/JobWorkerProgress.kt`) must stay schema-agnostic: only `status` + the raw `progressMap` + **generic key-parameterized** helpers (`longValue(key)`), never a Worker-specific field. Each per-type `WorkerDisplay` parses its own keys out of `progressMap` (`PreviewWorkerDisplay` → `parseHeader`/`parseRows` on `"header"`/`"rows"`; `SummaryWorkerDisplay` → `parseSummary` on `"summary"`; the default card renders only `status` + generic scalar entries). Display **selection** is likewise notation-driven — the Worker's `display:` marker resolved by `WorkerDisplayManager` against an autowired `List<WorkerDisplayWrapper>`, never a `when` on Worker type (the same pattern as Script steps' `StepDisplayManager`). **Adding a Worker-specific field (`header`/`rows`/`tableSummary`/…) to `JobWorkerProgress`, or a Worker-type branch to `JobController` / `JobObjectSlot` / `WorkerDisplayManager`, is the "god object" anti-pattern — it recouples general code to specific Workers and breaks the point of kzen (3rd-party extension with no kzen-source edit).** A 3rd-party Worker must be fully expressible via `@Reflect` + an `is: Worker` archetype + an `is: WorkerDisplay` card, with zero edits to shared code.
- **Job notation archetypes have two silent failure modes.** (1) **Never add `title:` (or any attribute needing an inherited definer) to the `Channel` / `DuplexChannel` archetypes** (`job-jvm.yaml`) — they have no `is:` parent, so an attribute that can't self-define fails the whole object's definition and silently **drops every channel** in the graph. (2) **Palette-inserted workers need empty-string body defaults for channel-ref attributes** — a ribbon insert creates `is: <Worker>` only; without the archetype's `port: ""` body default the port attribute is *missing* rather than *blank*, and synthesis/derivation treat those differently. (Both bit during the original Job build; also recorded in the Job plan's appendix.)
- **`logs/` and `work/` are runtime output dirs** under this root; they're `.gitignore`d. Logs from `KzenAutoMain` and dev mains land there.

## Pointers

- **kzen-auto-specific architecture** → [`docs/architecture.md`](docs/architecture.md) (paradigms, graph sync, REST surface, server composition root, report execution, plugin SPI, module registration).
- **JS client architecture** → [`docs/js-architecture.md`](docs/js-architecture.md) (Controller / Store / State / Observer patterns, document folder convention, React DSL wrapper).
- **Foundational concepts (kzen-lib)** → [`../kzen-lib/docs/architecture.md`](../kzen-lib/docs/architecture.md).
- **Composite build + toolchain rules** → [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **Plugin example** → `../kzen-sample-plugin/`.
- **Downstream consumer** → `../kzen-project/` (built on top of kzen-auto, not just kzen-lib).
