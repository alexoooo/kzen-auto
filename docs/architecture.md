# kzen-auto architecture

What kzen-auto adds on top of kzen-lib. Read [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md) first — this doc assumes you understand the Notation/Definition/Instance three-layer model, CQRS, and `ObjectLocation`-based addressing.

## What kzen-auto is

A web-based RPA / office-automation platform. Users open kzen-auto in a browser, edit declarative documents (reports, dataflows, sequences, etc.) in a graph editor, and execute them server-side. Plugins drop in extra report definitions via a small JAR-based SPI.

The non-obvious parts — and what this doc covers — are:

1. The **paradigm system**: four mutually-exclusive execution models.
2. **Client-server graph synchronization**: how the browser and server stay aligned.
3. **Backend execution**: how reports actually run (LMAX Disruptor).
4. **Document types**: the UI document model.
5. **Plugin SPI**: how third-party plugins extend reports.
6. **Module registration**: how SPI implementations get wired into kzen-lib.

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
   └─ ClientRestGraphStore ──HTTP─────────────┐   RestHandler ◀── POST /api/v1/graph/apply
                                              │
                                       (same NotationCommand on the wire,
                                        YAML-serialized)
```

`MirroredGraphStore` (from kzen-lib's `service/store/`) is the client-side composition: it forwards each `apply(command)` to both stores. The local store updates immediately (UI reads from it); the REST store ships the command to the server. Both stores emit `NotationEvent` to their observers, so UI repositories (`VisualDataflowLoop`, etc.) recompute derived state.

**Concrete data flow — user edits a text attribute in the browser:**

1. `TextAttributeEditor.onValueChanged()` in jsMain.
2. Constructs a `SetAttributeCommand` (a `SemanticNotationCommand`).
3. Calls `mirroredGraphStore.apply(command)`.
4. Local `DirectGraphStore` applies the command → emits `NotationEvent` → observers update the UI.
5. `ClientRestGraphStore` POSTs the YAML-serialized command to `/api/v1/graph/apply`.
6. Server `RestHandler` deserializes, applies to server-side graph store → server-side observers fire.
7. Server response confirms; if mismatch, client could resync (rare in practice — commands are deterministic).

The **observer pattern in kzen-lib** is what makes this work — both sides subscribe to the same event stream from their respective local stores. No diffing, no syncing logic beyond replaying the command.

## 3. Backend execution model

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

## 4. Document types in the UI

Each subdirectory under `kzen-auto-js/src/jsMain/kotlin/tech/kzen/auto/client/objects/document/` is a different document type with its own `*Controller`. Each maps to a kzen-lib document with a particular `ObjectNotation` shape — the UI is a specialized editor for that shape.

| Subdir | Document type | What it edits |
|--------|---------------|---------------|
| `report/` | Report | Interactive data queries: input selection, filtering, pivot, export |
| `graph/` | Visual dataflow | Node-and-edge graph; each node is a `Dataflow` instance |
| `sequence/` | Sequence | Step-by-step procedural execution; trace view |
| `data/` | Data schema | Field definitions / format |
| `plugin/` | Plugin registry | Upload / register plugin JARs |
| `registry/` | Object registry | Browse / add custom objects from the library |
| `feature/` | Feature extraction | Screenshot regions, computer-vision targets |

When adding a new document type, expect to:

1. Define an `ObjectNotation` shape in `kzen-auto-common`.
2. Add a `*Controller` in `kzen-auto-js/.../objects/document/<type>/`.
3. Register the document type in the auto-generated module (regenerated, not hand-edited — see § 6).

## 5. Plugin SPI

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

## 6. Module registration

Every paradigm and document type defines kzen-lib SPI objects (`ObjectDefiner`, `ObjectCreator`, `AttributeDefiner`). These need to be registered with kzen-lib's `ReflectionRegistry` at boot. kzen-auto generates these registrations:

- `kzen-auto-common/.../codegen/KzenAutoCommonModule.kt` — registrations for common-side SPI objects.
- `kzen-auto-jvm/.../codegen/KzenAutoJvmModule.kt` — JVM-specific.
- `kzen-auto-js/.../codegen/KzenAutoJsModule.kt` — JS-specific.

These files are **auto-generated by `ModuleReflectionGenerator`** — do not hand-edit. Re-running the generator picks up new SPI classes by reflection and rewrites the file.

`KzenAutoContext` is the JVM bootstrap singleton. Its `init {}` block calls `KzenLibCommonModule.register()` followed by the three `KzenAuto*Module.register()` calls. After this, kzen-lib's `ReflectionRegistry` knows how to instantiate every kzen-auto-defined object.

**When you add a new SPI class** (e.g. a new `ObjectDefiner` for a custom paradigm step):

1. Write the class in `commonMain`/`jvmMain`/`jsMain` as appropriate.
2. Rerun `ModuleReflectionGenerator` (find its Gradle task or run config).
3. Commit the regenerated `Module.kt`.

## Critical files

If you're new to kzen-auto, read these in order — they anchor the patterns above:

1. `kzen-auto-common/.../paradigm/dataflow/Dataflow.kt` — the cleanest paradigm.
2. `kzen-auto-common/.../paradigm/task/ManagedTask.kt` and `TaskModel.kt` — long-running execution.
3. `kzen-auto-js/.../service/rest/ClientRestGraphStore.kt` — the client-side REST proxy.
4. `kzen-auto-jvm/.../server/api/RestHandler.kt` — the server-side command-apply endpoint.
5. `kzen-auto-jvm/.../server/objects/report/exec/ReportPipelineStage.kt` — Disruptor handler.
6. `kzen-auto-plugin/.../ReportDefiner.kt` — plugin SPI entry point.
7. `kzen-auto-jvm/.../server/context/KzenAutoContext.kt` — bootstrap and module registration.
