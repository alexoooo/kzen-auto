# kzen-auto architecture

What kzen-auto adds on top of kzen-lib. Read [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md) first — this doc assumes you understand the Notation/Definition/Instance three-layer model, CQRS, and `ObjectLocation`-based addressing.

## What kzen-auto is

A web-based RPA / office-automation platform. Users open kzen-auto in a browser, edit declarative documents (reports, dataflows, sequences, etc.) in a graph editor, and execute them server-side. Plugins drop in extra report definitions via a small JAR-based SPI.

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
| **Dataflow** | `paradigm/dataflow/` | Lazy, pull-based pipeline. `Dataflow<State>` vertices have `RequiredInput`/`OptionalInput` and emit via `RequiredOutput`/`OptionalOutput`. Stateless, `StatelessDataflow`, and `StreamDataflow` variants exist. | Visual graph documents |
| **Logic** | `paradigm/logic/` | Step-through, traceable execution. `LogicController` coordinates pause/resume/step. Produces a `LogicTrace`. | Sequence / procedural documents |
| **Task** | `paradigm/task/` | Async, long-running, fire-and-forget. `ManagedTask` wraps `ExecutionRequest`; runs to completion under `TaskModel` tracking. | Background reports, automation runs |
| **Detached** | `paradigm/detached/` | One-shot request/response. `DetachedAction` executes one `ExecutionRequest` and returns `ExecutionResult` synchronously. No state tracking. | Quick administrative actions (e.g. plugin upload) |

**Rule of thumb when reading code:** if you see `TaskModel`, you're in the Task paradigm. `LogicTrace` / step controllers ⇒ Logic. `RequiredInput`/`RequiredOutput` ⇒ Dataflow. Plain `ExecutionRequest`/`ExecutionResult` with no wrapper ⇒ Detached.

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

