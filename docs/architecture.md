# kzen-auto architecture

What kzen-auto adds on top of kzen-lib. Read [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md) first — this doc assumes you understand the Notation/Definition/Instance three-layer model, CQRS, and `ObjectLocation`-based addressing.

## What kzen-auto is

A web-based RPA / office-automation platform. Users open kzen-auto in a browser, edit declarative documents (reports, flows, scripts, etc.) in a graph editor, and execute them server-side. Plugins drop in extra report definitions via a small JAR-based SPI.

The non-obvious parts — and what this doc covers — are:

1. The **paradigm system**: four mutually-exclusive execution models.
2. **Client-server graph synchronization**: how the browser and server stay aligned.
3. **REST API surface**: the wire endpoints that wrap the graph store and the four paradigms.
4. **Server-side composition root** (`KzenAutoContext`): how the JVM wires its services.
5. **Backend execution**: how reports actually run (LMAX Disruptor).
6. **Document types**: the UI document model.
7. **Plugin SPI**: how third-party plugins extend reports.
8. **Module registration**: how SPI implementations get wired into kzen-lib.

## 1. Paradigm system

Subpackage: `kzen-auto-common/src/commonMain/kotlin/tech/kzen/auto/common/paradigm/`.

A "paradigm" is **a category of execution model**. A kzen-auto document belongs to exactly one paradigm, and the paradigm determines:

- how the runtime invokes it,
- what UI surface it gets in the browser,
- whether progress / pause / step interaction is available.

The four paradigms:

| Paradigm | Subpackage | Execution model | Typical document |
|----------|-----------|-----------------|------------------|
| **Dataflow** | `paradigm/flow/` | Lazy, pull-based pipeline. `FlowVertex<State>` vertices have `RequiredInput`/`OptionalInput` and emit via `RequiredOutput`/`OptionalOutput`. Stateless, `StatelessFlowVertex`, and `StreamFlowVertex` variants exist. | Flow document vertices (`document/flow/`), executed under the Logic paradigm — see Flow note below |
| **Logic** | kzen-lib `exec/logic/` (was `paradigm/logic/`) | Step-through, traceable execution. `LogicController` coordinates pause/resume/step; produces a `LogicTrace`. | Script / procedural documents |
| **Task** | kzen-lib `exec/task/` (was `paradigm/task/`) | Async, long-running, fire-and-forget. `ManagedTask` wraps `ExecutionRequest`; runs to completion under `TaskModel` tracking. | Background reports, automation runs |
| **Detached** | `paradigm/detached/` | One-shot request/response. `DetachedAction` executes one `ExecutionRequest` and returns `ExecutionResult` synchronously. No state tracking. | Quick administrative actions (e.g. plugin upload) |

**Rule of thumb when reading code:** if you see `TaskModel`, you're in the Task paradigm. `LogicTrace` / step controllers ⇒ Logic. `RequiredInput`/`RequiredOutput` ⇒ Dataflow. Plain `ExecutionRequest`/`ExecutionResult` with no wrapper ⇒ Detached.

> **Making a new logic document runnable in the UI.** Whether a document gets the Run / Step / Pause ribbon (and run-blocking on definition errors) is gated by `AutoConventions.isLogic(documentNotation)` in kzen-auto-common — a **hardcoded OR** over the runnable document types' `*Conventions.isX(...)` checks (currently Script / Flow / Report / Job). A new logic document type is **not** runnable from the UI until it is added there, even after its server-side `Logic` impl and client `DocumentController` (`…-js.yaml`, `archetype:` + `ribbonController: RibbonController`) both exist — the run controls silently stay disabled. This bit Job (M1 step 5).

> **Relocation (2026-05-28).** The `Logic` / `Task` / `Trace` / `Tuple` *types* moved to kzen-lib `tech.kzen.lib.common.exec.*` — see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#execution-model-logic--task--trace). What stays in kzen-auto is the paradigm *binding*: the REST wire surface (`paradigm/logic/LogicConventions`, the `/logic` and `/task` paths in `CommonRestApi`), the `ServerLogicController` / `ModelTaskRepository` server impls, and the documents themselves — e.g. a Script document implements kzen-lib's `Logic`. `paradigm/flow/` (the renamed dataflow paradigm) and `paradigm/detached/` did not move.

