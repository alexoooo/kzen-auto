# kzen-auto architecture

What kzen-auto adds on top of kzen-lib. Read [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md) first — this doc assumes you understand the Notation/Definition/Instance three-layer model, CQRS, and `ObjectLocation`-based addressing.

## What kzen-auto is

A web-based RPA / office-automation platform. Users open kzen-auto in a browser, edit declarative documents (reports, flows, scripts, etc.) in a graph editor, and execute them server-side. Plugins drop in extra report definitions via a small JAR-based SPI.

The non-obvious parts — and what this doc covers — are:

1. The **paradigm system**: how a document gets executed, and the four Logic flavours.
2. **Client-server graph synchronization**: how the browser and server stay aligned.
3. **REST API surface**: the wire endpoints that wrap the graph store and the four paradigms.
4. **Server-side composition root** (`KzenAutoContext`): how the JVM wires its services.
5. **Backend execution**: the three runners, and how reports actually run (LMAX Disruptor).
6. **Managed storage**: the on-disk areas the server owns, with budgets and eviction.
7. **Document types**: the UI document model.
8. **Plugin SPI**: how third-party plugins extend reports.
9. **Module registration**: how SPI implementations get wired into kzen-lib.

## 1. Paradigm system

Subpackage: `kzen-auto-common/src/commonMain/kotlin/tech/kzen/auto/common/paradigm/`.

A "paradigm" is **a category of execution model**. It determines how the runtime invokes a document, what UI surface it gets in the browser, and whether progress / pause / step interaction is available. There are three, and **Logic is where essentially all real work happens**:

| Paradigm | Subpackage | Execution model | Used by |
|----------|-----------|-----------------|---------|
| **Logic** | kzen-lib `exec/engine/` (core) + `exec/logic/` (wire models); binding in `paradigm/logic/` | Long-running, pausable, steppable, traceable, live-editable. A `Logic` is a suspendable `run(execution)`; the single-writer `RunEngine` owns the run. Server-side driver is `ServerLogicController`, wire surface `/logic/*`. | **Four flavours** — Script, Flow, Job, Report (below) |
| **Detached** | `paradigm/detached/` | One-shot request/response. `DetachedAction` executes one `ExecutionRequest` and returns `ExecutionResult` synchronously. No state tracking. | Validators (`ScriptValidator`, `JobValidator`), `TargetLocateAction`, `ScreenshotTaker`, `PluginDocument`, `LogicTraceEndpoint`, and Report's own browse / preview actions |
| **Task** | kzen-lib `exec/task/` (was `paradigm/task/`) | Async, long-running, fire-and-forget. `ManagedTask` wraps `ExecutionRequest`; runs to completion under `TaskModel` tracking. | **Nothing built-in.** Reports migrated to Logic (see the Report note below); the only implementation left is the `AdhocTask` fixture. It survives as a real extension point — a `CustomDocument` prototype tagged `meta: tags: task` gets the Task run affordance (§ 7) |

**Dataflow is not a fourth paradigm** — `paradigm/flow/` is the *vertex SPI* that the Flow flavour of Logic is built from. `FlowVertex<State>` vertices declare `RequiredInput` / `OptionalInput` and emit via `RequiredOutput` / `OptionalOutput`, with `StatelessFlowVertex` and `StreamFlowVertex` variants. A Flow document's *execution* is a Logic run; the vertex model is how one step of it is described.

A document is **not** limited to one paradigm: `ReportDocument` is simultaneously a `LogicDocument` (it runs), a `DetachedAction` (file browsing, column listing, preview), and a `DetachedDownloadAction` (export).

**Rule of thumb when reading code:** `LogicTrace` / `Execution` / step controllers ⇒ Logic. `RequiredInput` / `RequiredOutput` ⇒ a Flow vertex, i.e. still Logic. `TaskModel` ⇒ Task. Plain `ExecutionRequest` / `ExecutionResult` with no wrapper ⇒ Detached.

### The four Logic flavours

Each is a document type whose `main` archetype declares `is: [Document, Logic]` in notation and implements the server-side `LogicDocument` interface, whose `toLogic(...)` compiles the document's notation into an engine `Logic`:

| Flavour | Archetype → compiler → run | Shape |
|---------|---------------------------|-------|
| **Script** | `ScriptDocument` → `ScriptLogicCompiler` → `ScriptLogic` / `ScriptRunContext` | Sequential steps, nested control flow, sub-script hosting |
| **Flow** | `FlowDocument` → `FlowLogicCompiler` → `FlowLogic` / `FlowRun` | Synchronous vertex DAG; one vertex execution = one step |
| **Job** | `JobDocument` → `JobLogicCompiler` → `JobLogic` / `JobRun` / `WorkerLogic` | Concurrent workers over channels, with a flavour-owned `JobDeadlockMonitor` |
| **Report** | `ReportDocument` → `ReportLogicCompiler` → `ReportLogic` / `ReportRun` | The Disruptor record pipeline, driven through `Execution` (§ 5) |

> **Making a new logic document runnable in the UI.** Whether a document gets the Run / Step / Pause ribbon (and run-blocking on definition errors) is gated by `AutoConventions.isLogic(graphNotation, documentPath)` in kzen-auto-common, and it is **notation-driven**: `isLogic` returns true when the document's `main` archetype's inheritance chain reaches the common `Logic` marker. So every runnable paradigm's `main` archetype declares `is: [Document, Logic]` (see `common-document.yaml`; `Logic` is a bare `abstract` marker composed *alongside* `Document`, never as an `is: Document` intermediate, so the sidebar archetype registry's direct-`is`-match autowire is undisturbed). The server-side twin is the `LogicDocument` interface each paradigm's `main` archetype implements — the authoritative runtime guard, enforced by `LogicCompiler`'s `as? LogicDocument` cast. **A new logic document type is runnable from the UI as soon as its `main` archetype declares `is: Logic` and implements `LogicDocument` — no edit to `isLogic` (or any other shared code) is required** (it still needs its client `DocumentController`: `…-js.yaml`, `archetype:` + `ribbonController: RibbonController`). This was formerly a hardcoded OR over the four `*Conventions.isX(...)` checks — a god-object edit that bit Job (M1 step 5) — replaced by the `Logic`-marker inheritance query (CC-17). `LogicCompiler` itself contains **no `when` over flavours**: it resolves the `main` archetype polymorphically from the graph and calls `toLogic`. One detail there is load-bearing — the archetype is instantiated from a graph narrowed by `filterTransitive` to the document's own closure, not the whole project. A full-document build would throw for Job, whose saved Worker ports are deliberately blank until channel synthesis runs.

> **Relocation.** The `Logic` / `Task` / `Trace` / `Tuple` *types* moved to kzen-lib `tech.kzen.lib.common.exec.*` — see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#execution-model-logic--task--trace). What stays in kzen-auto is the paradigm *binding*: the REST wire surface (`paradigm/logic/LogicConventions`, the `/logic` and `/task` paths in `CommonRestApi`), the `ServerLogicController` / `ModelTaskRepository` server impls, and the documents themselves — a Script document is a `LogicDocument` that *compiles to* kzen-lib's `Logic`. `paradigm/flow/` (the renamed dataflow paradigm) and `paradigm/detached/` did not move.

> **Flow.** The former **Graph** / "Time Series" visual document (`GraphDocument`, driven by the bespoke `/dataflow/*` engine) was modernized into **Flow** (`server/objects/flow/FlowDocument`), a `LogicDocument` compiling to `FlowLogic`: one vertex execution = one step, run through `ServerLogicController` + `/logic/*`, with dedicated input/output vertices supplying parameters and a return value. The standalone dataflow execution engine — `ActiveDataflowRepository`, `VisualDataflowRepository`, `VisualDataflowLoop`, the `ActiveVisualProvider`/`VisualDataflowProvider`, and the `/dataflow/*` routes — was **retired** (clean rename, no `Graph` compat archetype). The low-level vertex/topology SPI (`FlowVertex`, `FlowMatrix`, `FlowDag`, `FlowUtils`, `VisualVertexModel`) and the vertex/edge rendering (`CellController`, `EdgeController`, `VertexController`) are **reused** by Flow — only the execution and visual-service layers were removed. The client `document/flow/FlowController` rebuilds per-vertex visual state from the logic trace (`FlowProgressStore`), like `ScriptProgressStore`. **Full rename:** the `paradigm.dataflow` and `objects.document.graph` / `server.objects.graph` packages and all `Dataflow*` class names were renamed to `paradigm.flow` / `objects.document.flow` / `server.objects.flow.vertex` and `Flow*` (`Dataflow`→`FlowVertex`, `DataflowMatrix`→`FlowMatrix`, `DataflowWiring`→`FlowWiring`, `VisualDataflowModel`→`VisualFlowModel`, etc.); notation archetype `Dataflow`→`FlowVertex`, `StreamDataflow`→`StreamFlowVertex`, `DataflowWiring`→`FlowWiring`. The unused `FolderDocument` was also removed.