`MirroredGraphStore` (from kzen-lib's `service/store/`) is the client-side composition: it forwards each `apply(command)` to both stores. The local store updates immediately (UI reads from it); the REST store ships the command to the server. Both stores emit `NotationEvent` to their observers, so UI repositories (`VisualDataflowLoop`, etc.) recompute derived state.

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

- Observers should compute derived state from the event payload + their own local cache only. Don't call back to the server inside `onCommandSuccess`. Concrete example: `VisualDataflowRepository.applySingular`'s `AddedObjectEvent` branch constructs `VisualVertexModel.empty` locally rather than calling `provider.inspectVertex`.
- If an observer invalidates its cached state in response to an event, it must also publish the new state to *its* observers (typically the UI), or downstream consumers stay frozen on the pre-event model. The `if (host in models) publishModel(...)` gate in `VisualDataflowRepository.onCommandSuccess` is fine — but only because the current code never invalidates (it always `put`s an empty entry instead).

## 3. REST API surface

Routes are declared in `KzenAutoMain.kt` (`routeNotationQuery`, `routeNotationCommands`, `routeDetached`, `routeTask`, `routeLogic`, `routeDataflow`) and dispatch into `RestHandler` (`kzen-auto-jvm/.../server/api/RestHandler.kt`). All path constants live in `CommonRestApi` (`kzen-auto-common/.../api/CommonRestApi.kt`), shared by both server and JS client so the two sides cannot drift.

| Group | Prefix | Example paths | Purpose |
|----|----|----|----|
| Notation query | `/scan`, `/notation/...`, `/resource` | — | Read-side: scan tree, fetch a document, read a resource blob |
| Notation commands | `/command/...` | `/command/document/create`, `/command/object/add`, `/command/attribute/upsert`, `/command/refactor/rename`, `/command/resource/add` | CQRS commands against the notation graph |
| Detached | `/action/...` | `/action/detached`, `/action/download` | Detached-paradigm one-shot actions; `/action/download` returns a file body with `Content-Disposition` |
| Task | `/task/...` | `/task/submit`, `/task/query`, `/task/cancel`, `/task/lookup` | Long-running background jobs (Task paradigm) |
| Logic | `/logic/...` | `/logic/status`, `/logic/startRun`, `/logic/startStep`, `/logic/run`, `/logic/step`, `/logic/pause`, `/logic/cancel`, `/logic/request` | Step/pause/resume of a logic-paradigm run |
| Dataflow | `/dataflow/...` | `/dataflow/model`, `/dataflow/reset`, `/dataflow/perform` | Ad-hoc visual-dataflow vertex execution |

Most endpoints are GET (idempotent commands carry their payload in the query string); large or text-heavy command bodies — notation upserts, list inserts, multi-value updates — also have PUT variants taking form parameters. There are no WebSocket or SSE channels.

**Gotcha — Logic step/pause/resume is poll-based, not push-based.** The client drives a run by polling `/logic/status`; the server returns a `LogicStatus` containing the current `LogicRunInfo` whose `LogicRunState` is one of `Running` / `Pausing` / `Paused` / `Stepping` / `Cancelling`. The UI repaints when it sees a `Paused` (or `Cancelling`) state. Frame state lives in `ServerLogicController`'s synchronized `LogicState` (volatile `running` / `paused` / `stepping` flags plus `pauseRequested` / `cancelRequested`); execution thread runs in a plain `Thread`, not a coroutine. Trace values flow to a process-local `LogicTraceStore` (`ConcurrentHashMap`) keyed by `LogicRunExecutionId` + path — so traces are visible to the polling client but do not survive a JVM restart.

## 4. Server-side composition root

The JVM-side analogue of the client's `ClientContext` (see [`js-architecture.md` § 4](js-architecture.md#4-service-layer-plumbing)). `KzenAutoContext` (`kzen-auto-jvm/.../server/context/KzenAutoContext.kt`) is instantiated once by `kzenAutoInit` at `KzenAutoMain.main` time, then held as a process-global through `setGlobal(...)` / `global()`. **Each kzen-auto JVM process owns exactly one `KzenAutoContext` — there is no internal notion of multiple "projects" inside the server.** The project layer lives one level up in `kzen-launcher` / `kzen-shell`, which front-ends multiple JVM processes (see umbrella `AGENTS.md`); plugin authors should treat the in-process world as single-tenant.

The companion-object `init` block registers SPI metadata with kzen-lib's `ReflectionRegistry` via three calls — `KzenLibCommonModule.register()`, `KzenAutoCommonModule.register()`, then `KzenAutoJvmModule.register()` (the JS module is JS-only; not called here). The constructor then wires the service graph:

| Field | Type | Role |
|----|----|----|
| `notationMedia` | `ReadWriteNotationMedia(FileNotationMedia(GradleLocator), classpathOverlay)` | Disk-backed notation I/O with a read-only classpath overlay for bundled documents |
| `graphStore` | `DirectGraphStore` | In-process notation store; the canonical mirror target for `ClientRestGraphStore` |
| `detachedExecutor` | `ModelDetachedExecutor` | Detached-paradigm runner |
| `modelTaskRepository` | `ModelTaskRepository` | Task-paradigm registry + runner (observer on `graphStore`) |
| `activeDataflowRepository`, `visualDataflowRepository` | dataflow paradigm | Server-side execution + visual-state model (observers on `graphStore`) |
| `serverLogicController` | `ServerLogicController` | Logic-paradigm state machine (see § 3 gotcha) |
| `restHandler` | `RestHandler` | Dispatch target for every route in `KzenAutoMain` |
| `cachedKotlinCompiler`, `calculatedColumnEval` | scripting | Embedded Kotlin scripting for report formula columns |
| `definitionRepository` | `MultiDefinitionRepository` | Report-definer pool: built-in (`CsvReportDefiner` / `TsvReportDefiner` / `TextReportDefiner`) plus `PluginReportDefinitionRepository` for JAR-loaded plugins |
| `webDriverContext` | `WebDriverContext` | Selenium / WebDriver lifecycle for browser-automation sequence steps |

`KzenAutoContext.init()` subscribes the dataflow/task repositories to the graph store via `graphStore.observe(...)` — the same observer mechanism described in § 2. The shutdown hook calls `context.close()`, which currently only quits the WebDriver pool.

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
| `graph/` | Visual dataflow | Node-and-edge graph; each node is a `Dataflow` instance |
| `sequence/` | Sequence | Step-by-step procedural execution; trace view |
| `data/` | Data schema | Field definitions / format |
| `plugin/` | Plugin registry | Upload / register plugin JARs |
| `registry/` | Object registry | Browse / add custom objects from the library |
| `feature/` | Feature extraction | Screenshot regions, computer-vision targets |
| `common/` | *(not a document type)* | Shared attribute editors used by every controller above |

When adding a new document type, expect to:

1. Define an `ObjectNotation` shape in `kzen-auto-common`.
2. Add a `*Controller` in `kzen-auto-js/.../objects/document/<type>/`.
3. Register the document type in the auto-generated module (regenerated, not hand-edited — see § 8).

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

Every paradigm and document type defines kzen-lib SPI objects (`ObjectDefiner`, `ObjectCreator`, `AttributeDefiner`). These need to be registered with kzen-lib's `ReflectionRegistry` at boot. kzen-auto generates these registrations:

- `kzen-auto-common/.../codegen/KzenAutoCommonModule.kt` — registrations for common-side SPI objects.
- `kzen-auto-jvm/.../codegen/KzenAutoJvmModule.kt` — JVM-specific.
- `kzen-auto-js/.../codegen/KzenAutoJsModule.kt` — JS-specific.

These files are **auto-generated by `ModuleReflectionGenerator`** — do not hand-edit. The generator itself lives in **kzen-lib** (`kzen-lib-jvm/src/main/kotlin/tech/kzen/lib/server/codegen/ModuleReflectionGenerator.kt`), not in kzen-auto; kzen-auto consumes it. Re-running the generator picks up new SPI classes by reflection and rewrites the file. There is currently no Gradle task or IDE run config wired for this — invoke it manually.

The generated output is a long sequence of `reflectionRegistry.put(...)` calls. A representative entry from `KzenAutoJvmModule.kt`:

```kotlin
reflectionRegistry.put(
    "tech.kzen.auto.server.objects.data.DataFormatDocument",
    listOf("fields")
) { args ->
    DataFormatDocument(args[0] as FieldFormatListSpec)
}
```

— the FQCN string is the key, the second argument is the ordered attribute-name list, and the lambda reflectively constructs an instance from positional args. The kzen-lib runtime calls this when it needs to instantiate the object from notation.

`KzenAutoContext` is the JVM bootstrap singleton. Its `init {}` block calls `KzenLibCommonModule.register()` followed by `KzenAutoCommonModule.register()` and `KzenAutoJvmModule.register()` — only the two JVM-side kzen-auto modules; `KzenAutoJsModule` is JS-only and not called from the JVM context. After this, kzen-lib's `ReflectionRegistry` knows how to instantiate every kzen-auto-defined object.

**When you add a new SPI class** (e.g. a new `ObjectDefiner` for a custom paradigm step):

1. Write the class in `commonMain`/`jvmMain`/`jsMain` as appropriate.
2. Rerun `ModuleReflectionGenerator` manually (no Gradle task wired).
3. Commit the regenerated `Module.kt`.

## Critical files

If you're new to kzen-auto, read these in order — they anchor the patterns above:

1. `kzen-auto-common/.../paradigm/dataflow/Dataflow.kt` — the cleanest paradigm.
2. `kzen-auto-common/.../paradigm/task/ManagedTask.kt` and `TaskModel.kt` — long-running execution.
3. `kzen-auto-common/.../api/CommonRestApi.kt` — every wire endpoint as a constant; shared by client and server.
4. `kzen-auto-js/.../service/rest/ClientRestGraphStore.kt` — the client-side REST proxy.
5. `kzen-auto-jvm/.../server/KzenAutoMain.kt` — Ktor route declarations.
6. `kzen-auto-jvm/.../server/context/KzenAutoContext.kt` — the JVM composition root.
7. `kzen-auto-jvm/.../server/api/RestHandler.kt` — the route-dispatch handler.
8. `kzen-auto-jvm/.../server/objects/report/exec/ReportPipelineStage.kt` — Disruptor handler.
9. `kzen-auto-plugin/.../ReportDefiner.kt` — plugin SPI entry point.