> **Flow (2026-06-19).** The former **Graph** / "Time Series" visual document (`GraphDocument`, driven by the bespoke `/dataflow/*` engine) was modernized into **Flow** (`server/objects/flow/FlowDocument`), which implements kzen-lib's `Logic`: one vertex execution = one step, run through `ServerLogicController` + `/logic/*`, with dedicated input/output vertices supplying parameters and a return value. The standalone dataflow execution engine — `ActiveDataflowRepository`, `VisualDataflowRepository`, `VisualDataflowLoop`, the `ActiveVisualProvider`/`VisualDataflowProvider`, and the `/dataflow/*` routes — was **retired** (clean rename, no `Graph` compat archetype). The low-level vertex/topology SPI (`FlowVertex`, `FlowMatrix`, `FlowDag`, `FlowUtils`, `VisualVertexModel`) and the vertex/edge rendering (`CellController`, `EdgeController`, `VertexController`) are **reused** by Flow — only the execution and visual-service layers were removed. The client `document/flow/FlowController` rebuilds per-vertex visual state from the logic trace store (`FlowProgressStore`), like `ScriptProgressStore`. **Full rename (2026-06-19):** the `paradigm.dataflow` and `objects.document.graph` / `server.objects.graph` packages and all `Dataflow*` class names were renamed to `paradigm.flow` / `objects.document.flow` / `server.objects.flow.vertex` and `Flow*` (`Dataflow`→`FlowVertex`, `DataflowMatrix`→`FlowMatrix`, `DataflowWiring`→`FlowWiring`, `VisualDataflowModel`→`VisualFlowModel`, etc.); notation archetype `Dataflow`→`FlowVertex`, `StreamDataflow`→`StreamFlowVertex`, `DataflowWiring`→`FlowWiring`. The unused `FolderDocument` was also removed.

## 2. Client-server graph synchronization

The browser holds a **mirror** of the server's notation graph, applies edits locally for instant UI feedback, and replays the same `NotationCommand` to the server over REST. CQRS means both sides converge by applying identical commands.

```
Browser                                          Server
─────────────────────────────────────────────    ───────────────────────────────────
  MirroredGraphStore                               LocalGraphStore (DirectGraphStore)
   ├─ DirectGraphStore  ◀── apply(cmd) ───┐         ▲
   │  (local mirror)                       │         │ apply(cmd)
   └─ ClientRestGraphStore ──HTTP─────────────┐   RestHandler ◀── POST/GET/PUT  /command/...
                                              │
                                       (same NotationCommand on the wire,
                                        per-command endpoint, YAML-serialized payload)
```