> **Job.** The third flavour: a graph of **concurrently-running Workers connected by
> Channels** (`server/objects/job/`, engine side `server/exec/job/`), as opposed to Script's sequential
> steps and Flow's synchronous DAG. `JobLogic` is thin and immutable — `JobLogicCompiler` compiles the
> structure once, and each `run` builds a fresh `JobRun` with that call's instance graph, so one Job can
> be hosted more than once (e.g. nested in a Script). Channels are **auto-managed**: order drives wiring
> and the channel objects are synthesized in memory rather than written to YAML, which is why the
> archetype must be built from a `filterTransitive`-narrowed graph (a Worker's saved ports are blank
> until synthesis runs). The wiring invariants: adjacent workers auto-connect when the upper has exactly
> one open output port and the lower one open input port; a port is "open" when its notation scalar is
> blank **or dangling** (`JobChannelDerivation.isOpenPort` — dangling-as-open is load-bearing, since the
> editor hides channel-port editors and an orphaned reference could otherwise never be cleared; treating
> it as open lets synthesis reclaim it), while a port resolving to a real Channel is a manual wire (the
> fan-in escape hatch). `JobChannelSynthesis` (kzen-auto-common) augments an **in-memory copy** of the
> notation (synthesized `is: Channel` / `is: DuplexChannel` objects + filled port refs) and re-derives a
> normal GraphDefinition — creators, the run loop, and migrate carryover stay unchanged — with
> deterministic names (`JobConventions.autoSynthChannelName` = `ch__<upstreamLeaf>__<outPort>`) so
> ObjectStableId-keyed migration carryover survives unrelated edits. Per-output-port channel config
> (batchSize / capacity) lives on the **upstream Worker** in a free-form `channels.<outputPort>` map —
> deliberately undeclared in the Worker base's `meta` (no card editor, no "Missing" definition drop,
> still persisted in notation) so it follows the Worker across rename and reorder; precedence is Worker
> value > Job-wide default on `main` (`main.batchSize` / `main.capacity`) > archetype default, with the
> shared path builders in `JobConventions` keeping server synthesis and client editors agreeing. Two
> things about Job are worth knowing generally, because they are where
> concurrency stops being free: quiescence (what pause / step / edit act on) is **not** liveness, so
> deadlock detection is deliberately flavour-owned — `JobDeadlockMonitor` reads channel state and runs
> **off** the engine dispatcher, since running on it would deadlock `awaitQuiescent`. And the run-level
> pause reason across concurrently-parked spines is chosen by tree position rather than severity, so an
> error-parked Worker can be masked by a sibling at an ordinary boundary (a known gap, `logic-spec.md`
> §4). Job is intended to eventually subsume Report; the living plan is `kzen/plans/2026-07-25_job-improvements.md`.

> **Report → Logic.** Reports were the last holdout of the Task paradigm; they now run as
> the **fourth Logic flavour**. `ReportDocument` gained `LogicDocument` (keeping its `DetachedAction` /
> `DetachedDownloadAction` roles for browsing, preview and export), and the re-entrant
> `ReportExecution` — `init` + `continueOrStart` + `close`, driven by `ModelTaskRepository` — was
> **deleted** in favour of the coroutine-shaped `ReportRun`. The entire Disruptor record pipeline is
> reused verbatim (§ 5); only four seams were swapped onto `Execution`: the input poll loop calls
> `Execution.checkpoint` each iteration instead of polling a cancel command; the result is a returned
> `TupleValue` or a thrown failure instead of a `LogicResult`; progress is written through
> `ExecutionLogicTraceHandle` (literal trace paths bridged onto `Execution.emit`) instead of a
> framework-supplied trace handle; and the online preview handler registers via `Execution.onRequest`.
> **Known parity gap:** `ReportLogic` registers no `Execution.onCapture`, so a live edit cleanly
> *restarts* the report on the edited definition rather than migrating it — the safe default
> (`logic-spec.md` §5), and the same first-port state Flow and Job began in.

> **Script document model.** Step membership and order derive from **document position**, not
> explicit list attributes: `ScriptDocument` / `IfStep` / `ForEachStep` hold no `steps:` lists in
> YAML — kzen-lib's `NestedListAttributeDefiner` materializes each parent's `List<ObjectLocation>`
> from the objects nested under it, via weak refs (weak is load-bearing: only weak materializes as
> locations without forcing construction order). Editor consequences: reorder =
> `ShiftObjectTreeCommand` (moves the nested subtree contiguously); add = `AddObjectCommand` at a
> computed document index (not `AddObjectAtAttributeCommand`, which writes a stray scalar back);
> remove = a deepest-first cascade of `RemoveObjectCommand` (no built-in cascade). Parameters and
> loop items are **bindings** — real notation objects (`is: ScriptStep`) in a non-`steps` branch
> (`parameters` / `item`), so the whole ObjectLocation-keyed stack (step models, validation, the
> execution margin, dependency analysis) is reused; they are typed and validated (via
> `TypeMetadataDefiner`) but never executed, resolving on demand through
> `ScriptExecutionContext.referencedValue` (`ParameterBinding` with a leniently-coerced `default:`,
> `ForEachItemBinding`, `ScriptValueBinding`). The result signature is **ResultStep-only**: the
> `results` map (`ResultSignatureDefiner`) types the output tuple, and the Script's value is the
> last invoked `ResultStep`'s (VB-style), or void when none ran — there is no last-step fallback,
> so a Script consumed via `RunStep` / `ForEachStep` returns void until a ResultStep is added. Two
> notation-wiring traps recur here: a `by:` in `meta` needs a sibling `is:`
> (`meta: { default: { is: Object, by: ParameterDefaultDefiner } }` — a `ref`/`by` indirection
> silently falls back to `StructuralAttributeDefiner`), and
> `GraphNotation.firstAttribute(loc, AttributeName)` throws when absent — use the nullable
> `AttributePath` overload for optional attributes.

> **Script branch metadata markers.** Three attribute-metadata keys on a step's `meta.<branch>` steer
> the shared Script analyses without naming any step type, so a third-party construct joins each
> semantic declaratively (all three are inert for definition): **`rerun: true`** marks a branch a loop
> re-runs (`ScriptNestingAnalysis` — ControlStep targets, move-to rejection); **`scope: body`** marks
> an expression whose in-scope references are the declaring step's own body rather than its
> predecessors (`DoWhileStep.condition`, read by `ScriptConventions.isBodyScopedExpression`); and
> **`group: true`** marks a branch whose children are structural GROUP nodes rather than steps —
> `IfStep.branches`, whose children are `IfBranch` objects each owning a condition plus their own
> `steps` sub-branch. `ScriptConventions.stepGroupAttributeNames` is the single reader, and every
> group-aware rule keys off it: the group node gets no execution band and is not a jump target
> (`ScriptNestingAnalysis` descends through it; `ScriptJumpAnalysis` rejects it and filters it out of
> the descend ancestors), sibling groups are excluded from each other's scope (`ScriptTree.predecessors`
> — an earlier If branch did not run when a later one does), and the group node itself is registered as
> a dependency-edge endpoint so the identifiers its condition names draw a line (`ScriptDependencyAnalysis`
> — a lexical code-scalar edge, since a branch condition is a Kotlin expression, not a reference).
> An N-way construct that shared code has never heard of therefore needs no edit here — the contract
> `ScriptBranchDiscoveryTest` pins.

> **Kotlin expressions — reference analysis, rename rewriting, validation.** Expressions
> (`FormulaStep`, `ResultStep`, `DoWhile` and `IfBranch` conditions, Job formulas) are analyzed by
> **`KotlinExpressionAnalyzer`** (kzen-auto-common `util/`), a hand-rolled Kotlin lexer:
> `referencedIdentifiers(code)` / `renameIdentifier(code, from, to)` correctly skip strings
> (including raw strings and `${}` templates, whose identifiers ARE references), char literals,
> comments, back-tick identifiers, and member selectors. It is lexical, not semantic (a local
> `val foo` shadowing a step is not resolved) — but do not re-introduce regex / word-boundary
> matching or unconditional back-ticking anywhere. **`ExpressionUtils`** (kzen-auto-common) is the
> canonical name↔identifier conversion (`escapeKotlinVariableName` + `identifierContent`), shared
> by codegen, dependency analysis, and the rewriter. Rename refactors rewrite expressions through
> kzen-lib's `CodeReferenceRewriter` SPI: `KzenAutoCodeReferenceRewriter` (kzen-auto-common)
> resolves per-expression scope via `ScriptTree.predecessors + inScopeBindingPaths` — the same
> scoping codegen uses — so same-named objects in sibling branches don't cross-rewrite, and it is
> wired at both construction sites (server `KzenAutoContext` and client `ClientContext`) so the two
> sides' digests stay equal. Syntax/type validation runs server-side (`KotlinSyntaxValidator`,
> `server/service/compile/`) and surfaces client-side through `ExpressionValidationIndicator`
> (`objects/document/common/valid/`), used by the shared expression editors
> (`KotlinExpressionEditor`, `FormulaMapEditor`).

