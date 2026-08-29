# kzen-auto — AI agent guide

## Purpose

kzen-auto is a **robotic process automation / office-automation framework** built on top of kzen-lib. It hosts the user-facing reporting and pipeline UI (Kotlin/JS + React) backed by a Ktor JVM server, and exposes a public plugin SPI (`kzen-auto-plugin`) that third-party automation modules compile against.

Read [`../kzen-lib/docs/architecture.md`](../kzen-lib/docs/architecture.md) first for foundational kzen-lib concepts; then [`docs/architecture.md`](docs/architecture.md) for kzen-auto-specific patterns (paradigm system, client-server graph sync, Disruptor-based report pipeline, plugin SPI).

## Module layout

Five Gradle subprojects:

- **`kzen-auto-common`** — Kotlin Multiplatform shared code (`commonMain`/`jvmMain`/`jsMain`/`commonTest`). Models, paradigms (e.g. reporting), and shared services consumed by both client and server.
- **`kzen-auto-jvm`** — Ktor/Netty server. Hosts the backend, serves the JS bundle, owns server-side execution of automation tasks.
- **`kzen-auto-js`** — Kotlin/JS browser frontend. React + kotlin-wrappers DSL.
- **`kzen-auto-plugin`** — the public, **in-development SPI**, versioned with the coordinated release train. Downstream plugins (e.g. `../kzen-sample-plugin`) compile against this and only this. It is JVM-only and deliberately exposes kzen-lib's value contract; evolve it through measured migrations and rebuild every known consumer.
- **`kzen-auto-test`** — blackbox end-to-end self-test harness. JVM-only; spawns two kzen-auto JVMs (tester + SUT) and drives them through Chrome via the Script feature. See [`kzen-auto-test/AGENTS.md`](kzen-auto-test/AGENTS.md).

## File safety & git hygiene

`kzen-auto-jvm/src/main/resources/notation/main/` (and any `notation/main/` a run writes into) holds the **user's own working documents and run artifacts** — Scripts, Reports, screenshots — frequently `git add`ed but not yet committed; never touch files there that aren't part of your task. Changes here often span **two repos** (kzen-auto + the kzen-lib it consumes from mavenLocal), so `git status` and stage in each. Full rules: umbrella [`../kzen/AGENTS.md`](../kzen/AGENTS.md) "File safety" and "Stage new files you create".

## Entry points

| Class | Module | Purpose |
|----|----|----|
| `tech.kzen.auto.server.KzenAutoMain` | kzen-auto-jvm | Production server entry point. Run via `./gradlew jar` → `java -jar kzen-auto-jvm/build/libs/kzen-auto-jvm-*.jar`. |
| `tech.kzen.auto.server.dev.BackendDevelopment` | kzen-auto-jvm | IDE-launched dev backend; pairs with continuous JVM compile. |
| `tech.kzen.auto.server.dev.FrontendDevelopment` | kzen-auto-jvm | IDE-launched dev backend wired for the live-rebuild JS bundle. |
| `tech.kzen.auto.client.Main` | kzen-auto-js | JS entry point; bundled by esbuild (`jsEsbuildBundle`). |

## Dev loop

Two-terminal pattern. Open kzen-auto as its own IntelliJ project for run/debug — not via the umbrella (Provided-scope bug; see [`../kzen/AGENTS.md`](../kzen/AGENTS.md) Working policy).

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

End-to-end self-test (opt-in, opens Chrome, spawns two kzen-auto JVMs): `./gradlew :kzen-auto-test:selfTest`. See [`kzen-auto-test/AGENTS.md`](kzen-auto-test/AGENTS.md) for harness details, port pinning, and adding tests. Pure-logic behaviour gets a fast `kzen-auto-jvm/src/test` unit test instead — add a self-test only when explicitly asked. In-process Script/step execution is testable via `KzenAutoContext.forTest()` + a `notation/test/**/*.yaml` fixture through `AutoTestUtils.readNotation()` / `graphDefinitionAttempt(...)`, constructing `ScriptExecution` directly (`ScriptExecutionPauseOnErrorTest` is the pattern).