`MirroredGraphStore` (from kzen-lib's `service/store/`) is the client-side composition: it forwards each `apply(command)` to both stores. The local store updates immediately (UI reads from it); the REST store ships the command to the server. Both stores emit `NotationEvent` to their observers, so UI repositories recompute derived state.

**Concrete data flow — user edits a text attribute in the browser:**

1. `TextAttributeEditor.onValueChanged()` in jsMain.
2. Constructs an `UpsertAttributeCommand` (a `SemanticNotationCommand`).
3. Calls `mirroredGraphStore.apply(command)`.
4. Local `DirectGraphStore` applies the command → emits `NotationEvent` → observers update the UI.
5. `ClientRestGraphStore` ships the command to the corresponding per-command REST endpoint (here `/command/attribute/upsert` — see § 3 for the full surface).
6. Server `RestHandler` deserializes, applies to server-side graph store → server-side observers fire.
7. Server response confirms; if mismatch, client could resync (rare in practice — commands are deterministic).

The **observer pattern in kzen-lib** is what makes this work — both sides subscribe to the same event stream from their respective local stores. No diffing, no syncing logic beyond replaying the command.

**Gotcha — `MirroredGraphStore.apply` runs local + remote in parallel.** The two `apply` calls are wrapped in sibling `coroutineScope.async { ... }` blocks, so the local branch — which calls `publishSuccess(event)` → every observer's `onCommandSuccess` — executes concurrently with the in-flight remote POST. An observer that responds to a notation event by issuing its own remote query to the server can therefore race the original POST: the server may not yet have applied the command when the query arrives.

Two consequences:

- Observers should compute derived state from the event payload + their own local cache only. Don't call back to the server inside `onCommandSuccess` (e.g. construct a fresh empty model locally on an `AddedObjectEvent` rather than fetching it from the server).
- If an observer invalidates its cached state in response to an event, it must also publish the new state to *its* observers (typically the UI), or downstream consumers stay frozen on the pre-event model.

## 3. REST API surface

Routes are declared in `KzenAutoMain.kt` (`routeNotationQuery`, `routeNotationCommands`, `routeDetached`, `routeTask`, `routeLogic`) and dispatch into `RestHandler` (`kzen-auto-jvm/.../server/api/RestHandler.kt`). All path constants live in `CommonRestApi` (`kzen-auto-common/.../api/CommonRestApi.kt`), shared by both server and JS client so the two sides cannot drift.

| Group | Prefix | Example paths | Purpose |
|----|----|----|----|
| Notation query | `/scan`, `/notation/...`, `/resource` | — | Read-side: scan tree, fetch a document, read a resource blob |
| Notation commands | `/command/...` | `/command/document/create`, `/command/object/add`, `/command/attribute/upsert`, `/command/refactor/rename`, `/command/resource/add` | CQRS commands against the notation graph |
| Detached | `/action/...` | `/action/detached`, `/action/download` | Detached-paradigm one-shot actions; `/action/download` returns a file body with `Content-Disposition` |
| Task | `/task/...` | `/task/submit`, `/task/query`, `/task/cancel`, `/task/lookup` | Long-running background jobs (Task paradigm) |
| Logic | `/logic/...` | `/logic/status`, `/logic/startRun`, `/logic/startStep`, `/logic/run`, `/logic/step`, `/logic/stepOver`, `/logic/stepOut`, `/logic/pause`, `/logic/cancel`, `/logic/request`, `/logic/breakpoints` | Step / step-over / step-out / pause / resume of a logic-paradigm run (Script **and Flow**); `/logic/breakpoints` replace-sets the run's breakpoint elements (repeated `breakpoint` params, each a full `ObjectLocation`; the same params ride `/logic/startRun` / `/logic/startStep` so start-time breakpoints can't miss early steps) |

Most endpoints are GET (idempotent commands carry their payload in the query string); large or text-heavy command bodies — notation upserts, list inserts, multi-value updates — also have PUT variants taking form parameters. There are no WebSocket or SSE channels.

**Gotcha — Logic step/pause/resume is poll-based, not push-based.** The client drives a run by polling `/logic/status`; the server returns a `LogicStatus` containing the current `LogicRunInfo` whose `LogicRunState` is one of `Running` / `Pausing` / `Paused` / `Stepping` / `Cancelling`. The UI repaints when it sees a `Paused` (or `Cancelling`) state. Frame state lives in `ServerLogicController`'s synchronized `LogicState` (volatile `running` / `paused` / `stepping` flags plus `pauseRequested` / `cancelRequested`); execution thread runs in a plain `Thread`, not a coroutine. Trace values flow to a process-local `LogicTraceStore` (now `tech.kzen.lib.server.exec.logic.trace`, a `ConcurrentHashMap`) keyed by `LogicRunExecutionId` + an `ObjectStableId`-based path — so traces are visible to the polling client and survive document / step renames during *and after* a run (via the process-global `ObjectStableMapper`, see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#stable-identity-objectstablemapper)), but do not survive a JVM restart. `ServerLogicController` (the run state machine) stays in kzen-auto. **Starting a new run implicitly clears every prior run's trace** — `ServerLogicController.start` calls `logicTraceStore.clearAll()` before opening the run's trace handle (the same global wipe as the manual "Clear all traces" control), so a fresh run never shows stale per-step/per-vertex displays or screenshots.

## 4. Server-side composition root