> **Script control flow.** The Script flavour has structured control flow —
> `continue` / `break` / `return` — as **completion signals, not exceptions** (`ScriptControlSignal`:
> `SkipIteration` / `FinishLoop` / `EndScript`, in `server/objects/script/api/`). A throwable would be
> caught by the engine's `Execution.recoverable {}` catch-all and rendered as a step failure
> (error-parked under pause-on-error); a signal instead is a pending field on `ScriptRunContext` that
> the spine (`runSteps`) short-circuits on and a targeted consumer clears — **zero kzen-lib change**,
> `logic-spec.md` untouched. A new **ControlStep** (`action: skipIteration | finishLoop`, targeting an
> enclosing loop via `loop:`) raises Skip/Finish; **ResultStep** gained `then: keepRunning | endScript`
> (default `keepRunning` = today's last-Result-wins; `endScript` raises `EndScript` after capturing the
> result). A loop (`ForEachStep` / `DoWhileStep`) consumes a signal targeting itself via
> `StepExecution.consumeLoopSignal`; a signal for an outer loop / the root propagates (the enclosing
> spine traces the passed-through container as Done-with-no-outcome and short-circuits). `EndScript`
> unwinds to `ScriptLogic.run` and never crosses a `host()` boundary (a hosted child runs in its own
> context), so it is a proper `return` from the current document. Signals are **release-local**: raised
> and consumed within one engine release, never captured/migrated — so an End-Script-terminated run goes
> terminal (no park) and is never replayed. Loop membership is **notation-driven**: a loop flags its body
> branch `rerun: true` (`meta.steps.rerun`), read by the shared `ScriptNestingAnalysis`
> (kzen-auto-common) — a third-party loop step opts into loop semantics declaratively, no shared-code
> edit.

> **Script move-to / Set Next Statement.** A settled (paused or **error-parked**) Script run
> can be repositioned to a target step **without executing the intervening steps** — backward = re-run from
> the target, forward = skip over — via `ServerLogicController.moveTo` / `/logic/moveTo`. It is realised as a
> **self-migration**: the engine carries the target as an opaque one-shot `Execution.moveTarget` through the
> `RunEngine.migrate` barrier (kzen-lib `Repositionable`), and Script interprets
> it at restore time where the outcome maps live — **no engine `when` over flavours** (a non-`Repositionable`
> Logic ignores the target and rebuilds at its existing frontier). `ScriptRunContext.restore` performs
> **outcome-set surgery** computed by the notation-driven `ScriptJumpAnalysis` (kzen-auto-common, layered on
> `ScriptNestingAnalysis`): the target and everything at/after it drop from the carried capture (so a jump to
> a loop step restarts it at iteration 0), the value-less pre-target steps become a **skip set**
> (short-circuited with no value and a new `StepTrace.State.Skipped`; a later reference to one error-parks via
> the existing `referencedValue` "No value produced" backstop), and the descend **ancestors** (an enclosing
> `IfStep`; a branch GROUP node on the path is filtered out — it is not an executed step) run — re-evaluating
> their conditions — with their `checkpoint` suppressed, so the paused rebuild
> parks at the target rather than the ancestor's boundary. A jump always recompiles from the current notation
> and shares the migrate barrier (an edit-then-jump takes both in one rebuild). **Loop bodies are out of scope
> v1**: a target inside a `rerun` branch is rejected (`canMoveTo` → `LogicRunResponse.Rejected`); a jump to
> the loop step itself is allowed.
> **Client affordance:** both move-to and breakpoints live in the Script's **execution margin** —
> an IDE/VBA-style gutter column reserved by `ScriptController`'s `paddingLeft` and painted by
> `ScriptExecutionMargin` (kzen-auto-js), which anchors a breakpoint band and the draggable next-to-run arrow on
> each step's HEADER row. Dragging the arrow is the only way to reposition the run (the per-step "Set next step
> here" header action is gone), and breakpoints are set by clicking the margin — step headers carry no execution
> control at all. Bands cover every *executable* step (`ScriptNestingAnalysis.orderedExecutableStepPaths`,
> kzen-auto-common), so `If` / `ForEach` / `DoWhile` headers are breakpointable and binding rows
> (`parameters`, `item`) are not.

## 2. Client-server graph synchronization

The browser holds a **mirror** of the server's notation graph, applies edits locally for instant UI feedback, and replays the same `NotationCommand` to the server over REST. CQRS means both sides converge by applying identical commands.