Fixtures are grouped by document archetype, then by the aspect under test: `test/script/{context,control,engine,loop,navigation,resource,result,structure}/`, `test/job/{channel,message,migration,report,run,signature,synth}/`, plus `test/flow/` and `test/custom/`. The scan is recursive and no manifest lists them, so a new fixture only has to land in the right folder. Cross-document references are absolute from `notation/` — `instructions: "test/script/engine/nested-depth-test-2.yaml#main"` — so moving a fixture means rewriting its referrers; bare-name archetype references (`is: ShoutStep`) are folder-independent.

## Headless verification

The user's own dev servers are usually running — kzen-auto on `127.0.0.1:8080` (`BackendDevelopment`) and often an interactive tester on `18081`. Never kill or reuse them (see the umbrella [`../kzen/AGENTS.md`](../kzen/AGENTS.md) rule); boot your own instance on a spare port, and before stopping any JVM verify its `CommandLine` contains the `--server.port=` you chose. Run built jars with an explicit Java 26 (`~/.jdks/temurin-26.0.2` — PATH `java` is often 8).

- **Server behaviour (validation, detached actions):** there is no `installDist`; print the runtime classpath with a throwaway Gradle init script (`--no-configuration-cache`; look the project up via `gradle.rootProject.allprojects.find { it.name == 'kzen-auto-jvm' }` — plain `findProject` returns null because included builds are evaluated too), then boot `tech.kzen.auto.server.KzenAutoMainKt --server.port=8099`. Detached actions are drivable by GET with no UI: `curl -s -G http://127.0.0.1:8099/action/detached --data-urlencode "path=auto-jvm/script/script-jvm.yaml" --data-urlencode "object=ScriptValidator" --data-urlencode "host=main/X.yaml"` (Job equivalent: `auto-jvm/job/job-jvm.yaml` + `JobValidator`). Looping over `main/*.yaml` and diffing error counts against a pre-change baseline is a cheap regression sweep.
- **Client-graph boot check** (blank UI, every first interaction failing): suspect the client graph dying at boot, not UI timing. Copy `kzen-auto-test/fixtures/empty-project` to a scratch dir, run the fat jar there on a spare port, then headless Chrome: `chrome.exe --headless=new --disable-gpu --user-data-dir=<fresh> --enable-logging=stderr --virtual-time-budget=15000 --dump-dom http://127.0.0.1:<port>/index.html > dom.html 2> console.log` (use git-bash redirection — PowerShell `&` redirection captures 0 bytes). A dead client shows the error both in the `#root` div text and on a `CONSOLE` stderr line. The client graph is built in browser JS — JVM tests cannot catch its notation/registration mismatches.

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
| `objects/document/` | React UIs per document type (e.g. `report/`). `custom/` is the hybrid structured + raw-YAML editor for `CustomDocument` — saves via `SetDocumentObjectsCommand` (bulk-replace), no archetype/schema enforcement; see [`docs/architecture.md` § 7](docs/architecture.md#7-document-types-in-the-ui). |
| `wrap/` | kotlin-wrappers / React DSL glue |
| `service/` | Client services (graph store, mirror, etc.) |

**`kzen-auto-plugin`** (`src/main/kotlin/tech/kzen/auto/plugin/`):

| Path | What lives here |
|----|----|
| `api/` | Public SPI — entry points for plugins |
| `definition/`, `spec/`, `model/` | Public data types |
| `helper/` | Helpers downstream plugins can use |

## Gotchas

- **Plugin publish order.** `kzen-auto-plugin` must be `:publishToMavenLocal`'d before any non-composite consumer (third-party plugins, standalone builds of `../kzen-project`) picks up new bytecode — see [`../kzen/AGENTS.md`](../kzen/AGENTS.md) Toolchain bumps.
- **Plugin value-contract dependency is intentional.** `kzen-auto-plugin` has an API dependency on `kzen-lib-common-jvm` so `FlatFileRecord` can be a `ValueAccess` without a per-row forwarding object. The measured compile-classpath delta is coroutines core plus serialization JSON/core (and annotations 13→23); runtime additionally adds kotlin-reflect, dexx collections, Guava and Guava's small support artifacts. Plugin loaders are parent-first from `ClassLoaderUtils.dynamicParentClassLoader()`, and `PluginValueContractClassLoaderTest` pins that guest code resolves the exact host-loaded `ValueAccess` identity.
- **Composite NPM coordination broken under umbrella** — run `kotlinNpmInstall` from this directory, not the umbrella; see [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **kotlin-wrappers migration scaffolding.** The wrappers catalog version and the load-bearing `useCommonJs()`/mui-icons constraints are documented in [`../kzen/AGENTS.md`](../kzen/AGENTS.md) Toolchain pins; the `wrap/React.kt` template (`RPureComponent`, the `::class.react` extension, `createRef`) and the 2026.x breakage catalogue are in [`docs/js-architecture.md`](docs/js-architecture.md) § React DSL wrapper layer. kzen-launcher's `wrap/React.kt` mirrors this one.
- **`buildSrc` is compiled by Gradle's *embedded* Kotlin, not the project's.** A `kotlin-dsl` module (here `buildSrc`, holding only `Dependencies.kt`) uses the Kotlin bundled inside the Gradle distribution — 2.3.21 in Gradle 9.6.1 — which maxes out at JVM target 25, while the app targets 26. `buildSrc/build.gradle.kts` therefore pins *only* buildSrc's `JavaCompile`/`KotlinCompile` to 25 (`sourceCompatibility`/`targetCompatibility = "25"`, `jvmTarget = JvmTarget.JVM_25`); the app stays on 26. Remove that pin once a future Gradle bundles a Kotlin that supports JVM 26. Corollary: buildSrc requires the Gradle JVM to be ≥ 25 — a daemon on Java 21 fails to resolve `:buildSrc` (`Dependency requires at least JVM runtime version 25`); build with the JDK-26 toolchain (IntelliJ Gradle JVM, or `JAVA_HOME`/`-Dorg.gradle.java.home` → `C:\Users\ostro\.jdks\temurin-26.0.2`).
- **`FormulaStep` type inference reads the compiler's inferred `KType` via kotlin-reflect.** `FormulaStep.definition()` (`kzen-auto-jvm/src/main/kotlin/tech/kzen/auto/server/objects/script/step/eval/FormulaStep.kt`) compiles the user's expression once as an inference class (`StepExpressionCompiler.generateInferenceCode` emits a `probe()` member with no declared return type, and `evaluate` delegating to it — one content signature serves both validation and execution) and `ExpressionReturnTypeInference` reflects `probe`'s return `KType`, mapping public, qualified, non-synthetic classifiers to `TypeMetadata`; unnameable classifiers approximate to `Any` with nullability preserved. No diagnostic-text parsing. `FormulaStepTest` (`.../step/eval/FormulaStepTest.kt`) is the canary — a Kotlin inference change surfaces here as a *wrong inferred type*, not a hard build failure. **Load-bearing coupling: `ScriptKotlinCompiler` must stay `open`.** The scripting compiler emits every generated class as a nested member of a script facade `class __ extends ScriptKotlinCompiler`; the kotlin-reflect call resolves and *loads* `__`, so a `final` base throws `IncompatibleClassChangeError`. (This only bites on a **cold** compile — formula jars persist content-addressed under `<workdir>/code-cache`, so a warm cache masks it. Clear that cache when changing `StepExpressionCompiler`'s generated shape.) The Report calculated-column path (`CalculatedColumnEval`) uses only explicit return types, so it never reflects the generated class and is unaffected.
- **`@Reflect` / KSP runs over `src/main` only, and the test pass is disabled on purpose.** Each module runs the `kzen-lib-reflect-ksp` processor on its main source set under a single module-global `kzen.reflect.moduleClassName`. A test-source pass emits a **second** `ModuleReflection` under that same FQN, and test output precedes the main classes on the test runtime classpath — so it shadows the real module and silently drops every production registration. `kzen-auto-jvm/build.gradle.kts` disables `kspTestKotlin` for exactly this reason; don't re-enable it, and don't add `@Reflect` to a test class in a module whose test pass is still live. The symptom is a *green* suite in which the graph resolved production classes reflectively — grep the test output for `Serving … by JVM reflection`: only test fixtures may appear there.
- **Notation-instantiated test fixtures live in `src/test` and are served by the JVM reflective mirror.** A `@Reflect` fixture with no generated registration is instantiated at runtime by kzen-lib-jvm's `ReflectiveClassMirror`, appended to the `GlobalMirror` chain by `KzenAutoContext.init` and by `AutoTestUtils` — so no hand-written `ModuleReflection` is needed. `ScriptStepTestModule` (`kzen-auto-jvm/src/test/.../exec/script/test/`) is the deliberate exception, kept as the pinned proof that `ModuleReflection` is a trivially hand-implementable third-party contract. Production classes still belong in `src/main`: anything a bundled `src/main/resources/notation` document references must be on the production classpath, and the mirror is a JVM-only net — **JS has no runtime reflection**, so a class the client instantiates needs a generated registration, full stop.
- **A Worker's progress is an opaque `Map<String, Any?>` on both sides — keep it that way** (CC-17 in [`../kzen/docs/CODING_STANDARDS.md`](../kzen/docs/CODING_STANDARDS.md)). The client mirror `JobWorkerProgress` stays schema-agnostic; each per-type `WorkerDisplay` parses its own keys out of `progressMap`, and display selection is notation-driven (the Worker's `display:` marker resolved by `WorkerDisplayManager` against an autowired `List<WorkerDisplayWrapper>`), never a `when` on Worker type. A 3rd-party Worker must be fully expressible via `@Reflect` + an `is: Worker` archetype + an `is: WorkerDisplay` card, with zero edits to shared code. Flow vertices honour the same contract: `FlowRun` / `FlowLogicCompiler` dispatch on the capability interfaces in `kzen-auto-common/.../paradigm/flow/api/`, never on concrete vertex classes — `FlowCapabilityTest` is the acceptance pin.
- **Job notation archetypes have two silent failure modes.** (1) Never add `title:` (or any attribute needing an inherited definer) to the `Channel` / `DuplexChannel` archetypes (`job-jvm.yaml`) — they have no `is:` parent, so an attribute that can't self-define fails the whole object's definition and silently drops every channel in the graph. (2) Palette-inserted workers need empty-string body defaults for channel-ref attributes — a ribbon insert creates `is: <Worker>` only; without the archetype's `port: ""` body default the port attribute is *missing* rather than *blank*, and synthesis/derivation treat those differently.
- **`AutoConventions.serverAllowed` excludes `test/` notation.** `ModelDetachedExecutor` / `ModelTaskRepository` apply the policy filter (`kzen/`, `auto-common/`, `auto-jvm/`, `main/`) before instantiating anything, so a `DetachedAction` / `ManagedTask` fixture under `src/test/resources/notation/test/` can be instantiated directly in a unit test (pass the unfiltered definition) but returns "Not found" through the executors. An e2e test *through* an executor must drive a production action in an allowed nesting (`ScriptConventions.scriptValidatorLocation` is the worked example — its own closure is satisfiable in `KzenAutoContext.forTest()`); don't "fix" this by widening `serverAllowed`.
- **`AutoTestUtils.readNotation()` scans the on-disk notation tree**, not just the classpath — so a live dev backend writing notation (the user's `:8080` instance committing an edit) can throw a transient `IllegalArgumentException` from an unrelated test mid-build. If the failing frame is the `readNotation()` call itself, re-run the single test in isolation; if it passes, it was this race. Don't kill the dev server (see Headless verification).
- **Publish all of kzen-auto, not a subset.** The aggregate `./gradlew publishToMavenLocal` works and is the right release-chain command — `kzen-project-jvm` depends on `tech.kzen.auto:kzen-auto-jvm`, so publishing only `-common` + `-plugin` breaks a standalone kzen-project build. Related rule: any task writing into kzen-auto-jvm's generated-resources srcDir (`copyIconCollection`, `generateBuildInfo`) needs a matching `sourcesJar.dependsOn(...)` — `allSource` pulls that srcDir into the sources jar, and the missing dependency only trips on the publish path, never on `build`.
- **`notation/main/**` is excluded from test scans — the FizzBuzz Flow Loop is the Flow-compat oracle.** `AutoTestUtils.readNotation` excludes `main/` (`AutoConventions.autoMainDocumentNesting`), so no JVM test ever compiles or runs the real user-facing documents; a green sweep proves nothing about them. Before changing Flow scheduling/readiness/lint semantics, hand-simulate `kzen-auto-jvm/src/main/resources/notation/main/FizzBuzz/FizzBuzz Flow Loop.yaml` — its `SelectLast` has both optionals wired but only one branch produces per iteration (a `DivisibleFilter` drops the value), the hardest merge case; `flow-select-last-test.yaml` is the reduced test pin. The settled readiness rule: required inputs strict (wired + message), wired optionals never gate individually, ≥ 1 wired input must hold a message.
- **An attribute that names an object as *data* is declared `by: Nominal` — never left out of `meta:`.** `WeakAttributeDefiner` emits weak references: not constructor-resolved, invisible to `transitiveSuccessful` (a dangling or abstract-targeted entry degrades to a validation warning, never a prune), and rewritten by rename refactors. Precedents: `Custom.exports`, `RunStep.instructions`, `IfStepCommander.branchArchetype`, and the context declarations (`binds` on `ContextBinder`, `uses` / `releases` on `ScriptStep`, `context` on `Script`). Two supporting rules: (1) on the list/map forms, `of:` is mandatory (`WeakAttributeDefiner` reads `generics[0]` even for an empty value) and a scalar with an empty-string body default needs `nullable: true`; (2) an *undeclared* scalar naming a graph object is still promoted by `NotationMetadataReader.inferMetadata` into a hard `is: <that object>` reference — except when the target is `abstract: true`, which kzen-lib now skips (an inferred hard reference to an abstract object could only ever prune the host). The `Context` **archetype** is `abstract: true` like any never-instantiated archetype (`ResourceClosePolicy`, `TypeMetadata`), but a concrete Context **declaration** is not: it is a real `ContextDeclaration` object the graph instantiates, and the value contract it describes lives in its `type: TypeMetadata` rather than in `class:`. So the abstract-skip above is not what keeps a `binds:` / `uses:` entry weak — their explicit `by: Nominal` meta is, which is why declaring it is not optional.
- **Two different metadata inheritance rules, and mixing them up costs a definition failure.** An attribute's *own* `meta:` entry on a subtype **replaces** the inherited one wholesale (`NotationMetadataReader.readObjectImpl` walks the most-derived-first inheritance chain and keeps the first declaration) — so restating `binds` on a step to add an `editor:` must repeat `is:` / `nullable:` / `by:`, or the weak-reference contract and the empty-default definition are both lost. A *type archetype's* `meta: ref:` map, by contrast, is merged **per key** (`refMap.putAll(directMap)` in `readAttribute`), so `{is: SomeType, values: {...}}` overrides only `values` and still inherits that type's `by:` and `editor:`. Corollary worth knowing before narrowing an inherited attribute: `WeakAttributeDefiner` resolves an empty reference against the declared type's `nullable`, so giving an `is: List` attribute a scalar `""` default fails with "Empty object reference" — narrow the metadata to `is: ObjectLocation` + `nullable: true` (what `ScriptStep.releases` and `UseContextStep.uses` both do). *(Most-derived-wins is only true since 2026-08-02: `readObjectImpl` previously overwrote unconditionally, so metadata inherited in the opposite direction from values and a subtype could not refine it at all. A restatement that was value-identical to its base looked like it worked; only a narrowing revealed it.)*
- **Historical naming: "Feature" is now "Target".** `FeatureDocument` / `FeatureController` / `VisionService` in old history and sprint archives map to `TargetDocument` / `TargetController` / `TargetLocator` (`server.service.target`). There is no archetype aliasing, so user notation outside the repo with `is: Feature` needs a one-line hand edit to `is: Target`. Translate old names when reading historical docs — don't "fix" them there.
- **`logs/` and `work/` are runtime output dirs** under this root; they're `.gitignore`d.

## Pointers

- **kzen-auto-specific architecture** → [`docs/architecture.md`](docs/architecture.md) (paradigms, graph sync, REST surface, server composition root, report execution, plugin SPI, module registration).
- **JS client architecture** → [`docs/js-architecture.md`](docs/js-architecture.md) (Controller / Store / State / Observer patterns, render discipline, document folder convention, React DSL wrapper).
- **Foundational concepts (kzen-lib)** → [`../kzen-lib/docs/architecture.md`](../kzen-lib/docs/architecture.md).
- **Composite build + toolchain rules** → [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **Plugin example** → `../kzen-sample-plugin/`.
- **Downstream consumer** → `../kzen-project/` (built on top of kzen-auto, not just kzen-lib).
