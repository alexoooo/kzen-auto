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

> **Making a new logic document runnable in the UI.** Whether a document gets the Run / Step / Pause ribbon (and run-blocking on definition errors) is gated by `AutoConventions.isLogic(graphNotation, documentPath)` in kzen-auto-common, and it is **notation-driven**: `isLogic` returns true when the document's `main` archetype's inheritance chain reaches the common `Logic` marker. So every runnable paradigm's `main` archetype declares `is: [Document, Logic]` (see `common-document.yaml`; `Logic` is a bare `abstract` marker composed *alongside* `Document`, never as an `is: Document` intermediate, so the sidebar archetype registry's direct-`is`-match autowire is undisturbed). The server-side twin is the `LogicDocument` interface each paradigm's `main` archetype implements — the authoritative runtime guard, enforced by `LogicCompiler`'s `as? LogicDocument` cast. **A new logic document type is runnable from the UI as soon as its `main` archetype declares `is: Logic` and implements `LogicDocument` — no edit to `isLogic` (or any other shared code) is required** (it still needs its client `DocumentController`: `…-js.yaml`, `archetype:` + `ribbonController: RibbonController`). This was formerly a hardcoded OR over the four `*Conventions.isX(...)` checks — a god-object edit that bit Job (M1 step 5) — replaced by the `Logic`-marker inheritance query (CC-17).

> **Relocation (2026-05-28).** The `Logic` / `Task` / `Trace` / `Tuple` *types* moved to kzen-lib `tech.kzen.lib.common.exec.*` — see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#execution-model-logic--task--trace). What stays in kzen-auto is the paradigm *binding*: the REST wire surface (`paradigm/logic/LogicConventions`, the `/logic` and `/task` paths in `CommonRestApi`), the `ServerLogicController` / `ModelTaskRepository` server impls, and the documents themselves — e.g. a Script document implements kzen-lib's `Logic`. `paradigm/flow/` (the renamed dataflow paradigm) and `paradigm/detached/` did not move.

> **Flow (2026-06-19).** The former **Graph** / "Time Series" visual document (`GraphDocument`, driven by the bespoke `/dataflow/*` engine) was modernized into **Flow** (`server/objects/flow/FlowDocument`), which implements kzen-lib's `Logic`: one vertex execution = one step, run through `ServerLogicController` + `/logic/*`, with dedicated input/output vertices supplying parameters and a return value. The standalone dataflow execution engine — `ActiveDataflowRepository`, `VisualDataflowRepository`, `VisualDataflowLoop`, the `ActiveVisualProvider`/`VisualDataflowProvider`, and the `/dataflow/*` routes — was **retired** (clean rename, no `Graph` compat archetype). The low-level vertex/topology SPI (`FlowVertex`, `FlowMatrix`, `FlowDag`, `FlowUtils`, `VisualVertexModel`) and the vertex/edge rendering (`CellController`, `EdgeController`, `VertexController`) are **reused** by Flow — only the execution and visual-service layers were removed. The client `document/flow/FlowController` rebuilds per-vertex visual state from the logic trace store (`FlowProgressStore`), like `ScriptProgressStore`. **Full rename (2026-06-19):** the `paradigm.dataflow` and `objects.document.graph` / `server.objects.graph` packages and all `Dataflow*` class names were renamed to `paradigm.flow` / `objects.document.flow` / `server.objects.flow.vertex` and `Flow*` (`Dataflow`→`FlowVertex`, `DataflowMatrix`→`FlowMatrix`, `DataflowWiring`→`FlowWiring`, `VisualDataflowModel`→`VisualFlowModel`, etc.); notation archetype `Dataflow`→`FlowVertex`, `StreamDataflow`→`StreamFlowVertex`, `DataflowWiring`→`FlowWiring`. The unused `FolderDocument` was also removed.

> **Script control flow (2026-07-14).** The Script flavour gained structured control flow —
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
> edit. (execution-control plan phase XC4.)