```
Browser                                          Server
─────────────────────────────────────────────    ───────────────────────────────────
  MirroredGraphStore                               LocalGraphStore (DirectGraphStore)
   ├─ DirectGraphStore  ◀── apply(cmd) ───┐         ▲
   │  (local mirror)                       │         │ apply(cmd)
   └─ ClientRestGraphStore ──HTTP─────────────┐   NotationCommandHandler ◀── POST/GET/PUT /command/...
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
6. Server `NotationCommandHandler` deserializes, applies to server-side graph store → server-side observers fire.
7. Server response confirms; if mismatch, client could resync (rare in practice — commands are deterministic).

The **observer pattern in kzen-lib** is what makes this work — both sides subscribe to the same event stream from their respective local stores. No diffing, no syncing logic beyond replaying the command.

**Gotcha — `MirroredGraphStore.apply` runs local + remote in parallel.** The two `apply` calls are wrapped in sibling `coroutineScope.async { ... }` blocks, so the local branch — which calls `publishSuccess(event)` → every observer's `onCommandSuccess` — executes concurrently with the in-flight remote POST. An observer that responds to a notation event by issuing its own remote query to the server can therefore race the original POST: the server may not yet have applied the command when the query arrives.

Two consequences:

- Observers should compute derived state from the event payload + their own local cache only. Don't call back to the server inside `onCommandSuccess` (e.g. construct a fresh empty model locally on an `AddedObjectEvent` rather than fetching it from the server).
- If an observer invalidates its cached state in response to an event, it must also publish the new state to *its* observers (typically the UI), or downstream consumers stay frozen on the pre-event model.

**Definition-error surfacing.** `GraphDefinitionAttempt.failures` is computed client-side too (`DirectGraphStore.graphDefinition()`) and already broadcast to every graph-store observer — surfacing definition failures needs no new detection machinery. kzen-auto funnels them through one shared helper, `DefinitionErrors` (kzen-auto-js `client/util/`): `all()` / `forDocument()` feed the global banner and the per-document panel (non-destructive — the editor still loads so the user can fix the problem; stage-side rendering in `StageErrorIndicator` / `StageObjectLocator`), and `runBlocker(attempt, root)` — the reason a root can't run, via `attempt.transitiveSuccessful`, the same predicate the server's `filterTransitive(root)` checks — folds into the existing `runnable` flag so every run button disables with the reason as tooltip; `ServerLogicController.start` wraps `filterTransitive` in a clean 400 as the server backstop. **Don't conflate the two absence channels**: `failures` = the object failed to *define* — always broken, safe to report unprompted; `transitiveFailures` / absence from `transitiveSuccessful` = defined fine but **pruned** over a dangling or required-but-empty reference — *not* always broken, because every saved Job worker is permanently pruned by design (its channel ports are blank until `JobChannelSynthesis` fills an in-memory run copy — see the Job note in § 1; Job `main` itself survives because `workers` is a weak `NestedList`). Report pruning only through a run-root-scoped blocker like `runBlocker`, never in an unprompted error list.

## 3. REST API surface

Routes are declared in `KzenAutoMain.kt` — one `route*` function per group, all called from `routeRequests` — and each dispatches into its **own handler**, constructed in `KzenAutoContext` (§ 4). There is no single god-handler: `NotationQueryHandler`, `NotationCommandHandler` (whose per-target command handlers live under `server/api/handler/command/` as `Notation{Document,Object,Attribute,Refactor,Resource}Commands`), `DetachedActionHandler`, `TaskHandler`, `LogicHandler`, `ObjectStableHandler`, `FileListingHandler`, `StorageHandler`, plus `api/IconCollectionHandler`. All path constants live in `CommonRestApi` (`kzen-auto-common/.../api/CommonRestApi.kt`), shared by both server and JS client so the two sides cannot drift.

| Group | Prefix | Example paths | Purpose |
|----|----|----|----|
| Notation query | `/scan`, `/notation/...`, `/notation-batch`, `/resource` | — | Read-side: scan tree, fetch one or many documents, read a resource blob |
| Notation commands | `/command/...` | `/command/document/create`, `/command/object/add`, `/command/attribute/upsert`, `/command/refactor/rename`, `/command/resource/add` | CQRS commands against the notation graph |
| Detached | `/action/...` | `/action/detached`, `/action/download` | Detached-paradigm one-shot actions; `/action/download` returns a file body with `Content-Disposition` |
| Task | `/task/...` | `/task/submit`, `/task/query`, `/task/cancel`, `/task/lookup` | Long-running background jobs (Task paradigm — no built-in document uses it; see § 1) |
| Logic | `/logic/...` | `/logic/status`, `/logic/events`, `/logic/startRun`, `/logic/startStep`, `/logic/run`, `/logic/step`, `/logic/stepOver`, `/logic/stepOut`, `/logic/moveTo`, `/logic/pause`, `/logic/cancel`, `/logic/request`, `/logic/breakpoints`, `/logic/setPauseOnError`, `/logic/trace-binary` | Step / step-over / step-out / move-to / pause / resume of a logic run (**all four flavours** — Script, Flow, Job, Report); `/logic/setPauseOnError` toggles error-parking mid-run; `/logic/events` is the **SSE push stream** of the same `LogicStatus` payload `/logic/status` returns (see the push gotcha below); `/logic/breakpoints` replace-sets the run's breakpoint elements (repeated `breakpoint` params, each a full `ObjectLocation`; the same params ride `/logic/startRun` / `/logic/startStep` so start-time breakpoints can't miss early steps); a refused start answers **400 with the reason as the plain-text body** (`LogicStartAttempt.Failed` — an in-progress run, or a compile failure naming the root and its cause), which the client renders above the document rather than swallowing; `/logic/moveTo` (Script only) repositions a settled run's pointer to a target step (`path` + `object`) without executing the intervening steps — see the Script move-to note in § 1 — returning `Rejected` for an unsupported target; `/logic/trace-binary?run=&hash=` is the **only raw-bytes route on this surface** — a screenshot blob addressed by content hash (`application/octet-stream`, `Cache-Control: public, immutable`, 404 when the run isn't retained or the hash is unknown) — see the trace-binary-by-handle note below |
| Stable identity | `/object-stable/...` | `/object-stable/snapshot` | The server's whole `ObjectLocation ↔ ObjectStableId` bimap, fetched once at client connect to `seed()` the client-side mapper (§ 4) |
| File listing | `/file-listing` | `/file-listing?directory=&filter=` | Server-side filesystem browse, backing the Report input picker |
| Storage | `/storage/...` | `/storage/summary`, `/storage/bundles`, `/storage/delete` | Managed on-disk areas — see § 6 |
| Icons | `/icon/...` | `/icon/material-symbols.json?icons=a,b,c` | Self-hosted Iconify collection, fetched on demand by name (see [`js-architecture.md` § 5](js-architecture.md#5-react-dsl-wrapper-layer-wrapreactkt)) |
| Job output | `/job/download` | — | Download a Job worker's output bundle |

Most endpoints are GET (idempotent commands carry their payload in the query string); large or text-heavy command bodies — notation upserts, list inserts, multi-value updates — also have PUT variants taking form parameters. There are no WebSocket channels; the **one** streaming endpoint is `/logic/events` (SSE — see the push gotcha below).

**Every structured response is pre-encoded, not streamed.** `KzenAutoMain` funnels JSON replies through a single `respondJson` helper that encodes with kotlinx (`Json.encodeToString`) and hands the finished string to `respondText`. This is deliberate and permanent, not transitional: `respondText` yields a fully-buffered `TextContent` that `install(Compression)` can gzip in place, whereas routing through `call.respond(dto)` with a `json()` converter would emit a streaming `WriteChannelContent` — the exact case that forces Compression to buffer the whole body *anyway* and logs a WARN per response. These bodies are finite documents, so buffered encoding is the correct, warning-free path. The `Json` instance is deliberately **stock** (`encodeDefaults = false`, `explicitNulls = true`), so a nullable property *with* a `= null` default is omitted from the wire while one *without* a default encodes as an explicit `null`.

**Responses are gzip/deflate-compressed** (Ktor `Compression` in `ktorMain`) — the win is the JSON trace/detached bodies whose base64-of-PNG screenshots dominate the byte volume. Two content types are **excluded**: `text/event-stream` (the `/logic/events` SSE stream — compression would buffer and break its incremental framing/flush) and `application/octet-stream` (the `resource` PNG route and binary downloads — already compressed). `minimumSize(1024)` skips the tiny control-verb responses. The kzen-shell proxy relays compression end-to-end unchanged (its CIO client installs no `ContentEncoding` plugin, so it forwards `Accept-Encoding` upstream and the gzipped body + `Content-Encoding` back verbatim).

**Trace binaries are referenced by content-addressed handle, not inlined.** A large binary trace value (today: a screenshot) no longer serializes as inline base64 in the trace JSON — the `RunEngineLogicTrace` projection replaces each `BinaryExecutionValue` with a `BinaryHandleExecutionValue` (`{type: binary-handle, run, hash, size, mime}`, kzen-lib), so `lookupRun` / `lookup` / `lookupRunHistory` carry only a `Digest.ofBytes(bytes).asString()` hash. The browser fetches each unique image **once** from `/logic/trace-binary?run=&hash=` (served by `RunEngineLogicTrace.lookupBinary`, which resolves the bytes from the retained engine's live map **and** film-strip history) and caches it by that immutable URL. This is a **trace-wire-only** transform: it is scoped to the projection seam (`toWireValue`), so a non-trace `BinaryExecutionValue` — e.g. the Target document's `ScreenshotTaker` detached result rendered directly — keeps its inline base64. Client-side, the single render choke point `StepImage.pngUrl` accepts the sealed `BinaryValue` supertype and builds the blob URL for a handle (else a base64 data URL); the one consumer that needs the actual bytes (`TargetController`'s locate-from-a-traced-screenshot) fetches them via `ClientRestApi.logicTraceBinaryBytes`. A stale handle (run no longer retained) 404s and the thumbnail falls back to blank, same as any cleared trace.

**Logic step/pause/resume is push-first, with an adaptive poll fallback** (this replaced a pure 1.5 s poll). The server returns a `LogicStatus` containing the current `LogicRunInfo` whose `LogicRunState` is one of `Running` / `Pausing` / `Paused` / `Stepping` / `Cancelling`; the UI repaints when it sees a `Paused` (or `Cancelling`) state. Frame state lives in `ServerLogicController`'s synchronized `LogicState` (volatile `running` / `paused` / `stepping` flags plus `pauseRequested` / `cancelRequested` / `settled`); execution thread runs in a plain `Thread`, not a coroutine.

The same `LogicStatus` reaches the client two ways, and **both apply through one code path** (`ClientLogicGlobal.applyStatus`) because the SSE frame carries the byte-identical payload the GET returns — push is a faster courier, not a second protocol:

- **Push** — `GET /logic/events` (Ktor `install(SSE)`). `ServerLogicController.observeStatus` is a controller-level, payload-free signal; the route's listener only `trySend`s into a `Channel.CONFLATED` (it runs on an engine dispatcher thread on the emit/log/park hot path, and sometimes under the controller monitor — it must never call `status()` or block). The route re-sends only when the serialized status **differs from what it last sent**, which is what makes over-announcing on the server free. Idle streams emit a named `ping` every 15 s.
- **Poll** — `/logic/status`, still armed while a run executes: 10 s while push is proven healthy, **1.5 s otherwise**. That 1.5 s is deliberately the pre-push cadence, so every failure of the stream degrades to the old behaviour rather than freezing the UI.

Three things are easy to get wrong here:

1. **The settle must be announced by the controller, not the engine.** The engine publishes its park *before* `settleAfterDrive` runs (which only happens once `awaitQuiescent` returned), and at that moment `stepping` is still set — so the engine-sourced signal reports `Stepping`, never `Paused`. `settleAfterDrive` therefore notifies explicitly; without it the client sits on "Stepping" until the fallback poll. Covered by `ServerLogicControllerStatusObserverTest`.
2. **Subscribe to the controller, never to an engine.** The engine is replaced on each `start()` and disposed on clear, and `RunEngine.shutdown()`/`dispose()` **do not clear observer lists** — so a per-consumer engine subscription would both miss the run it cares about and leak. The controller holds exactly one subscription per run (`LogicState.engineSubscription`, closed in `disposeState`) and fans out.
3. **Stream health is delivery-proven, never connection-proven.** A buffering intermediary opens an `EventSource` perfectly and delivers nothing, which is indistinguishable from a healthy idle stream — so `onopen` does not mark healthy; only an arriving message does (the server sends the current status on connect precisely to supply that probe). The two silent failures are then told apart by whether the connection *opened*: **opened but mute within 3 s ⇒ buffering ⇒ latch push off for the page** (`sseUnavailable`) — no amount of reconnecting fixes a proxy, and without the latch the probe's own teardown re-arms the loop, reconnects, and fails the probe again, forever; **never opened ⇒ server/network down ⇒ leave it alone**, since `EventSource` reconnects itself and a recovered backend re-promotes on its next delivered message. Silence for 45 s (3 lost heartbeats) on an established stream ⇒ reconnect, backstopped by the same probe.

**Connection budget.** kzen-auto is cleartext HTTP/1.1 on loopback and always will be (browsers require TLS for HTTP/2 — there is no cleartext h2c — and the shell's loopback-only contract rules out HTTPS). So the browser's ~6-connections-per-origin cap applies, **shared across every tab of the origin** — in the packaged product that origin is the shell: the launcher and every project. An `EventSource` holds one of those six for its lifetime, so the client subscribes **only while the tab is visible** and only while a run executes; a hidden tab closes its stream and re-syncs on `visibilitychange`. (WebSocket would escape the cap via a separate socket pool, but nothing needs it at one stream per window.)

**Through the kzen-shell proxy.** The proxy relays SSE unchanged (it streams via `respondBytesWriter` + `copyTo`, forwards `text/event-stream` and `Cache-Control`, re-frames chunked, and `EventSource` inherits the URL prefix because `ClientContext.baseUrl` is relative). It required exactly one fix: its shared Ktor **CIO** client had no `HttpTimeout`, so CIO's default `requestTimeout = 15000` — a wall-clock cap on the whole call context — silently truncated any response at 15 s (CIO's SSE/upgrade exemptions all miss, because the proxy forwards via a plain `prepareRequest`). It is now `INFINITE_TIMEOUT_MS` with a finite 60 s **socket** timeout as the real liveness check. This failure was invisible in the dev loop (which talks to kzen-auto directly) and is pinned by `ProxyHttpClientTimeoutTest` in kzen-shell.

**Trace values are served directly from the run's `RunEngine`** — the engine already holds the authoritative event log + per-node live map, so there is no second trace store. The former `LogicTraceStore` bridge (`ServerLogicController.mirrorTrace` / `onFrameClosed` / `onTraceReset`) was retired: the REST trace queries are answered by `RunEngineLogicTrace` (kzen-auto `server/exec/`), which projects the engine's node tree + history at query time and translates each flavour's within-node emit `Address` to its wire `LogicTracePath`. That translation is itself an **extension point** — a flavour contributes a `LogicTraceAddressRouting` (marker → path) that is autowired and indexed by marker, so the generic projector carries no flavour `when`; Job contributes `$job-progress` and Report `$trace-path`, while flavours emitting plain element addresses (Script, Flow) contribute none and fall through to the stable-id default. The projection also synthesizes each settled node's terminal `Outcome` onto a dedicated `$outcome` path, so a run's outcomes are readable as trace rather than only as run state. Traces are still `ObjectStableId`-keyed, so they survive document / step renames during *and after* a run (via the process-global `ObjectStableMapper`, see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#stable-identity-objectstablemapper)), but do not survive a JVM restart. A **settled run's engine is retained** for post-run trace review (pools stopped via `RunEngine.shutdown`, tree + history kept readable); `status()` reports it as no-active-run. **Starting a new run implicitly clears the prior run's trace** — `ServerLogicController.start` disposes the retained engine before compiling the new one (the same effect the old `logicTraceStore.clearAll()` had; the manual "Clear all traces" control disposes it via `clearRetainedTrace`), so a fresh run never shows stale per-step/per-vertex displays or screenshots.

**Gotcha — `LogicStatus` is versioned, and there is deliberately NO wall clock on the wire.** Until E5, `status()` stamped `time = Clock.System.now()` per call, and eight client sites keyed their trace/progress re-fetch on it — so the key differed on *every* poll and each tick re-pulled full, unwatermarked trace snapshots (~4 detached calls for a Script, 1–2 Flow, 2 Job), forever, even for a run parked at a breakpoint. `time` is gone; `LogicStatus` now carries:

- **`epoch`** (`Long`) — a controller counter for transitions a run's sequence cannot express: a run started, settled terminal, or a retained trace was cleared. It bumps **even with no active run**, which is load-bearing: `status()` reports `active == null` both before *and* after a "Clear all traces", so without the epoch the response is byte-identical across the clear and no view would ever repaint to empty (the old wall clock conveyed this by accident).
- **`LogicRunInfo.sequence`** (`Long`) — the run's monotonic trace high-water, straight off `RunState.sequence` (already built by `status()`, so it is free). A client holding sequence N has, by construction, nothing newer to fetch.
- **`structureVersion`** (`Long`) — a controller counter that moves on a genuine **execution-tree** change: an execution created/destroyed, a run-state transition, or a run lifecycle/clear event. Computed **lazily in `status()`** (already `@Synchronized`, off the SSE hot path) by diffing a cheap signature `(epoch, runId?, runState?, unfiltered snapshot.root node-id set)` against the last — `epoch` is folded in, so all three epoch transitions bump it too. Deliberately does **not** move on a plain frame-position advance within a stable execution set (a plain run's per-step motion), which is the whole trick: a structure-keyed consumer (the traced-document set, the execution tree) keys on it and stops re-fetching per emit. Present even with no active run, like epoch. ⚠️ The node-id set must be **unfiltered** (mirroring `RunEngineLogicTrace`'s execution walk, not the terminal-pruned `nodeToFrame`) — a child hosted+completed inside one Step-Over leaves the live frame but stays in `lookupRunExecutions`, so a frame-derived set would make the client's execution tree go stale.

Clients must key re-fetch on **`ClientLogicState.traceVersion()`** = `structureVersion|sequence` (a per-emit key, for `lookup` / `lookupRunHistory` / the run-merged snapshot), never a per-flavour notion of "changed", and never a timestamp; the **structure-keyed** queries `traced` and `lookupRunExecutions` instead gate on `structureVersion()` alone (their answer changes only on structure, so they re-fetch ~15-17×/run instead of once per publish ~46×). All three `Long`s serialize as **strings**, following the existing `LogicTraceEvent.sequence` convention that dodges JS `Long` round-tripping (see the Long-on-the-wire rule below). Live trace views (`lookup` / `lookupRun`) remain full snapshots — they are *sequence-gated* (not fetched unless the run moved), not incremental; making them delta-fetched would need engine-side reset tombstones, since `resetEmitted` clears live values a delta pass would miss and ghost.

**Long on the wire — the rule, refined by SER3.** The "serialize `Long` as a string" convention above is **not** a blanket rule, and its stated mechanism only ever applied to the **hand-written map codec**: there, a JSON number reaches Kotlin/JS via `JSON.parse` as a JS `Number` and cannot become a `Long`, so the codec stringified. A generated **kotlinx** codec has no such step — `AbstractJsonLexer.consumeNumericLiteral` accumulates the digits straight into a `Long` off the char stream, on JS as on JVM (pinned by `WireDtoSerializerTest` running under ChromeHeadless with an epoch-millis fixture). So:

- **Long-as-string** where the value can exceed JS's 2^53 safe integer, or where a documented contract fixes the form: `LogicStatus.epoch` / `sequence` / `structureVersion`, `LogicTraceEvent.sequence`. Under kotlinx these use the built-in `LongAsStringSerializer` rather than a manual `.toString()`.
- **Plain JSON number** where the domain bounds the value far below 2^53: file sizes (~1e12) and epoch millis (~1.75e12) are ~5000× under the limit. SER3's `StorageAreaInfo.sizeBytes` / `StorageBundleInfo.lastModifiedMillis` / `DataLocationInfo.size` are typed `Long` and ride the wire as numbers.

**Re-fetching on the right key is not enough — the fan-out itself is throttled** (`ClientLogicGlobal.publishStatus`). `traceVersion` correctly identifies *whether* there is anything new, but during an active run there always is: the engine bumps the sequence on every emit, so statuses arrive ~3.4/s, and each `publish()` fans out into ~4 REST round trips across the subscribed views (`lookup` / `lookupRunHistory` / `traced` / `lookupRunExecutions`). Measured on a 48 s script: 433 requests, ~9/s. Nobody can read three trace repaints a second, so the cost bought nothing.

So a status arriving **from the transport** — pushed or polled, one rule either way — publishes on this rule:

- **Structure changed** ⇒ publish immediately, and reset the throttle's clock. `ClientLogicState.structureVersion()` reads the server's `structureVersion` verbatim (pre-TP4 it was derived client-side as `epoch|runId|state`): a run started, settled, changed state — **every step boundary is one of these** — a trace was cleared, **or an execution was created/destroyed**. These are transitions the user is waiting on, so stepping stays instant and the epoch still drives the clear-repaint.
- **Sequence only** ⇒ `lodash.throttle` at 1 s (`statusPublishThrottleMillis`). Leading **and** trailing: `debounce` would be wrong here, since a run is a continuous stream and a trailing-only debounce fires nothing until the run *stops*. The trailing edge is what makes deferral safe — the last status of a burst always lands, so a value can't be stranded unshown by a run going quiet mid-flight.

Throttling at the publish rather than at each query is what makes this one decision instead of four: every view is publish-driven, so none needs its own clock. An earlier per-query gate was tried and removed — with N queries it needs N clocks, they drift out of step, and two callers asking the same question then miss the shared memo. Control verbs deliberately bypass the throttle (one per user action, must land at once), and `awaitStepSettled` reads `clientLogicState` directly rather than the published state, so no throttle can delay a settle.

`structureVersion` still **excludes raw frame position**, because a Script's frame position changes on essentially every step — folding it in would mark a plain run structure-changing throughout and defeat the throttle. But a *meaningful* frame transition — a stepped-over `RunStep` descending into its child — **creates a new execution**, so it now bumps `structureVersion` (via the node-id set) and that intermediate frame repaints immediately again (the per-emit intra-step animation E5 traded away, restored by TP4). Frames traversed *within* the child (its own position advancing, no new execution) still repaint at the throttle's cadence, not per emit — the documented, accepted trade (animating those would re-open the ~3.4-frames/s ×4-REST cost the throttle exists to remove). The remaining traffic (`lookup` + `lookupRunHistory`, both genuinely fresh per publish) is this design's floor; `traced` + `lookupRunExecutions` no longer ride it, having moved to the `structureVersion` gate above. **SER4 coordination:** `structureVersion` is a string-encoded `Long` sibling of `epoch`/`sequence` on `LogicStatus`; when SER4 migrates these DTOs to kotlinx it carries the field mechanically.

> **Script's step traces are transient emits (S7).** Because E4 made the live map the sole source of trace *values* (`RunEngineLogicTrace.nodeEntries`) while `lookupRunHistory` filters history down to log-style events (null address — the screenshot film strip), a retained `emit` was storage no reader ever consulted. `ScriptRunContext.emitStepTrace` therefore passes `retain = false` (kzen-lib `Execution.emit`, logic-spec §7): a `StepTrace` is the step's *current* state, so it updates the live view and its observers but never appends to history. Consequences worth knowing: **a Script contributes nothing to run history** — the history of a Script run is its `execution.log` film strip alone — so a long loop no longer grows it (it previously appended ~2 events per body step per iteration, forever), and a screenshot is stored **once** rather than also inside the `Running` and `Done` traces that carried it as their detail. Flow and Job emits are unaffected (they still retain; `FlowNotationTest.tracedMessages` reads them). Two other Script-side bounds ride along: step displays are capped at `TraceDisplay.maxScriptTraceChars` (**display only** — `stepValues` keeps the whole value, so downstream expressions and the result are unaffected), and a `ForEachStep` whose own value nothing reads collects no per-iteration outputs (`StepExecution.isValueReferenced`, derived at compile time by `ScriptValueReferences`).

## 4. Server-side composition root

The JVM-side analogue of the client's `ClientContext` (see [`js-architecture.md` § 4](js-architecture.md#4-service-layer-plumbing)). `KzenAutoContext` (`kzen-auto-jvm/.../server/context/KzenAutoContext.kt`) is built once via the self-initializing factory `KzenAutoContext.create(config)` at `KzenAutoMain.main` time (tests use `KzenAutoContext.forTest()`) and threaded explicitly to everything that needs it. Hand-constructed services (`ServerLogicController`, the REST handlers, …) receive their dependencies as constructor arguments. Graph-instantiated objects — which the notation system constructs from YAML, so they can't be hand-injected — receive runtime services through **construction-time dependency injection**: a constructor parameter annotated `@Service` (kzen-lib) is filled by kzen-lib's `GraphCreator` from a `GraphEnvironment` registry rather than from notation (see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md) for the `@Service` / `GraphEnvironment` mechanism). `KzenAutoContext` builds that `graphEnvironment` (keyed by each service's declared type) eagerly, and threads it as a plain `GraphEnvironment` into every server-side `createGraph` call. The construction cycle — a registered service (`serverLogicController`, `logicTrace`) is itself constructed *below* the environment — is broken by kzen-lib's provider registration: those two go in as `put(className) { service }` lambdas, memoized on first resolve, which happens at request/run time long after construction completes. (Correspondingly, nothing may resolve the environment *during* `KzenAutoContext` construction.) Consequently `Logic.execute` and `DetachedAction.execute` now carry only per-run handles (no host/context parameter), and a step like `FormulaStep` simply declares `@Service private val cachedKotlinCompiler: CachedKotlinCompiler`. This replaced the earlier `LogicHost` / `DetachedActionContext` marker-and-downcast role interfaces (and, before those, the `setGlobal` / `global()` process-global). There is **no process-global singleton.** **Each kzen-auto JVM process owns exactly one `KzenAutoContext` — there is no internal notion of multiple "projects" inside the server.** The project layer lives one level up in `kzen-launcher` / `kzen-shell`, which front-ends multiple JVM processes (see umbrella `AGENTS.md`); plugin authors should treat the in-process world as single-tenant.

The companion-object `init` block registers SPI metadata with kzen-lib's `ReflectionRegistry` via three calls — `KzenLibCommonModule.register()`, `KzenAutoCommonModule.register()`, then `KzenAutoJvmModule.register()` (the JS module is JS-only; not called here). The constructor then wires the service graph:

| Field | Type | Role |
|----|----|----|
| `notationMedia` | `ReadWriteNotationMedia(FileNotationMedia(GradleLocator), classpathOverlay)` | Disk-backed notation I/O with a read-only classpath overlay for bundled documents |
| `graphStore` | `DirectGraphStore` | In-process notation store; the canonical mirror target for `ClientRestGraphStore` |
| `detachedExecutor` | `ModelDetachedExecutor` | Detached-paradigm runner; instantiates each action from its own closure via `graphInstanceCache` |
| `modelTaskRepository` | `ModelTaskRepository` | Task-paradigm registry + runner (observer on `graphStore`); submits tasks instantiated via `graphInstanceCache` |
| `graphInstanceCache` | `GraphInstanceCache` | Closure-scoped, digest-keyed instance reuse shared by the two above (see § 5) |
| `serverLogicController` | `ServerLogicController` | Logic-paradigm state machine (see § 3 gotcha); runs **all four flavours** — Script, Flow, Job, Report; observer on `graphStore` (event-driven live-edit detection: coarse edit-dirty flag, then a precise closure content-digest compare via kzen-lib `GraphDefinition.transitiveDigest` — widened by `LinkedLogicDocuments` to span linked logic documents, i.e. weakly-referenced RunStep / RunLogic / RunWorker callees discovered from `is: ObjectLocation` notation metadata + `AutoConventions.isLogic`, recursively, so editing a paused caller's callee migrates the caller). The edge itself is defined once in kzen-auto-common by `LogicCallGraph`, which also serves the client's callee-picker as `transitiveCallers` (a document that already calls this one is not offered, since selecting it would close a cycle) |
| `objectStableMapper` | `ObjectStableMapper` (kzen-lib) | Process-global `ObjectLocation ↔ ObjectStableId` bimap; `graphStore.observe(...)` at boot + pre-warmed over the initial notation (see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#stable-identity-objectstablemapper)) |
| `logicTrace` | `RunEngineLogicTrace` (kzen-auto) | Serves the `LogicTrace` REST surface by projecting the controller's retained `RunEngine` at query time (E4); no separate store. Holds `objectStableMapper` + the per-flavour address routings + accessors onto `serverLogicController`'s retained run |
| `notationQueryHandler`, `notationCommandHandler`, `detachedActionHandler`, `taskHandler`, `logicHandler`, `objectStableHandler`, `fileListingHandler`, `storageHandler` | `server/api/handler/` | One handler per REST group (§ 3) — there is no single dispatch god-object |
| `targetLocator` | `TargetLocator` | Resolves a `TargetSpec` to a `WebElement` via registered `TargetTypeLocator`s (§ 7) |
| `workUtils`, `reportWorkPool`, `jobWorkPool` | work dirs | Sibling-relative working directories for run artifacts; the pools own the report / Job worker output + scratch trees |
| `cachedKotlinCompiler`, `kotlinSyntaxValidator`, `calculatedColumnEval` | scripting | Embedded Kotlin scripting for Script formula steps and report formula columns; the compiler owns a disk-backed, budgeted code cache (§ 6) |
| `scriptValidationCache`, `jobValidationCache` | validation caches | Digest-keyed `ScriptValidation` / Job-validation caches shared by the editor's detached validation and every run/hosted-child compile — the key covers the root document's closure ∪ linked logic documents' closures ∪ object-registry class lists, so validation re-runs only when notation it depends on changed |
| `filterIndex` | `FilterIndex` | Persistent column-value index backing report filtering |
| `definitionRepository` | `MultiDefinitionRepository` | Report-definer pool: `HostReportDefinitionRepository` over the built-ins (`CsvReportDefiner` / `TsvReportDefiner` / `TextReportDefiner`) plus `PluginReportDefinitionRepository` for JAR-loaded plugins |
| `managedStorageRegistry` | `ManagedStorageRegistry` | Every on-disk area the server owns, with budgets and eviction — see § 6 |

The Selenium / WebDriver browser handle is **no longer a context service**: it is a per-run resource keyed `"browser"`, registered **with the engine** — the kzen-lib `RunEngine` stores the live handle (value) alongside the disposal closer on the owning node (opened by `BrowserOpenStep` via `StepExecution.openResource`, read by the action steps via `StepExecution.resource(...)` — an ancestor-chain walk, so **any** hosted child, Script, Flow, or Job, can read the handle its host opened — disposed per its `closePolicy` when its owning document settles). There is no Script-side value registry (the former `ScriptRunResources` was deleted when the engine took over value storage); `WebDriverSupport` holds only the shared key + quiet-quit helper. This replaced the former `webDriverContext` process singleton (removes a global; allows concurrent runs). The `closePolicy` also selects *which* document owns the handle's lifetime — its own (`auto`/`manual`/`keepOnFailure`), the calling document one level up (`parent`/`parentKeepOnFailure`), or the whole run (`run`/`runKeepOnFailure`) — so a sub-script can open the SUT but bind it to the enclosing test (see kzen-lib `ResourceScope`). A `manual` handle also outlives its owning document's settle — the engine hands the registration up to the calling document (cascading toward the run root), so a browser opened in one sub-script stays readable and explicitly closeable by later sibling sub-scripts (logic-spec §6). Open resources also **survive live-edit migration**: the engine lifts registrations at the `migrate` barrier and re-adopts them by owning frame's stable identity (logic-spec §5), so pause → edit → resume keeps the same browser window.

Construction is self-initializing: the private `init()` (run by `create()`/`forTest()`) subscribes the task repository, `objectStableMapper`, **and `serverLogicController`** (its edit-dirty flag for live-edit detection) to the graph store via `graphStore.observe(...)` — the same observer mechanism described in § 2 — then pre-warms the mapper by iterating the boot notation, then registers every managed storage area via `initManagedStorage()` (§ 6). Its last step is `ServiceEnvironmentValidation.validate(graphEnvironment)` (kzen-auto-common, shared with the client): every `@Service` parameter type the `ReflectionRegistry` records is checked against the environment, so a service type nothing registered fails the boot naming the missing type *and* the classes declaring it, rather than surfacing later as a `Missing service` deep inside a graph-creation call. Validation is one-directional (registry → environment) — environment services consumed only by hand `resolve` calls or by downstream modules are legitimate. The shutdown hook calls `context.close()`, which cancels the active run — settling its root node disposes any run-scoped resources (an open browser) through the engine.

## 5. Backend execution model

Subpackages of interest:

- `kzen-auto-jvm/.../server/exec/` — the Logic-engine binding: `LogicCompiler` / `LogicDocument` / `RunEngineLogicTrace` at the root, and one subpackage per flavour (`script/`, `flow/`, `job/`, `report/`).
- `kzen-auto-jvm/.../server/service/exec/` — the non-Logic runners and the shared instance cache.
- `kzen-auto-jvm/.../server/objects/report/exec/` — the report record pipeline itself (see below).

Three runners, in descending order of how much work goes through them:

- **The Logic engine** — everything runnable. `ServerLogicController` compiles the document via `LogicCompiler` and drives a kzen-lib `RunEngine`; state, pause/step, tracing and live-edit migration are the engine's, not kzen-auto's. See § 1 for the flavours and [`../../kzen-lib/docs/logic-spec.md`](../../kzen-lib/docs/logic-spec.md) for the model.
- **`ModelDetachedExecutor`** (Detached paradigm) — runs `DetachedAction` objects (validators, `PluginDocument`, Report's browse/preview, simple admin actions) synchronously and returns results.
- **`ModelTaskRepository`** (Task paradigm) — tracks long-running executions; UI triggers create a `TaskModel` with a unique ID and clients poll status. Reports used to run here; nothing built-in does now (§ 1).

The latter two instantiate through **`GraphInstanceCache`**: the per-call graph build is scoped to the action's own transitive definition closure (after the `AutoConventions.serverAllowed` policy filter, which always comes first — client-only objects are never instantiated server-side) instead of the whole project, and the resulting instance is reused across calls. The cache key is the closure's notation digest (`GraphDefinition.transitiveDigest`) combined with each closure member's **inheritance-chain** notation digests — the chain part is load-bearing, since an ancestor is not a definition reference, so editing a user-editable prototype's inherited value would otherwise leave a stale instance served. Reuse makes the statelessness contract on `DetachedAction` / `DetachedDownloadAction` / `ManagedTask` binding: an instance may serve concurrent requests, so its fields must be immutable configuration and injected services, with per-request state in locals. An implementation that can't honour that declares `instanceCaching: "false"` on its archetype (read through the inheritance chain, so one declaration covers every instance) and gets a fresh instance per request.

### Report pipeline (LMAX Disruptor)

Reports are the most performance-sensitive flavour, so the record path uses the LMAX Disruptor pattern: a lock-free ring buffer where each pipeline stage is a Disruptor `EventHandler` running on its own thread. Since the Report → Logic port (§ 1) this pipeline runs **inside** a Logic run — `ReportRun` builds and drives it, calling `Execution.checkpoint` once per input poll so the whole thing pauses and cancels cooperatively like any other Logic — but the pipeline itself is unchanged.

```
ReportInputFramer ─▶ decode ─▶ filter ─▶ pivot ─▶ output
        │              │         │         │        │
        └─ each stage = one ReportPipelineStage thread
        └─ events pass through the ring buffer lock-free