The JVM-side analogue of the client's `ClientContext` (see [`js-architecture.md` § 4](js-architecture.md#4-service-layer-plumbing)). `KzenAutoContext` (`kzen-auto-jvm/.../server/context/KzenAutoContext.kt`) is built once via the self-initializing factory `KzenAutoContext.create(config)` at `KzenAutoMain.main` time (tests use `KzenAutoContext.forTest()`) and threaded explicitly to everything that needs it. Hand-constructed services (`RestHandler`, `ServerLogicController`, …) receive their dependencies as constructor arguments. Graph-instantiated objects — which the notation system constructs from YAML, so they can't be hand-injected — receive runtime services through **construction-time dependency injection**: a constructor parameter annotated `@Service` (kzen-lib) is filled by kzen-lib's `GraphCreator` from a `GraphEnvironment` registry rather than from notation (see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md) for the `@Service` / `GraphEnvironment` mechanism). `KzenAutoContext` builds that `graphEnvironment` (keyed by each service's declared type) and threads it into every server-side `createGraph` call — via a deferred `() -> GraphEnvironment` provider for the callers that are themselves registered in it (`serverLogicController`, the plugin repo), to break the construction cycle. Consequently `Logic.execute` and `DetachedAction.execute` now carry only per-run handles (no host/context parameter), and a step like `FormulaStep` simply declares `@Service private val cachedKotlinCompiler: CachedKotlinCompiler`. This replaced the earlier `LogicHost` / `DetachedActionContext` marker-and-downcast role interfaces (and, before those, the `setGlobal` / `global()` process-global). There is **no process-global singleton.** **Each kzen-auto JVM process owns exactly one `KzenAutoContext` — there is no internal notion of multiple "projects" inside the server.** The project layer lives one level up in `kzen-launcher` / `kzen-shell`, which front-ends multiple JVM processes (see umbrella `AGENTS.md`); plugin authors should treat the in-process world as single-tenant.

The companion-object `init` block registers SPI metadata with kzen-lib's `ReflectionRegistry` via three calls — `KzenLibCommonModule.register()`, `KzenAutoCommonModule.register()`, then `KzenAutoJvmModule.register()` (the JS module is JS-only; not called here). The constructor then wires the service graph:

| Field | Type | Role |
|----|----|----|
| `notationMedia` | `ReadWriteNotationMedia(FileNotationMedia(GradleLocator), classpathOverlay)` | Disk-backed notation I/O with a read-only classpath overlay for bundled documents |
| `graphStore` | `DirectGraphStore` | In-process notation store; the canonical mirror target for `ClientRestGraphStore` |
| `detachedExecutor` | `ModelDetachedExecutor` | Detached-paradigm runner |
| `modelTaskRepository` | `ModelTaskRepository` | Task-paradigm registry + runner (observer on `graphStore`) |
| `serverLogicController` | `ServerLogicController` | Logic-paradigm state machine (see § 3 gotcha); runs Script **and Flow**; observer on `graphStore` (event-driven live-edit detection: coarse edit-dirty flag, then a precise closure content-digest compare via kzen-lib `GraphDefinition.transitiveDigest`) |
| `flowMessageInspector` | `FlowMessageInspector` | Injected (via `graphEnvironment`) into Flow vertices for message inspection / tracing |
| `objectStableMapper` | `ObjectStableMapper` (kzen-lib) | Process-global `ObjectLocation ↔ ObjectStableId` bimap; `graphStore.observe(...)` at boot + pre-warmed over the initial notation (see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#stable-identity-objectstablemapper)) |
| `logicTraceStore` | `LogicTraceStore` (kzen-lib) | In-memory, stable-id-keyed trace store; constructed with `objectStableMapper` |
| `restHandler` | `RestHandler` | Dispatch target for every route in `KzenAutoMain` |
| `cachedKotlinCompiler`, `calculatedColumnEval` | scripting | Embedded Kotlin scripting for report formula columns |
| `definitionRepository` | `MultiDefinitionRepository` | Report-definer pool: built-in (`CsvReportDefiner` / `TsvReportDefiner` / `TextReportDefiner`) plus `PluginReportDefinitionRepository` for JAR-loaded plugins |

The Selenium / WebDriver browser handle is **no longer a context service**: it is a per-run resource keyed `"browser"` in the Script run's resource registry (opened by `BrowserOpenStep`, read by the action steps via `StepExecution.resource(...)`, disposed per its `closePolicy` when its owning document settles). `WebDriverSupport` holds only the shared key + quiet-quit helper. This replaced the former `webDriverContext` process singleton (removes a global; allows concurrent runs). The `closePolicy` also selects *which* document owns the handle's lifetime — its own (`auto`/`manual`/`keepOnFailure`), the calling document one level up (`parent`/`parentKeepOnFailure`), or the whole run (`run`/`runKeepOnFailure`) — so a sub-script can open the SUT but bind it to the enclosing test (see kzen-lib `ResourceScope`).

Construction is self-initializing: the private `init()` (run by `create()`/`forTest()`) subscribes the task repository, `objectStableMapper`, **and `serverLogicController`** (its edit-dirty flag for live-edit detection) to the graph store via `graphStore.observe(...)` — the same observer mechanism described in § 2 — then pre-warms the mapper by iterating the boot notation. The shutdown hook calls `context.close()`, which cancels the active run — settling its root node disposes any run-scoped resources (an open browser) through the engine.

## 5. Backend execution model

Subpackages of interest:

- `kzen-auto-jvm/.../server/service/exec/` — generic execution wiring.
- `kzen-auto-jvm/.../server/objects/report/exec/` — report-specific execution.

Two main runners:

- **`ModelTaskRepository`** (Task paradigm) — tracks long-running executions. UI triggers create a `TaskModel` with a unique ID; clients poll status. Used for reports, automation runs.
- **`ModelDetachedExecutor`** (Detached paradigm) — runs `DetachedAction` objects (`PluginDocument`, simple admin actions) synchronously and returns results.

### Report pipeline (LMAX Disruptor)

Reports — the most performance-sensitive paradigm — use the LMAX Disruptor pattern: a lock-free ring buffer where each pipeline stage is a Disruptor `EventHandler` running on its own thread.

```
ReportInputFramer ─▶ decode ─▶ filter ─▶ pivot ─▶ output
        │              │         │         │        │
        └─ each stage = one ReportPipelineStage thread
        └─ events pass through the ring buffer lock-free
```

`ReportPipelineStage` extends Disruptor's `EventHandler`. Stages don't block waiting on each other — they pull events from the ring buffer in order. This is why reports scale to large datasets with low overhead.

**Implication for editors:** never `Thread.sleep` or do blocking I/O inside a `ReportPipelineStage` — it stalls the ring buffer for everything downstream. Use async patterns (suspending functions, callbacks).

## 6. Document types in the UI

> For the JS-client patterns that back each UI (Controller / Store / State / Observer; document folder convention; the custom `RComponent` wrapper), see [`js-architecture.md`](js-architecture.md).

Each subdirectory under `kzen-auto-js/src/jsMain/kotlin/tech/kzen/auto/client/objects/document/` corresponds to a document type with its own `*Controller`, mapping to a kzen-lib document with a particular `ObjectNotation` shape — the UI is a specialized editor for that shape. The exception is `common/`, which is not a document type but a library of shared editors (`TextAttributeEditor`, `BooleanAttributeEditor`, `SelectAttributeEditor`, `AttributePathValueEditor`, `MultiTextAttributeEditor`, `DefaultAttributeEditor`, `LogicSignatureEditor`) reused across every document type.

| Subdir | Document type | What it edits |
|--------|---------------|---------------|
| `report/` | Report | Interactive data queries: input selection, filtering, pivot, export |
| `flow/` | Flow | Node-and-edge DAG, run via the Logic paradigm (Run/Step/Pause); each node is a `FlowVertex`. `FlowController` + `FlowProgressStore` plus the shared vertex/edge rendering (`CellController`, `EdgeController`, `VertexController`) all live here (the legacy `GraphController` was retired) |
| `script/` | Script | Step-by-step procedural execution; trace view |
| `data/` | Data schema | Field definitions / format |
| `plugin/` | Plugin registry | Upload / register plugin JARs |
| `registry/` | Object registry | Browse / add custom objects from the library |
| `target/` | Target | Element targeting: screenshot-crop visual matching today; selectors / expressions planned |
| `custom/` | CustomDocument | Hybrid editor: structured UI for prototype-driven object creation + raw-YAML escape hatch |
| `common/` | *(not a document type)* | Shared attribute editors used by every controller above |

When adding a new document type, expect to:

1. Define an `ObjectNotation` shape in `kzen-auto-common`.
2. Add a `*Controller` in `kzen-auto-js/.../objects/document/<type>/`.
3. Register the document type in the auto-generated module (regenerated, not hand-edited — see § 8).

### Target — open target-type set

The `target:` attribute on browser steps (`{type, value?, policy?, index?}`) is an **open set**:
each target type registers three fragments, and no shared file mentions any concrete type.
(1) An `is: TargetSpecType` notation object (common-action.yaml) declares the `type:` name it
handles (`typeName:`) and its value shape (`valueKind: none | text | reference`) — the shared
`TargetSpecDefiner` reads these straight from notation (a definer cannot take autowired
instances: it is instantiated mid-definition, before other objects exist), while the object's
`class:` (a `TargetSpecType` subclass) is autowired into `TargetSpecCreator` and instantiates
the runtime `TargetSpec`. (2) Server-side, a `TargetTypeLocator` registered with the
`TargetLocator` service (built-ins at construction; third parties via `register()`) resolves the
spec to a `WebElement`. (3) Client-side, an `is: TargetTypeDisplay` object (script-js.yaml)
contributes the type's dropdown label, value-editor row, and collapsed summary, autowired into
the `TargetSpecEditor` / `TargetAttributeView` hosts. The `policy:` key (`unique` default —
ambiguity fails loudly — or `first` / `nth` + `index` / `best`) is uniform across types and
enforced by `TargetLocator.selectByPolicy`. The acceptance proof is
`TargetExtensibilityTest` + the test-only `CssSelectorTarget`: a full type added with zero
shared-code edits. This is the same contract Workers, Steps, and Flow vertices honour.

Actuation is **browser-first**: every locator resolves against the Selenium driver, and desktop
(`ScreenshotTaker` / AWT `Robot`) capture exists only as a capture-source convenience in the
Target document screen — there are no desktop click/type steps. `ScreenshotTaker` is the future
hook if desktop RPA becomes concrete; the locator SPI's driver-typed context is the seam to
retype into a capture+actuation surface then (decision recorded 2026-07-12, target-improvements
plan phase 7).

### `CustomDocument` — structured UI + raw-YAML escape hatch

`CustomDocument` has two editing modes, toggled in the header (`DocumentViewMode.View | .Raw`, persisted via `CustomGlobal`). **View mode** (`CustomView` + `CustomCreate`) is a structured UI for prototype-driven object creation: `CustomConventions.listPrototypes(graphNotation)` discovers every object marked `is: Prototype` anywhere in the graph and exposes them in the `+ Add` dropdown. UI-created objects nest under `main.objects/<Name>` (`CustomConventions.objectsAttributePath`); the `main.logic` list is a separate selection of which objects participate in execution, toggleable per-object in the view. **Raw mode** (`DocumentRaw`) is a plain-text YAML editor — `<textarea>` with a synced line-number gutter (`YamlEditor` under `objects/document/common/edit/`), Ctrl/Cmd+S to Save — and enforces no nesting convention; any structure that parses is accepted. Comments and key order are **not** preserved across the parse → deparse round trip.

Both modes share the save flow: the client parses the full document via `YamlNotationParser.parseDocumentObjects` and dispatches `SetDocumentObjectsCommand` (the only bulk-replace command in the notation CQRS — see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md)) through the same `MirroredGraphStore` pipeline as every other command. No archetype or schema enforcement on save — power-tool semantics; broken references surface at the definition layer on next reload.