> **Script move-to / Set Next Statement (2026-07-14).** A settled (paused or **error-parked**) Script run
> can be repositioned to a target step **without executing the intervening steps** — backward = re-run from
> the target, forward = skip over — via `ServerLogicController.moveTo` / `/logic/moveTo`. It is realised as a
> **self-migration**: the engine carries the target as an opaque one-shot `Execution.moveTarget` through the
> `RunEngine.migrate` barrier (kzen-lib `Repositionable`, execution-control phase XC1), and Script interprets
> it at restore time where the outcome maps live — **no engine `when` over flavours** (a non-`Repositionable`
> Logic ignores the target and rebuilds at its existing frontier). `ScriptRunContext.restore` performs
> **outcome-set surgery** computed by the notation-driven `ScriptJumpAnalysis` (kzen-auto-common, layered on
> `ScriptNestingAnalysis`): the target and everything at/after it drop from the carried capture (so a jump to
> a loop step restarts it at iteration 0), the value-less pre-target steps become a **skip set**
> (short-circuited with no value and a new `StepTrace.State.Skipped`; a later reference to one error-parks via
> the existing `referencedValue` "No value produced" backstop), and the descend **ancestors** (an enclosing
> `IfStep`) run — re-evaluating their condition — with their `checkpoint` suppressed, so the paused rebuild
> parks at the target rather than the ancestor's boundary. A jump always recompiles from the current notation
> and shares the migrate barrier (an edit-then-jump takes both in one rebuild). **Loop bodies are out of scope
> v1**: a target inside a `rerun` branch is rejected (`canMoveTo` → `LogicRunResponse.Rejected`); a jump to
> the loop step itself is allowed. (execution-control plan phase XC2; the client arrow affordance is XC3.)

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
| Logic | `/logic/...` | `/logic/status`, `/logic/events`, `/logic/startRun`, `/logic/startStep`, `/logic/run`, `/logic/step`, `/logic/stepOver`, `/logic/stepOut`, `/logic/moveTo`, `/logic/pause`, `/logic/cancel`, `/logic/request`, `/logic/breakpoints`, `/logic/trace-binary` | Step / step-over / step-out / move-to / pause / resume of a logic-paradigm run (Script **and Flow**); `/logic/events` is the **SSE push stream** of the same `LogicStatus` payload `/logic/status` returns (see the push gotcha below); `/logic/breakpoints` replace-sets the run's breakpoint elements (repeated `breakpoint` params, each a full `ObjectLocation`; the same params ride `/logic/startRun` / `/logic/startStep` so start-time breakpoints can't miss early steps); `/logic/moveTo` (Script only) repositions a settled run's pointer to a target step (`path` + `object`) without executing the intervening steps — see the Script move-to note in § 1 — returning `Rejected` for an unsupported target; `/logic/trace-binary?run=&hash=` is the **only raw-bytes route on this surface** — a screenshot blob addressed by content hash (`application/octet-stream`, `Cache-Control: public, immutable`, 404 when the run isn't retained or the hash is unknown) — see the trace-binary-by-handle note below |

Most endpoints are GET (idempotent commands carry their payload in the query string); large or text-heavy command bodies — notation upserts, list inserts, multi-value updates — also have PUT variants taking form parameters. There are no WebSocket channels; the **one** streaming endpoint is `/logic/events` (SSE, added E5 2026-07-15 — see the push gotcha below).

**Responses are gzip/deflate-compressed** (Ktor `Compression` in `ktorMain`, TP1 2026-07-16) — the win is the JSON trace/detached bodies whose base64-of-PNG screenshots dominate the byte volume. Two content types are **excluded**: `text/event-stream` (the `/logic/events` SSE stream — compression would buffer and break its incremental framing/flush) and `application/octet-stream` (the `resource` PNG route and binary downloads — already compressed). `minimumSize(1024)` skips the tiny control-verb responses. The kzen-shell proxy relays compression end-to-end unchanged (its CIO client installs no `ContentEncoding` plugin, so it forwards `Accept-Encoding` upstream and the gzipped body + `Content-Encoding` back verbatim).