```

`ReportPipelineStage` extends Disruptor's `EventHandler`. Stages don't block waiting on each other — they pull events from the ring buffer in order. This is why reports scale to large datasets with low overhead.

**Implication for editors:** never `Thread.sleep` or do blocking I/O inside a `ReportPipelineStage` — it stalls the ring buffer for everything downstream. Use async patterns (suspending functions, callbacks).

## 6. Managed storage

Subpackage: `kzen-auto-jvm/.../server/service/storage/`.

The server writes a fair amount to disk that is neither notation nor user data — compiled Kotlin, report run output, column indexes, Job worker output and scratch, logs. Left alone, those grow without bound and invisibly. **`ManagedStorageRegistry`** is the central catalogue that makes them visible and, where appropriate, bounded.

The convention is the point: **any service that resolves a new root under `WorkUtils` — or otherwise writes a server-owned directory — must register a `ManagedStorageArea` during `KzenAutoContext.init`**, even if its lifecycle is self-managed (then it registers *display-only*). Disk usage stays centrally accountable rather than being rediscovered later as a mystery directory.

A `ManagedStorageArea` declares an id, display name, description, whether it is `deletable`, and an optional `budgetBytes`. Its contents are **bundles** — the smallest independently deletable unit, typically one child directory (a compiled-code signature, a report run, an index entry). Two mechanisms hang off that:

- **Manual deletion.** `deleteBundle` releases in-memory state referencing the bundle (classloaders, open files) **first**, then deletes from disk, and refuses outright when the bundle is `active`.
- **LRU eviction.** A non-null `budgetBytes` enables `StorageLruEvictor`, which deletes least-recently-modified bundles until the area fits. Owners trigger it after growing the area and once at boot (so overshoot accumulated by a prior process is reclaimed). It is single-flight — concurrent triggers collapse into one sweep — and the caller must hold no per-bundle lock, since `deleteBundle` takes those itself.

The registered areas today: the Kotlin **code cache** (budgeted + evicting, the only one with an evictor attached), **reports**, the **filter index**, **Job worker output**, **Job scratch** (display-only — cleaned automatically when a run settles and at server start), and **logs** (display-only — self-pruning). Several are gated on an `anyRunActive` predicate so nothing deletes underneath a live run.

Surfaced over `/storage/summary` / `/storage/bundles` / `/storage/delete` (§ 3) via `StorageHandler`, and driven by the client's `StorageManagerController` in the ribbon. `/storage/delete` deliberately answers `text/plain` (error message or empty) rather than JSON, matching `deleteBundle`'s return contract.

## 7. Document types in the UI

> For the JS-client patterns that back each UI (Controller / Store / State / Observer; document folder convention; the custom `RComponent` wrapper), see [`js-architecture.md`](js-architecture.md).

Each subdirectory under `kzen-auto-js/src/jsMain/kotlin/tech/kzen/auto/client/objects/document/` corresponds to a document type with its own `*Controller`, mapping to a kzen-lib document with a particular `ObjectNotation` shape — the UI is a specialized editor for that shape. Two subdirectories are not document types: `common/`, a library of shared editors grouped by concern (`common/attribute/`, `edit/`, `raw/`, `signature/`, `valid/`, `dragdrop/` — `TextAttributeEditor`, `BooleanAttributeEditor`, `SelectAttributeEditor`, `MultiTextAttributeEditor`, `DefaultAttributeEditor`, `LogicSignatureEditor`, `YamlEditor`, …) reused across every document type; and `bridge/`, the `DocumentBridge` keyed-slot mechanism a document uses to share one store between separately-mounted header and body slots.

| Subdir | Document type | What it edits |
|--------|---------------|---------------|
| `report/` | Report | Interactive data queries: input selection, filtering, pivot, export. Runs as a Logic flavour (§ 1) |
| `flow/` | Flow | Node-and-edge DAG, run via the Logic paradigm (Run/Step/Pause); each node is a `FlowVertex`. `FlowController` + `FlowProgressStore` plus the shared vertex/edge rendering (`CellController`, `EdgeController`, `VertexController`) all live here (the legacy `GraphController` was retired) |
| `script/` | Script | Step-by-step procedural execution; trace view |
| `job/` | Job | Concurrent workers wired by auto-managed channels. `JobController` + `JobProgressStore` / `JobSummaryStore` / `JobValidationStore`, the channel editors (`JobChannelDisplay`, `JobChannelDefaults`), and the per-worker card registry (`JobCardRowRegistry`) |
| `data/` | Data schema | Field definitions / format |
| `plugin/` | Plugin registry | Register plugin JARs (by server filesystem path) |
| `registry/` | Object registry | A JVM class-name whitelist: the document registers host-classpath class names, and nothing more. `ObjectRegistryDocument.scan` resolves them (`Class.forName`, unresolvable names dropped) into the type-visibility set `FormulaStep` type inference reads when typing Script expressions |
| `target/` | Target | Element targeting: screenshot-crop visual matching today; selectors / expressions planned |
| `custom/` | CustomDocument | Hybrid editor: structured UI for prototype-driven object creation + raw-YAML escape hatch |
| `common/`, `bridge/` | *(not document types)* | Shared attribute editors, and the header/body store bridge |

When adding a new document type, expect to:

1. Define an `ObjectNotation` shape in `kzen-auto-common`.
2. Add a `*Controller` in `kzen-auto-js/.../objects/document/<type>/`.
3. Register the document type in the auto-generated module (regenerated, not hand-edited — see § 9).

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
retype into a capture+actuation surface then (a recorded design decision).

### `CustomDocument` — structured UI + raw-YAML escape hatch

`CustomDocument` has two editing modes, toggled in the header (`DocumentViewMode.View | .Raw`). The mode lives on `CustomState` in the single per-document `CustomStore`, which the header and body slots share through the `DocumentBridge` under `CustomStoreKey` (the former `CustomGlobal` is retired). **View mode** (`CustomView` + `CustomCreate`) is a structured UI for prototype-driven object creation: `CustomConventions.listPrototypes(graphNotation)` discovers every object marked `is: Prototype` anywhere in the graph and exposes them in the `+ Add` dropdown. UI-created objects nest under `main.objects/<Name>` (`CustomConventions.objectsAttributePath`), and their attributes are edited in place through `AttributeEditorManager`.

Two notation constructs drive the per-object affordances. The `exports` list on `main` (`{is: List, of: ObjectLocation, by: Nominal}`, `common-document.yaml`) selects which objects are visible to *other* documents — toggled per-object in the view (a glow on the card), and consumed cross-document by `SelectLogicEditor` (`CustomConventions.customDocumentExportedLogic`) and `SelectObjectEditor` (`customDocumentExports`). Separately, each prototype's `meta: tags:` (`detached` / `task` / `logic`) decides which run affordance a card offers. There is no `main.logic` list.

Both the per-object projection and the prototype list are computed by `CustomViewModel.Builder` (in kzen-auto-common, so it is unit-testable) — run by `CustomStore` once per notation event rather than per render, since the prototype scan is graph-wide and a prototype added in another document must still reach the picker. The Builder returns the *previous* instance when nothing changed, which is what keeps the extra runs publish-free and lets `RPureComponent` bail for untouched cards.

**Raw mode** (`DocumentRaw`) is a plain-text YAML editor — `<textarea>` with a synced line-number gutter (`YamlEditor` under `objects/document/common/edit/`), Ctrl/Cmd+S to Save — and enforces no nesting convention; any structure that parses is accepted. Comments and key order are **not** preserved across the parse → deparse round trip.

Both modes share the save flow: the client parses the full document via `YamlNotationParser.parseDocumentObjects` and dispatches `SetDocumentObjectsCommand` (the only bulk-replace command in the notation CQRS — see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md)) through the same `MirroredGraphStore` pipeline as every other command. No archetype or schema enforcement on save — power-tool semantics; broken references surface at the definition layer on next reload.

The raw-editing stack is document-agnostic and lives under `objects/document/common/raw/` (`DocumentViewMode`, `DocumentRawState`, `DocumentRawStore`, `DocumentRaw`, plus the `DocumentRawHost` seam each document store implements). `ScriptDocument` reuses it for a **Raw** view, but exposes it differently from Custom: instead of a header toggle, "Raw" is a tab in the shared ribbon (the `ScriptGroup_Raw` `RibbonGroup` in notation, with no `RibbonTool` children, so it offers no actions). Selecting a ribbon tab publishes its `RibbonGroup.viewMode` (`""` for action groups, `"Raw"` for the raw tab) onto the document's `DocumentBridge` under `ViewModeKey` — a self-constructing ribbon→stage channel, mirroring `InsertionKey` — and `ScriptController` subscribes and calls `ScriptStore.setViewMode`, switching the stage. This keeps the shared `RibbonController` document-agnostic (it forwards a notation-declared view id; it knows nothing about Script or raw). See [`js-architecture.md` § 4](js-architecture.md#4-service-layer-plumbing) for the bridge itself.

## 8. Plugin SPI

Subpackage: `kzen-auto-plugin/src/main/kotlin/tech/kzen/auto/plugin/`. **This is the public contract** for third-party plugins. Don't break it casually.

The plugin model is JAR-based, reflection-loaded:

1. A plugin JAR contains classes that subclass `ReportDefiner<Output>` (no-arg constructor required).
2. User enters the JAR's **server filesystem path** in the Plugin document (`jarPath` attribute). There is no browser upload — JAR-as-document-resource is an open decision, not a shipped feature.
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

## 9. Module registration

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
    tech.kzen.auto.server.objects.data.DataFormatDocument(args[0] as tech.kzen.auto.common.objects.document.data.spec.FieldFormatListSpec)
}
```