The raw-editing stack is document-agnostic and lives under `objects/document/common/raw/` (`DocumentViewMode`, `DocumentRawState`, `DocumentRawStore`, `DocumentRaw`, plus the `DocumentRawHost` seam each document store implements). `ScriptDocument` reuses it for a **Raw** view, but exposes it differently from Custom: instead of a header toggle, "Raw" is a tab in the shared ribbon (the `ScriptGroup_Raw` `RibbonGroup` in notation, with no `RibbonTool` children, so it offers no actions). Selecting a ribbon tab publishes its `RibbonGroup.viewMode` (`""` for action groups, `"Raw"` for the raw tab) through `ViewModeGlobal` — a command channel mirroring `InsertionGlobal` — and `ScriptController` subscribes and calls `ScriptStore.setViewMode`, switching the stage. This keeps the shared `RibbonController` document-agnostic (it forwards a notation-declared view id; it knows nothing about Script or raw).

## 7. Plugin SPI

Subpackage: `kzen-auto-plugin/src/main/kotlin/tech/kzen/auto/plugin/`. **This is the public contract** for third-party plugins. Don't break it casually.

The plugin model is JAR-based, reflection-loaded:

1. A plugin JAR contains classes that subclass `ReportDefiner<Output>` (no-arg constructor required).
2. User uploads the JAR via the plugin document UI.
3. Server-side `PluginDocument` (a `DetachedAction`) loads the JAR via a `URLClassLoader`, scans for `ReportDefiner` subclasses, instantiates each reflectively, registers them with `PluginReportDefinitionRepository`.
4. Plugin-defined reports become available like built-in ones.

Key public types:

| Type | Purpose |
|------|---------|
| `ReportDefiner<Output>` | Subclass + override `info()` and `define()` to declare a report |
| `ReportDefinition<Output>` | Result of `define()`: input/output shape, stage chain |
| `ReportTerminalStep`, `ReportIntermediateStep` | Pipeline stage building blocks |
| `DataFramer`, `HeaderExtractor` | Utility SPIs for structured-data plugins |

The sibling `../kzen-sample-plugin` is an example. When working on a plugin: it compiles against `kzen-auto-plugin` from mavenLocal, so make sure `./gradlew :kzen-auto-plugin:publishToMavenLocal` ran after any plugin-SPI change.

## 8. Module registration

Every paradigm and document type defines kzen-lib SPI objects (`ObjectDefiner`, `ObjectCreator`, `AttributeDefiner`). These need to be registered with kzen-lib's `ReflectionRegistry` at boot. A KSP processor (`kzen-lib-reflect-ksp`) walks every `@Reflect`-annotated class in each module and emits a `ModuleReflection` object that wires the registrations:

- `tech.kzen.auto.common.codegen.KzenAutoCommonModule` — registrations for common-side SPI objects.
- `tech.kzen.auto.server.codegen.KzenAutoJvmModule` — JVM-specific.
- `tech.kzen.auto.client.codegen.KzenAutoJsModule` — JS-specific.

These objects live under `build/generated/ksp/.../codegen/` (gitignored) and are produced by the `kspCommonMainKotlinMetadata`, `kspKotlin`, and `kspKotlinJs` tasks. They run automatically as part of `./gradlew build` — no separate codegen step. The KSP processor is configured per-module in `kzen-auto-{common,jvm,js}/build.gradle.kts` via a `ksp { arg("kzen.reflect.moduleClassName", "<fqn>") }` block.

The generated output is a sequence of `reflectionRegistry.put(...)` calls. A representative entry from `KzenAutoJvmModule.kt`:

```kotlin
reflectionRegistry.put(
    "tech.kzen.auto.server.objects.data.DataFormatDocument",
    listOf("fields")
) { args ->
    DataFormatDocument(args[0] as FieldFormatListSpec)
}
```

— the FQCN string is the key, the second argument is the ordered attribute-name list, and the lambda reflectively constructs an instance from positional args. The kzen-lib runtime calls this when it needs to instantiate the object from notation.

`KzenAutoContext` is the JVM bootstrap composition root (one per process, threaded explicitly — not a global singleton). Its `init {}` block calls `KzenLibCommonModule.register()` followed by `KzenAutoCommonModule.register()` and `KzenAutoJvmModule.register()` — only the two JVM-side kzen-auto modules; `KzenAutoJsModule` is JS-only and not called from the JVM context. After this, kzen-lib's `ReflectionRegistry` knows how to instantiate every kzen-auto-defined object.

**When you add a new SPI class** (e.g. a new `ObjectDefiner` for a custom paradigm step):

1. Write the class in `commonMain`/`jvmMain`/`jsMain` as appropriate, with `@Reflect` on it.
2. `./gradlew build` (or just let your next compile pick it up) — KSP regenerates the matching `Module` automatically.

## Critical files

If you're new to kzen-auto, read these in order — they anchor the patterns above:

1. `kzen-auto-common/.../paradigm/flow/api/FlowVertex.kt` — the cleanest paradigm.
2. `kzen-lib-common/.../exec/task/ManagedTask.kt` and `exec/task/model/TaskModel.kt` — long-running execution (relocated from kzen-auto 2026-05-28).
3. `kzen-auto-common/.../api/CommonRestApi.kt` — every wire endpoint as a constant; shared by client and server.
4. `kzen-auto-js/.../service/rest/ClientRestGraphStore.kt` — the client-side REST proxy.
5. `kzen-auto-jvm/.../server/KzenAutoMain.kt` — Ktor route declarations.
6. `kzen-auto-jvm/.../server/context/KzenAutoContext.kt` — the JVM composition root.
7. `kzen-auto-jvm/.../server/api/RestHandler.kt` — the route-dispatch handler.
8. `kzen-auto-jvm/.../server/objects/report/exec/ReportPipelineStage.kt` — Disruptor handler.
9. `kzen-auto-plugin/.../ReportDefiner.kt` — plugin SPI entry point.
10. `kzen-auto-jvm/.../server/objects/script/ScriptDocument.kt` — the reference `Logic` implementation (Script paradigm), built on kzen-lib `exec/logic` + the process-global `ObjectStableMapper`.