**Trace binaries are referenced by content-addressed handle, not inlined** (TP3, 2026-07-16). A large binary trace value (today: a screenshot) no longer serializes as inline base64 in the trace JSON — the `RunEngineLogicTrace` projection replaces each `BinaryExecutionValue` with a `BinaryHandleExecutionValue` (`{type: binary-handle, run, hash, size, mime}`, kzen-lib), so `lookupRun` / `lookup` / `lookupRunHistory` carry only a `Digest.ofBytes(bytes).asString()` hash. The browser fetches each unique image **once** from `/logic/trace-binary?run=&hash=` (served by `RunEngineLogicTrace.lookupBinary`, which resolves the bytes from the retained engine's live map **and** film-strip history) and caches it by that immutable URL. This is a **trace-wire-only** transform: it is scoped to the projection seam (`toWireValue`), so a non-trace `BinaryExecutionValue` — e.g. the Target document's `ScreenshotTaker` detached result rendered directly — keeps its inline base64. Client-side, the single render choke point `StepImage.pngUrl` accepts the sealed `BinaryValue` supertype and builds the blob URL for a handle (else a base64 data URL); the one consumer that needs the actual bytes (`TargetController`'s locate-from-a-traced-screenshot) fetches them via `ClientRestApi.logicTraceBinaryBytes`. A stale handle (run no longer retained) 404s and the thumbnail falls back to blank, same as any cleared trace.

**Logic step/pause/resume is push-first, with an adaptive poll fallback** (E5, 2026-07-15 — this replaced a pure 1.5 s poll). The server returns a `LogicStatus` containing the current `LogicRunInfo` whose `LogicRunState` is one of `Running` / `Pausing` / `Paused` / `Stepping` / `Cancelling`; the UI repaints when it sees a `Paused` (or `Cancelling`) state. Frame state lives in `ServerLogicController`'s synchronized `LogicState` (volatile `running` / `paused` / `stepping` flags plus `pauseRequested` / `cancelRequested` / `settled`); execution thread runs in a plain `Thread`, not a coroutine.

The same `LogicStatus` reaches the client two ways, and **both apply through one code path** (`ClientLogicGlobal.applyStatus`) because the SSE frame carries the byte-identical payload the GET returns — push is a faster courier, not a second protocol:

- **Push** — `GET /logic/events` (Ktor `install(SSE)`). `ServerLogicController.observeStatus` is a controller-level, payload-free signal; the route's listener only `trySend`s into a `Channel.CONFLATED` (it runs on an engine dispatcher thread on the emit/log/park hot path, and sometimes under the controller monitor — it must never call `status()` or block). The route re-sends only when the serialized status **differs from what it last sent**, which is what makes over-announcing on the server free. Idle streams emit a named `ping` every 15 s.
- **Poll** — `/logic/status`, still armed while a run executes: 10 s while push is proven healthy, **1.5 s otherwise**. That 1.5 s is deliberately the pre-push cadence, so every failure of the stream degrades to the old behaviour rather than freezing the UI.

Three things are easy to get wrong here:

1. **The settle must be announced by the controller, not the engine.** The engine publishes its park *before* `settleAfterDrive` runs (which only happens once `awaitQuiescent` returned), and at that moment `stepping` is still set — so the engine-sourced signal reports `Stepping`, never `Paused`. `settleAfterDrive` therefore notifies explicitly; without it the client sits on "Stepping" until the fallback poll. Covered by `ServerLogicControllerStatusObserverTest`.
2. **Subscribe to the controller, never to an engine.** The engine is replaced on each `start()` and disposed on clear, and `RunEngine.shutdown()`/`dispose()` **do not clear observer lists** — so a per-consumer engine subscription would both miss the run it cares about and leak. The controller holds exactly one subscription per run (`LogicState.engineSubscription`, closed in `disposeState`) and fans out.
3. **Stream health is delivery-proven, never connection-proven.** A buffering intermediary opens an `EventSource` perfectly and delivers nothing, which is indistinguishable from a healthy idle stream — so `onopen` does not mark healthy; only an arriving message does (the server sends the current status on connect precisely to supply that probe). The two silent failures are then told apart by whether the connection *opened*: **opened but mute within 3 s ⇒ buffering ⇒ latch push off for the page** (`sseUnavailable`) — no amount of reconnecting fixes a proxy, and without the latch the probe's own teardown re-arms the loop, reconnects, and fails the probe again, forever; **never opened ⇒ server/network down ⇒ leave it alone**, since `EventSource` reconnects itself and a recovered backend re-promotes on its next delivered message. Silence for 45 s (3 lost heartbeats) on an established stream ⇒ reconnect, backstopped by the same probe.

**Connection budget.** kzen-auto is cleartext HTTP/1.1 on loopback and always will be (browsers require TLS for HTTP/2 — there is no cleartext h2c — and the shell's loopback-only contract rules out HTTPS). So the browser's ~6-connections-per-origin cap applies, **shared across every tab of the origin** — in the packaged product that origin is the shell: the launcher and every project. An `EventSource` holds one of those six for its lifetime, so the client subscribes **only while the tab is visible** and only while a run executes; a hidden tab closes its stream and re-syncs on `visibilitychange`. (WebSocket would escape the cap via a separate socket pool, but nothing needs it at one stream per window.)

**Through the kzen-shell proxy.** The proxy relays SSE unchanged (it streams via `respondBytesWriter` + `copyTo`, forwards `text/event-stream` and `Cache-Control`, re-frames chunked, and `EventSource` inherits the URL prefix because `ClientContext.baseUrl` is relative). It required exactly one fix: its shared Ktor **CIO** client had no `HttpTimeout`, so CIO's default `requestTimeout = 15000` — a wall-clock cap on the whole call context — silently truncated any response at 15 s (CIO's SSE/upgrade exemptions all miss, because the proxy forwards via a plain `prepareRequest`). It is now `INFINITE_TIMEOUT_MS` with a finite 60 s **socket** timeout as the real liveness check. This failure was invisible in the dev loop (which talks to kzen-auto directly) and is pinned by `ProxyHttpClientTimeoutTest` in kzen-shell. **Trace values are served directly from the run's `RunEngine`** (E4, 2026-07-15) — the engine already holds the authoritative event log + per-node live map, so there is no second trace store. The former `LogicTraceStore` bridge (`ServerLogicController.mirrorTrace` / `onFrameClosed` / `onTraceReset`) was retired: the REST trace queries are answered by `RunEngineLogicTrace` (kzen-auto `server/exec/`), which projects the engine's node tree + history at query time and translates each flavour's within-node emit `Address` to its wire `LogicTracePath` (the `$job-progress` / `$trace-path` marker routings, else the stable-id default). Traces are still `ObjectStableId`-keyed, so they survive document / step renames during *and after* a run (via the process-global `ObjectStableMapper`, see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#stable-identity-objectstablemapper)), but do not survive a JVM restart. A **settled run's engine is retained** for post-run trace review (pools stopped via `RunEngine.shutdown`, tree + history kept readable); `status()` reports it as no-active-run. **Starting a new run implicitly clears the prior run's trace** — `ServerLogicController.start` disposes the retained engine before compiling the new one (the same effect the old `logicTraceStore.clearAll()` had; the manual "Clear all traces" control disposes it via `clearRetainedTrace`), so a fresh run never shows stale per-step/per-vertex displays or screenshots.

**Gotcha — `LogicStatus` is versioned, and there is deliberately NO wall clock on the wire.** Until E5, `status()` stamped `time = Clock.System.now()` per call, and eight client sites keyed their trace/progress re-fetch on it — so the key differed on *every* poll and each tick re-pulled full, unwatermarked trace snapshots (~4 detached calls for a Script, 1–2 Flow, 2 Job), forever, even for a run parked at a breakpoint. `time` is gone; `LogicStatus` now carries:

- **`epoch`** (`Long`) — a controller counter for transitions a run's sequence cannot express: a run started, settled terminal, or a retained trace was cleared. It bumps **even with no active run**, which is load-bearing: `status()` reports `active == null` both before *and* after a "Clear all traces", so without the epoch the response is byte-identical across the clear and no view would ever repaint to empty (the old wall clock conveyed this by accident).
- **`LogicRunInfo.sequence`** (`Long`) — the run's monotonic trace high-water, straight off `RunState.sequence` (already built by `status()`, so it is free). A client holding sequence N has, by construction, nothing newer to fetch.
- **`structureVersion`** (`Long`, TP4 2026-07-16) — a controller counter that moves on a genuine **execution-tree** change: an execution created/destroyed, a run-state transition, or a run lifecycle/clear event. Computed **lazily in `status()`** (already `@Synchronized`, off the SSE hot path) by diffing a cheap signature `(epoch, runId?, runState?, unfiltered snapshot.root node-id set)` against the last — `epoch` is folded in, so all three epoch transitions bump it too. Deliberately does **not** move on a plain frame-position advance within a stable execution set (a plain run's per-step motion), which is the whole trick: a structure-keyed consumer (the traced-document set, the execution tree) keys on it and stops re-fetching per emit. Present even with no active run, like epoch. ⚠️ The node-id set must be **unfiltered** (mirroring `RunEngineLogicTrace`'s execution walk, not the terminal-pruned `nodeToFrame`) — a child hosted+completed inside one Step-Over leaves the live frame but stays in `lookupRunExecutions`, so a frame-derived set would make the client's execution tree go stale.

Clients must key re-fetch on **`ClientLogicState.traceVersion()`** = `structureVersion|sequence` (a per-emit key, for `lookup` / `lookupRunHistory` / the run-merged snapshot), never a per-flavour notion of "changed", and never a timestamp; the **structure-keyed** queries `traced` and `lookupRunExecutions` instead gate on `structureVersion()` alone (their answer changes only on structure, so they re-fetch ~15-17×/run instead of once per publish ~46×). All three `Long`s serialize as **strings**, following the existing `LogicTraceEvent.sequence` convention that dodges JS `Long` round-tripping (see the Long-on-the-wire rule below). Live trace views (`lookup` / `lookupRun`) remain full snapshots — they are *sequence-gated* (not fetched unless the run moved), not incremental; making them delta-fetched would need engine-side reset tombstones, since `resetEmitted` clears live values a delta pass would miss and ghost.

**Long on the wire — the rule, refined by SER3 (2026-07-17).** The "serialize `Long` as a string" convention above is **not** a blanket rule, and its stated mechanism only ever applied to the **hand-written map codec**: there, a JSON number reaches Kotlin/JS via `JSON.parse` as a JS `Number` and cannot become a `Long`, so the codec stringified. A generated **kotlinx** codec has no such step — `AbstractJsonLexer.consumeNumericLiteral` accumulates the digits straight into a `Long` off the char stream, on JS as on JVM (pinned by `WireDtoSerializerTest` running under ChromeHeadless with an epoch-millis fixture). So:

- **Long-as-string** where the value can exceed JS's 2^53 safe integer, or where a documented contract fixes the form: `LogicStatus.epoch` / `sequence` / `structureVersion`, `LogicTraceEvent.sequence`. Under kotlinx these use the built-in `LongAsStringSerializer` rather than a manual `.toString()`.
- **Plain JSON number** where the domain bounds the value far below 2^53: file sizes (~1e12) and epoch millis (~1.75e12) are ~5000× under the limit. SER3's `StorageAreaInfo.sizeBytes` / `StorageBundleInfo.lastModifiedMillis` / `DataLocationInfo.size` are typed `Long` and ride the wire as numbers.

**Re-fetching on the right key is not enough — the fan-out itself is throttled** (`ClientLogicGlobal.publishStatus`, 2026-07-15). `traceVersion` correctly identifies *whether* there is anything new, but during an active run there always is: the engine bumps the sequence on every emit, so statuses arrive ~3.4/s, and each `publish()` fans out into ~4 REST round trips across the subscribed views (`lookup` / `lookupRunHistory` / `traced` / `lookupRunExecutions`). Measured on a 48 s script: 433 requests, ~9/s. Nobody can read three trace repaints a second, so the cost bought nothing.

So a status arriving **from the transport** — pushed or polled, one rule either way — publishes on this rule:

- **Structure changed** ⇒ publish immediately, and reset the throttle's clock. `ClientLogicState.structureVersion()` reads the server's `structureVersion` verbatim (pre-TP4 it was derived client-side as `epoch|runId|state`): a run started, settled, changed state — **every step boundary is one of these** — a trace was cleared, **or an execution was created/destroyed**. These are transitions the user is waiting on, so stepping stays instant and the epoch still drives the clear-repaint.
- **Sequence only** ⇒ `lodash.throttle` at 1 s (`statusPublishThrottleMillis`). Leading **and** trailing: `debounce` would be wrong here, since a run is a continuous stream and a trailing-only debounce fires nothing until the run *stops*. The trailing edge is what makes deferral safe — the last status of a burst always lands, so a value can't be stranded unshown by a run going quiet mid-flight.

Throttling at the publish rather than at each query is what makes this one decision instead of four: every view is publish-driven, so none needs its own clock. An earlier per-query gate was tried and removed — with N queries it needs N clocks, they drift out of step, and two callers asking the same question then miss the shared memo. Control verbs deliberately bypass the throttle (one per user action, must land at once), and `awaitStepSettled` reads `clientLogicState` directly rather than the published state, so no throttle can delay a settle.

`structureVersion` still **excludes raw frame position**, because a Script's frame position changes on essentially every step — folding it in would mark a plain run structure-changing throughout and defeat the throttle. But a *meaningful* frame transition — a stepped-over `RunStep` descending into its child — **creates a new execution**, so it now bumps `structureVersion` (via the node-id set) and that intermediate frame repaints immediately again (the per-emit intra-step animation E5 traded away, restored by TP4). Frames traversed *within* the child (its own position advancing, no new execution) still repaint at the throttle's cadence, not per emit — the documented, accepted trade (animating those would re-open the ~3.4-frames/s ×4-REST cost the throttle exists to remove). The remaining traffic (`lookup` + `lookupRunHistory`, both genuinely fresh per publish) is this design's floor; `traced` + `lookupRunExecutions` no longer ride it, having moved to the `structureVersion` gate above. **SER4 coordination:** `structureVersion` is a string-encoded `Long` sibling of `epoch`/`sequence` on `LogicStatus`; when SER4 migrates these DTOs to kotlinx it carries the field mechanically.

> **Script's step traces are transient emits (S7, 2026-07-15).** Because E4 made the live map the sole source of trace *values* (`RunEngineLogicTrace.nodeEntries`) while `lookupRunHistory` filters history down to log-style events (null address — the screenshot film strip), a retained `emit` was storage no reader ever consulted. `ScriptRunContext.emitStepTrace` therefore passes `retain = false` (kzen-lib `Execution.emit`, logic-spec §7): a `StepTrace` is the step's *current* state, so it updates the live view and its observers but never appends to history. Consequences worth knowing: **a Script contributes nothing to run history** — the history of a Script run is its `execution.log` film strip alone — so a long loop no longer grows it (it previously appended ~2 events per body step per iteration, forever), and a screenshot is stored **once** rather than also inside the `Running` and `Done` traces that carried it as their detail. Flow and Job emits are unaffected (they still retain; `FlowNotationTest.tracedMessages` reads them). Two other Script-side bounds ride along: step displays are capped at `TraceDisplay.maxScriptTraceChars` (**display only** — `stepValues` keeps the whole value, so downstream expressions and the result are unaffected), and a `ForEachStep` whose own value nothing reads collects no per-iteration outputs (`StepExecution.isValueReferenced`, derived at compile time by `ScriptValueReferences`).

## 4. Server-side composition root

The JVM-side analogue of the client's `ClientContext` (see [`js-architecture.md` § 4](js-architecture.md#4-service-layer-plumbing)). `KzenAutoContext` (`kzen-auto-jvm/.../server/context/KzenAutoContext.kt`) is built once via the self-initializing factory `KzenAutoContext.create(config)` at `KzenAutoMain.main` time (tests use `KzenAutoContext.forTest()`) and threaded explicitly to everything that needs it. Hand-constructed services (`RestHandler`, `ServerLogicController`, …) receive their dependencies as constructor arguments. Graph-instantiated objects — which the notation system constructs from YAML, so they can't be hand-injected — receive runtime services through **construction-time dependency injection**: a constructor parameter annotated `@Service` (kzen-lib) is filled by kzen-lib's `GraphCreator` from a `GraphEnvironment` registry rather than from notation (see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md) for the `@Service` / `GraphEnvironment` mechanism). `KzenAutoContext` builds that `graphEnvironment` (keyed by each service's declared type) eagerly, and threads it as a plain `GraphEnvironment` into every server-side `createGraph` call. The construction cycle — a registered service (`serverLogicController`, `logicTrace`) is itself constructed *below* the environment — is broken by kzen-lib's provider registration: those two go in as `put(className) { service }` lambdas, memoized on first resolve, which happens at request/run time long after construction completes. (Correspondingly, nothing may resolve the environment *during* `KzenAutoContext` construction.) Consequently `Logic.execute` and `DetachedAction.execute` now carry only per-run handles (no host/context parameter), and a step like `FormulaStep` simply declares `@Service private val cachedKotlinCompiler: CachedKotlinCompiler`. This replaced the earlier `LogicHost` / `DetachedActionContext` marker-and-downcast role interfaces (and, before those, the `setGlobal` / `global()` process-global). There is **no process-global singleton.** **Each kzen-auto JVM process owns exactly one `KzenAutoContext` — there is no internal notion of multiple "projects" inside the server.** The project layer lives one level up in `kzen-launcher` / `kzen-shell`, which front-ends multiple JVM processes (see umbrella `AGENTS.md`); plugin authors should treat the in-process world as single-tenant.

The companion-object `init` block registers SPI metadata with kzen-lib's `ReflectionRegistry` via three calls — `KzenLibCommonModule.register()`, `KzenAutoCommonModule.register()`, then `KzenAutoJvmModule.register()` (the JS module is JS-only; not called here). The constructor then wires the service graph:

| Field | Type | Role |
|----|----|----|
| `notationMedia` | `ReadWriteNotationMedia(FileNotationMedia(GradleLocator), classpathOverlay)` | Disk-backed notation I/O with a read-only classpath overlay for bundled documents |
| `graphStore` | `DirectGraphStore` | In-process notation store; the canonical mirror target for `ClientRestGraphStore` |
| `detachedExecutor` | `ModelDetachedExecutor` | Detached-paradigm runner; instantiates each action from its own closure via `graphInstanceCache` |
| `modelTaskRepository` | `ModelTaskRepository` | Task-paradigm registry + runner (observer on `graphStore`); submits tasks instantiated via `graphInstanceCache` |
| `graphInstanceCache` | `GraphInstanceCache` | Closure-scoped, digest-keyed instance reuse shared by the two above (see § 5) |
| `serverLogicController` | `ServerLogicController` | Logic-paradigm state machine (see § 3 gotcha); runs Script **and Flow**; observer on `graphStore` (event-driven live-edit detection: coarse edit-dirty flag, then a precise closure content-digest compare via kzen-lib `GraphDefinition.transitiveDigest` — widened by `LinkedLogicDocuments` to span linked logic documents, i.e. weakly-referenced RunStep / RunLogic / RunWorker callees discovered from `is: ObjectLocation` notation metadata + `AutoConventions.isLogic`, recursively, so editing a paused caller's callee migrates the caller) |
| `flowMessageInspector` | `FlowMessageInspector` | Injected (via `graphEnvironment`) into Flow vertices for message inspection / tracing |
| `objectStableMapper` | `ObjectStableMapper` (kzen-lib) | Process-global `ObjectLocation ↔ ObjectStableId` bimap; `graphStore.observe(...)` at boot + pre-warmed over the initial notation (see [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#stable-identity-objectstablemapper)) |
| `logicTrace` | `RunEngineLogicTrace` (kzen-auto) | Serves the `LogicTrace` REST surface by projecting the controller's retained `RunEngine` at query time (E4); no separate store. Holds `objectStableMapper` + the per-flavour address routings + accessors onto `serverLogicController`'s retained run |
| `restHandler` | `RestHandler` | Dispatch target for every route in `KzenAutoMain` |
| `cachedKotlinCompiler`, `calculatedColumnEval` | scripting | Embedded Kotlin scripting for report formula columns |
| `scriptValidationCache` | `ScriptValidationCache` | Digest-keyed `ScriptValidation` cache shared by the editor's detached validation and every run/hosted-child compile — key covers the root document's closure ∪ linked logic documents' closures ∪ object-registry class lists, so validation re-runs only when notation it depends on changed |
| `definitionRepository` | `MultiDefinitionRepository` | Report-definer pool: built-in (`CsvReportDefiner` / `TsvReportDefiner` / `TextReportDefiner`) plus `PluginReportDefinitionRepository` for JAR-loaded plugins |

The Selenium / WebDriver browser handle is **no longer a context service**: it is a per-run resource keyed `"browser"`, registered **with the engine** — the kzen-lib `RunEngine` stores the live handle (value) alongside the disposal closer on the owning node (opened by `BrowserOpenStep` via `StepExecution.openResource`, read by the action steps via `StepExecution.resource(...)` — an ancestor-chain walk, so **any** hosted child, Script, Flow, or Job, can read the handle its host opened — disposed per its `closePolicy` when its owning document settles). There is no Script-side value registry (the former `ScriptRunResources` was deleted when the engine took over value storage); `WebDriverSupport` holds only the shared key + quiet-quit helper. This replaced the former `webDriverContext` process singleton (removes a global; allows concurrent runs). The `closePolicy` also selects *which* document owns the handle's lifetime — its own (`auto`/`manual`/`keepOnFailure`), the calling document one level up (`parent`/`parentKeepOnFailure`), or the whole run (`run`/`runKeepOnFailure`) — so a sub-script can open the SUT but bind it to the enclosing test (see kzen-lib `ResourceScope`). A `manual` handle also outlives its owning document's settle — the engine hands the registration up to the calling document (cascading toward the run root), so a browser opened in one sub-script stays readable and explicitly closeable by later sibling sub-scripts (logic-spec §6). Open resources also **survive live-edit migration**: the engine lifts registrations at the `migrate` barrier and re-adopts them by owning frame's stable identity (logic-spec §5), so pause → edit → resume keeps the same browser window.

Construction is self-initializing: the private `init()` (run by `create()`/`forTest()`) subscribes the task repository, `objectStableMapper`, **and `serverLogicController`** (its edit-dirty flag for live-edit detection) to the graph store via `graphStore.observe(...)` — the same observer mechanism described in § 2 — then pre-warms the mapper by iterating the boot notation. The shutdown hook calls `context.close()`, which cancels the active run — settling its root node disposes any run-scoped resources (an open browser) through the engine.

## 5. Backend execution model

Subpackages of interest:

- `kzen-auto-jvm/.../server/service/exec/` — generic execution wiring.
- `kzen-auto-jvm/.../server/objects/report/exec/` — report-specific execution.

Two main runners:

- **`ModelTaskRepository`** (Task paradigm) — tracks long-running executions. UI triggers create a `TaskModel` with a unique ID; clients poll status. Used for reports, automation runs.
- **`ModelDetachedExecutor`** (Detached paradigm) — runs `DetachedAction` objects (`PluginDocument`, simple admin actions) synchronously and returns results.

Both instantiate through **`GraphInstanceCache`**: the per-call graph build is scoped to the action's own transitive definition closure (after the `AutoConventions.serverAllowed` policy filter, which always comes first — client-only objects are never instantiated server-side) instead of the whole project, and the resulting instance is reused across calls. The cache key is the closure's notation digest (`GraphDefinition.transitiveDigest`) combined with each closure member's **inheritance-chain** notation digests — the chain part is load-bearing, since an ancestor is not a definition reference, so editing a user-editable prototype's inherited value would otherwise leave a stale instance served. Reuse makes the statelessness contract on `DetachedAction` / `DetachedDownloadAction` / `ManagedTask` binding: an instance may serve concurrent requests, so its fields must be immutable configuration and injected services, with per-request state in locals. An implementation that can't honour that declares `instanceCaching: "false"` on its archetype (read through the inheritance chain, so one declaration covers every instance) and gets a fresh instance per request.

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