— the FQCN string is the key, the second argument is the ordered attribute-name list, and the lambda reflectively constructs an instance from positional args. Every type is rendered fully qualified (the generated file has no imports), so two constructor parameter types sharing a simple name can't collide. The kzen-lib runtime calls this when it needs to instantiate the object from notation.

`KzenAutoContext` is the JVM bootstrap composition root (one per process, threaded explicitly — not a global singleton). Its `init {}` block calls `KzenLibCommonModule.register()` followed by `KzenAutoCommonModule.register()` and `KzenAutoJvmModule.register()` — only the two JVM-side kzen-auto modules; `KzenAutoJsModule` is JS-only and not called from the JVM context. After this, kzen-lib's `ReflectionRegistry` knows how to instantiate every kzen-auto-defined object.

**When you add a new SPI class** (e.g. a new `ObjectDefiner` for a custom paradigm step):

1. Write the class in `commonMain`/`jvmMain`/`jsMain` as appropriate, with `@Reflect` on it.
2. `./gradlew build` (or just let your next compile pick it up) — KSP regenerates the matching `Module` automatically.

### JVM reflective fallback

Generated registrations are the primary path; on the JVM they are backed by kzen-lib's `ReflectiveClassMirror` (kotlin-reflect based), appended to the `GlobalMirror` delegate chain after the module `register()` calls in `KzenAutoContext.init` and in `AutoTestUtils`. `ReflectionRegistry.global` is consulted first and always wins, so the mirror only ever sees genuine misses; it serves `@Reflect`-annotated classes exclusively, and logs `Serving <class> by JVM reflection` for each one it resolves.

Its consumer today is **test fixtures**: the KSP module class name is module-global, so a test-source pass would emit a colliding `KzenAutoJvmModule` that shadows the main one — `kzen-auto-jvm` therefore disables `kspTestKotlin`, and `@Reflect` fixtures under `src/test` resolve through the mirror instead of a hand-written `ModuleReflection`. The mirror is constructed per `ClassLoader`, which is what will let a plugin JAR contribute `@Reflect` classes without a KSP pass of its own.

The log line is the parity signal — JS has no runtime reflection, so anything the client also instantiates must have a *generated* registration, and a production class showing up in that log means codegen is missing for it.

## Critical files

If you're new to kzen-auto, read these in order — they anchor the patterns above:

1. `kzen-auto-jvm/.../server/exec/LogicDocument.kt` — the flavour seam: how a document becomes runnable. Then `LogicCompiler.kt` beside it, which resolves and calls it without knowing any flavour.
2. `kzen-auto-jvm/.../server/objects/script/ScriptDocument.kt` → `server/exec/script/ScriptLogic.kt` — the reference flavour: a two-line archetype delegating to a compiler that produces a kzen-lib `Logic`.
3. `kzen-auto-common/.../paradigm/flow/api/FlowVertex.kt` — the cleanest SPI in the codebase, and how one Logic flavour describes a step.
4. `kzen-auto-common/.../api/CommonRestApi.kt` — every wire endpoint as a constant; shared by client and server.
5. `kzen-auto-js/.../service/rest/ClientRestGraphStore.kt` — the client-side REST proxy.
6. `kzen-auto-jvm/.../server/KzenAutoMain.kt` — Ktor route declarations and the response-encoding policy.
7. `kzen-auto-jvm/.../server/context/KzenAutoContext.kt` — the JVM composition root; the one place the whole service graph is visible.
8. `kzen-auto-jvm/.../server/service/impl/ServerLogicController.kt` — the run state machine, live-edit detection, and every control verb the engine deliberately doesn't own.
9. `kzen-auto-jvm/.../server/objects/report/exec/ReportPipelineStage.kt` — Disruptor handler.
10. `kzen-auto-plugin/.../ReportDefiner.kt` — plugin SPI entry point.
