# kzen-auto JS client architecture

Patterns and plumbing of `kzen-auto-js`. Complements [`architecture.md`](architecture.md), which focuses on paradigms / server execution / graph sync.

> **Snapshot:** as of `kotlin-wrappers 2026.7.1`. The custom `RComponent` wrapper layer absorbed most of the 2026.x migration surface; [§5](#5-react-dsl-wrapper-layer-wrapreactkt) is the canonical home for the bridges it carries and the migration checklist.

## 1. Top-level packages

Under `kzen-auto-js/src/jsMain/kotlin/tech/kzen/auto/client/`:

| Package | Role |
|---------|------|
| `objects/` | All React components, organized by domain. `objects/document/<type>/` for each document type; `objects/ribbon/`, `objects/sidebar/` for chrome. |
| `service/` | Composition root (`ClientContext`); global observer hubs under `service/global/`; REST clients under `service/rest/`; client-side logic under `service/logic/`; browser-persisted preferences under `service/storage/`. |
| `wrap/` | Custom React DSL wrapper (`wrap/React.kt` defines `RComponent`/`RPureComponent`) + adapters for MUI, Iconify, Lodash, react-select. **The layer most affected by wrappers upgrades.** |
| `api/` | `ReactWrapper` interface — composable React-render bridge used by the dynamic `DocumentController` mount machinery. |
| `codegen/` | KSP-generated module registration (`KzenAutoJsModule`), produced into `build/generated/ksp/js/jsMain/kotlin/` on every build. See [architecture.md § 9](architecture.md#9-module-registration). |
| `util/` | Misc client-side helpers. |

## 2. The Controller / Store / State / Observer quartet

Every UI feature in the codebase follows the same four-piece pattern.

| Piece | Role | Example |
|-------|------|---------|
| `<X>State` | Immutable data class. Composed hierarchically — a parent `*State` aggregates child `*State`s. | `ReportState` aggregates `ReportInputState`, `ReportFilterState`, `ReportOutputState`, etc. |
| `<X>Store` | Mutable state holder. Owns sub-stores. Implements `ClientStateGlobal.Observer` (if top-level). Defines its own nested `Observer` interface for downstream subscribers. | `ReportStore: ClientStateGlobal.Observer` owns `input/formula/filter/analysis/previewFiltered/output/run` sub-stores |
| `<X>Controller` | A `RComponent` / `RPureComponent` subclass (prefer `RPureComponent` + consumed-subset state — see [Preferred render scoping](#preferred-render-scoping-with-rpurecomponent-and-consumed-subset-state)). Registers as `<X>Store.Observer` in `componentDidMount`; calls `setState` on `onXyzState` callbacks. | `ReportController: RPureComponent<Props, ReportControllerState>, ReportStore.Observer` |
| `<X>Store.Observer` | A nested interface defining the one method downstream observers (typically the controller) implement. | `fun onReportState(reportState: ReportState)` |

Concrete mounting code (from `ReportController.kt`):

```kotlin
override fun componentDidMount() {
    store.didMount(this)            // registers controller as observer
}

override fun componentWillUnmount() {
    store.willUnmount()
}

override fun onReportState(reportState: ReportState) {
    setState {
        this.reportState = reportState
    }
}
```

The store side (from `ReportStore.kt`):

```kotlin
fun didMount(subscriber: Observer) {
    this.observer = subscriber
    mounted = true
    async {
        ClientContext.clientStateGlobal.observe(this)
    }
}
```

So the chain is: `ClientStateGlobal` → `ReportStore` (observes) → `ReportController` (observes) → `setState` → React rerender. No diffing logic; everything flows through method-call observer notifications.

### Preferred render scoping with `RPureComponent` and consumed-subset state

A store's `publish()` notifies **every** subscriber with the **full** published `*State` on **any**
change, and that `*State` is a fresh object each time. So the default tendency — extend `RComponent`
(which always re-renders on `setState`/parent render) and `setState { this.xState = xState }` with the
whole object — makes one entity's change re-render every sibling observer. This is the dominant source
of avoidable re-renders in this codebase (it also lights up *every* sibling in React DevTools'
"Highlight updates" overlay — see [§7](#7-render-discipline--editor-commit-patterns) on measuring
re-renders). **Prefer the following unless there's a concrete reason a component must always
re-render:**

1. **Extend `RPureComponent`, not `RComponent`.** `RPureComponent.shouldComponentUpdate`
   shallow-compares props and state (`===` per key, in `wrap/React.kt`) and bails when nothing changed —
   both on no-op `setState` and on a parent re-rendering with unchanged props.

2. **Store only the subset of the published state that `render` actually reads — never the whole
   `*State` object.** The whole object is a fresh reference every publish, so storing it defeats the
   shallow-equal (its `===` check always fails) and the component re-renders on every publish. Storing
   the consumed fields (usually primitives / already-stable references) lets `RPureComponent` bail when
   this component's slice is unchanged — *no manual guard needed; the setState can stay unconditional
   because the shallow-equal does the avoidance.* Example — `ScriptController` keeps `scriptLoaded` /
   `globalError` / `hasProgress` rather than the whole `ScriptState`, so a per-step expand/collapse
   publish doesn't re-render the Script subtree:
   ```kotlin
   // ScriptController.onScriptState — extract only what render consumes
   override fun onScriptState(scriptState: ScriptState) {
       setState {
           this.scriptLoaded = true
           this.globalError = scriptState.globalError
           this.hasProgress = scriptState.progress.hasProgress()
       }
   }
   ```

3. **If a consumed value is freshly *allocated* each publish, add a value-equality (`==`) early-return
   guard in the observer callback** (or stabilize the reference). Some derived values are rebuilt every
   call — e.g. `computeStepTraceInfo` returns a fresh `StepTrace` whose fields come from a stable map, so
   it is value-equal (`==`) but **not** `===`. A fresh reference in state defeats `RPureComponent`'s
   `===` shallow-equal, so the guard must skip the `setState` entirely when the slice is value-equal (a
   `===`/reference guard would never bail). `ScriptBranchDisplay.onClientState` shows the pattern:
   ```kotlin
   // Both values are freshly allocated each fire → must be ==, not ===
   if (state.stepLocations == stepLocations && state.dependencyEdges == dependencyEdges) {
       return
   }
   setState { /* … */ }
   ```
   Where a family of components consumes the *same* slice, hold the guard in a shared base rather than
   copying it: `ScriptStepDisplayBase` (`script/display/`) owns both store subscriptions and the guarded
   derivation for every step-body display (leaf card, If / ForEach / DoWhile), which therefore cannot skip
   it. A subclass with an extra slice guards only its own fields in `onClientStateExtra` /
   `onScriptStateExtra`; React batches the two partial `setState` calls into one render.

4. **Keep props passed to a pure child referentially stable** (cache the value object; reuse one
   handle instead of `Foo.Handle().also { … }` per render) so the child's `===` prop check can bail —
   otherwise a fresh prop cascades a re-render through the whole subtree. `ScriptController.renderMain`
   caches its `common` and reuses a single `StepDisplayManager.Handle` for this reason.

`ScriptStore.publish()` is deliberately left as a full broadcast — re-architecting it to per-location
targeting is invasive, and per-observer scoping (1–3) achieves the same end. The canonical
`ReportController` still stores the whole `ReportState` (older style); the script controllers above are
the reference for this scoping and the preferred shape for new and refactored components.

### Observer callbacks can fire with a stale `ObjectLocation`

A store `publish()` runs **before** the parent re-renders its children, so a child's `onScriptState` /
`onClientState` can fire while `props.objectLocation` still points at a step that was *just renamed or
deleted* (the parent hasn't yet handed it the new location). Two consequences:

- **Stable-id-keyed reads tolerate it.** `computeStepTraceInfo` / `objectStableMapper.objectStableId(…)`
  resolve a stale location fine — that's the point of stable ids (see
  [kzen-lib stable identity](../../kzen-lib/docs/architecture.md#stable-identity-objectstablemapper)).
- **Notation lookups throw.** `GraphNotation.inheritanceChain` / `firstAttribute` /
  `inheritanceParents` raise `IllegalArgumentException("Missing: <location>")` for a location absent from
  `coalesce`. So any observer callback that does a notation lookup on its *own* `objectLocation` must
  guard first — `if (objectLocation !in graphNotation.coalesce) return` (or fall back to the location
  unchanged). `RunStepArgumentsEditor` and `RunStepDisplay` both do this. Symptom when missed:
  `Observer error in ScriptStore: Missing: …` on rename/delete. This is also why per-RunStep
  representative-screenshot resolution lives in `ScriptProgressStore` (which derives each RunStep's
  owned executions from the run's execution tree and keys results by stable id), not in
  the thumbnail's observer keyed off a possibly-stale `props.objectLocation`.

### RunStep screenshot detail = the trace timeline, not per-step latest

A RunStep's detail is a **film strip of every screenshot under it** (all nested sub-scripts, all loop
iterations), in execution order, grouped/labelled by sub-script execution. It is built from the
**retained trace-event timeline** (kzen-lib's `lookupRunHistory`, value-agnostic — see
[kzen-lib trace](../../kzen-lib/docs/architecture.md)), not from per-step "latest frame" lookups: a
loop calls `Execution.resetEmitted` each iteration, which clears the live per-address values, so only
the history retains them. (Since S7 a Script's step traces are transient emits anyway, so its history
is the `execution.log` film strip alone — see [`architecture.md` § 3](architecture.md#3-rest-api-surface).)
`ScriptProgressStore` fetches the run's history incrementally (by a sequence watermark, resetting on a
new run) and publishes the accumulated `traceEvents`. Scoping a RunStep's strip to *only the
executions that step launched* (not every execution of the same sub-script document — two RunSteps can
invoke the same sub-script) uses the run's **execution tree** (`lookupRunExecutions`: per-execution
parent + call-site): on structural change (memoized — the derivation is keyed on the viewed execution
plus the cached executions list), the store seeds at the viewed document's resolved execution and
assigns each direct child execution — and its transitive subtree — to the RunStep named by the child's
call-site, publishing `runStepOwnedExecutions` (stable-id → owned `executionId`s) plus each step's
representative (its latest owned binary event, folded forward from each refresh's newly appended
events rather than rescanned). `ScriptProgressState.screenshotFramesByExecution` turns that into a
RunStep's strip — its owned binary events, grouped by `executionId` in first-appearance order — and is
the single definition of strip order, shared by `RunStepDisplay` (which labels the groups) and
`pageScreenshots` (which flattens them for the full-screen walk). Each frame is a `ScreenshotThumbnail` (its own
`ScreenshotFullscreen`), distinct from the location-keyed `StepImageThumbnail` / `StepImageFullscreen`
used for a single step's current frame on the main canvas.

## 3. Document folder convention

Every document type under `objects/document/<type>/` follows this layout:

```
objects/document/<type>/
├── <Type>Controller.kt              # top-level controller (RPureComponent + Store.Observer)
├── model/                           # state + store at this level
│   ├── <Type>State.kt               # data class
│   ├── <Type>Store.kt               # mutable holder, ClientStateGlobal.Observer
│   └── <Type>StateCache.kt          # optional perf cache
├── <subdomain1>/                    # e.g. input/, filter/, analysis/
│   ├── <Subdomain1>Controller.kt
│   ├── model/
│   │   ├── <Subdomain1>State.kt
│   │   └── <Subdomain1>Store.kt
│   └── … (recursive nesting allowed)
├── <subdomain2>/
├── widget/                          # shared mini-components scoped to this doc type
└── …
```

The `report/` document is the canonical example — seven sub-stores, deepest nesting. Other document types (flow/, script/, job/, data/, plugin/, registry/, target/) follow the same shape with fewer subdomains.

**`script/` is the reference for two patterns `report/` doesn't exercise:**

- **Keyed-map dynamic sub-state.** Report's sections are fixed (input / filter / formula / …), so each gets its own sub-store. A Script's *steps* are dynamic and unbounded, so per-step UI state can't be a fixed set of sub-stores. Instead `ScriptStepState` lives under `ScriptState.steps: Map<ObjectLocation, ScriptStepState>`, written through a single `ScriptStepStore` sub-store that prunes entries equal to the default (so the map holds only non-default steps and never accumulates orphans as steps are collapsed or deleted). Reach for this shape whenever per-entity UI state is keyed by a *dynamic collection* rather than a fixed layout — distinct from the network-backed `progressStore` / `validationStore` siblings, which are kept beside it precisely because they are server calls, not pure UI toggles.
- **React-Context store propagation, through one keyed bridge.** Rather than thread the store (or its sub-stores) down as props through every step-display layer the way Report does, `ScriptController` `provide`s it on the document's `DocumentBridge` under `ScriptStoreKey`, and class-component descendants read it with `installContextType` / `contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)` (helpers in `wrap/React.kt`). Script alone needs several such stores — `ScriptDragStoreKey`, `ScriptStepReferenceStoreKey` — and that is exactly why they share one context: a Kotlin/JS class component has a **single** `contextType` slot, so a context per store does not scale. This shape arrived in stages: first a module-level `WeakRef<ScriptStore>` global (`ScriptGlobal`), then a per-store React context (`ScriptStoreContext`), then the keyed bridge that folded every per-document context and `*Global` into one. Prefer a `BridgeKey` over a global or a bespoke context when a store must reach deeply, dynamically-nested descendants.

Both still follow the core quartet — `ScriptStore: ClientStateGlobal.Observer` owns `progressStore` / `validationStore` / `stepStore`; `ScriptController` observes the store and re-renders. (One wrinkle: `ScriptController` *also* observes `ClientStateGlobal` directly for `clientLogicState`, whereas the canonical `ReportController` folds that into `ReportState` and observes only its store — tracked in `audit/2026-05-29_script-refactor-review_4.8-xhigh.md` A01.)

`custom/` (the `CustomDocument` hybrid structured + raw-YAML editor) now **follows** the convention rather than being the exception to it: `model/` holds `CustomState` / `CustomStore` / `CustomStoreKey`, and `view/` holds `CustomViewStore` plus the view components (`CustomView`, `CustomCreate`, and the per-object cards under `view/obj/`). `CustomStore: ClientStateGlobal.Observer` is the one store; header and body are mounted in sibling slots and share it through the `DocumentBridge` under `CustomStoreKey`. Its Raw mode is the document-agnostic stack under `objects/document/common/raw/`, and the editor widget itself (`YamlEditor`) lives under `objects/document/common/edit/` because it's reusable.

The pure view-model pieces — `CustomObjectInfo`, `CustomViewModel`, `CustomViewExports`, `CustomViewReorder` — live in **kzen-auto-common** (`common/objects/document/custom/model/`) rather than jsMain, because they are React-free projections and index arithmetic that deserve unit tests; kzen-auto-js has no real JS test net. See [`architecture.md` § 7](architecture.md#7-document-types-in-the-ui) for the parse/save flow and power-tool semantics.

## 4. Service-layer plumbing

`service/ClientContext.kt` is the **composition root**. It's a singleton `object` that constructs the entire client-side service graph at load time:

```kotlin
object ClientContext {
    val restClient = ClientRestApi(baseUrl)
    val notationParser = YamlNotationParser()
    // … reducer, definer, creator, media …

    private val directGraphStore = DirectGraphStore(…)
    private val remoteGraphStore = ClientRestGraphStore(restClient, notationParser)
    val mirroredGraphStore = MirroredGraphStore(directGraphStore, remoteGraphStore)

    val navigationGlobal = NavigationGlobal()
    val clientLogicGlobal = ClientLogicGlobal(restClient)
    val clientStateGlobal = ClientStateGlobal()

    fun init() { /* register modules */ }
    suspend fun initAsync() { /* wire observers */ }
}
```

It also builds the `graphEnvironment` that fills `@Service` constructor parameters of graph-instantiated
objects. Because `KClass.qualifiedName` is unavailable in Kotlin/JS, its keys are **hand-written
`ClassName("…")` literals** that must match the FQNs KSP records for each declared `@Service` parameter
type. The last step of `initAsync()` is `ServiceEnvironmentValidation.validate(graphEnvironment)` (shared
with the JVM composition root): a typo or a package rename on either side fails the boot with the missing
type and its declaring classes rendered into `#root`, rather than surfacing later as a `Missing service`
deep inside a graph-creation call.

Notable globals under `service/global/`:

| Singleton | Role |
|-----------|------|
| `ClientStateGlobal` | Top observer hub. Observes `NavigationGlobal`, `ClientLogicGlobal`, `LocalGraphStore`. Publishes a `ClientState` to its subscribers (typically the top-level `<Doc>Store`s). |
| `NavigationGlobal` | URL routing → current `DocumentPath` + parameters. |
| `ExecutionIntentGlobal` | Tracks user-initiated execution intents (hover-to-highlight the element a run would touch). |
| `LogicValidationGlobal` | Validation results, published to by each paradigm and by the commit pipeline (under `service/logic/`). |

Exactly these are constructed in `ClientContext`; most are also registered in the `graphEnvironment` so a notation-instantiated controller can take one as an `@Service` parameter.

> **Not every `*Global` is a global.** `InsertionGlobal` and `ViewModeGlobal` are **per-document**
> instances now, lazily created by that document's `DocumentBridge` under `InsertionKey` /
> `ViewModeKey` (`objects/document/bridge/`) — the class names are historical. The bridge is a
> per-document, keyed communication hub between the **header (ribbon)** and the **stage (body)**,
> which are sibling React components with no shared parent state: `ProjectController` creates one per
> mounted document and hands it to both subtrees through a single React context, and both sides reach
> channels and stores by `BridgeKey`. A key that overrides `create()` is a dependency-free pub/sub
> channel the bridge builds on first touch; a key that leaves it null is an owner-provided store the
> owning controller `provide`s. This replaced the former per-document `*Global` singletons and the
> per-subtree `ScriptStoreContext` / `ScriptStepDragStoreContext`, so a class component spends only its
> single `contextType` slot on the bridge yet reaches everything by key — and a downstream document
> type can define its own `BridgeKey` without touching framework code.

`service/logic/` bridges the Logic paradigm — controllers that need step/pause/resume state subscribe through **`ClientLogicGlobal`**, which owns the SSE-push-with-adaptive-poll transport and the publish throttle (see [`architecture.md` § 3](architecture.md#3-rest-api-surface)) and publishes a **`ClientLogicState`**; `LogicRunFrames` derives frame/execution structure from it, `LogicValidationGlobal` carries validation results, and `ControlError` surfaces a refused control verb.

`mirroredGraphStore` is the bridge to kzen-lib's CQRS layer. See [`architecture.md` § 2](architecture.md#2-client-server-graph-synchronization).

`objectStableMapper` (kzen-lib `service/store/normal/`) is the **client-side** half of the rename-survival identity model: constructed in `ClientContext`, `seed()`ed from the server's snapshot via `restClient.objectStableMapperSnapshot()` at connect, then observing `mirroredGraphStore`. It lets stores translate a stable-keyed trace path back to the current `ObjectLocation` locally — so `ScriptStore`, for instance, refreshes progress only on a new run (logic-time change), not on every rename / insert / shift. See [`../../kzen-lib/docs/architecture.md`](../../kzen-lib/docs/architecture.md#stable-identity-objectstablemapper).

## 5. React DSL wrapper layer (`wrap/React.kt`)

The codebase uses **its own** `RComponent` / `RPureComponent` abstractions. `RComponent` extends modern `react.Component`; `RPureComponent` no longer can — `react.PureComponent` was removed in `kotlin-wrappers 2026.x`, so it's now re-implemented in-house. Post-migration, `wrap/React.kt` carries three bridges that fill the gap the now-defunct `kotlin-react-legacy` artifact used to provide:

```kotlin
abstract class RComponent<P : Props, S : State> : Component<P, S> {
    constructor() : super() { state = unsafeJso { init() } }
    constructor(props: P) : super(props) { state = unsafeJso { init(props) } }

    open fun S.init() {}
    open fun S.init(props: P) {}

    abstract fun ChildrenBuilder.render()
    override fun render(): ReactNode = Fragment.create { render() }
}

// In-house re-implementation of the removed react.PureComponent.
abstract class RPureComponent<P : Props, S : State> : Component<P, S> {
    // …same constructors + init + ChildrenBuilder.render() as RComponent…
    override fun shouldComponentUpdate(nextProps: P, nextState: S): Boolean =
        !shallowEqual(props, nextProps) || !shallowEqual(state, nextState)
}

// Replaces the removed `react.react` extension. Every ::class.react call site depends on this.
inline val <P : Props> KClass<out Component<P, *>>.react: ComponentType<P>
    get() = unsafeCast(js)

// Replaces removed react.createRef. useRef is a hook and class components can't call hooks,
// so class components instantiate a RefObject directly here.
fun <T : Any> createRef(): RefObject<T> = unsafeJso { current = null }

fun <S : State> Component<*, S>.setState(buildState: S.() -> Unit) {
    val partialState: S = unsafeJso { buildState() }
    setState(partialState)
}
```

So `setState { … }` is a Kotlin-friendly builder. `init` is the equivalent of constructor-time default state. `ChildrenBuilder.render()` is the modern (non-legacy) render API. The three migration bridges (`RPureComponent`'s shallow-compare `shouldComponentUpdate`, `KClass<…>.react`, top-level `createRef`) all live in this file. One trap: the `setState { … }` builder lambda runs on a **fresh empty `unsafeJso`, not a copy of current state** — it is write-only. `this.someField` inside the lambda is `undefined`, so read-modify-write (`tick += 1`) silently produces `NaN`; compute any value that depends on prior state in a `val` outside the lambda and assign it inside.

**2026.x migration checklist** (this section is the canonical home; kzen-launcher's `wrap/React.kt` mirrors this one, minus `createRef` — the launcher uses no refs). Copy `wrap/React.kt` verbatim for any future JS sibling; only the package path differs. Consumer-side breakage to expect:

- `key = "stringExpr"` must wrap in `react.Key(...)`.
- `ChangeEvent<C, T>` takes two mandatory type args; `event.target` returns the *second* arg and `event.currentTarget` the *first* — use `.currentTarget.checked` for `<input>` checkbox handlers.
- `setState { … }` needs an explicit `import tech.kzen.auto.client.wrap.setState` (the old `PureComponent.setState` extension is gone), and `import react.react` becomes `import tech.kzen.auto.client.wrap.react`.
- Build level: `useCommonJs()` and the mui-icons/BOM version match are load-bearing — see the umbrella `AGENTS.md` Toolchain pins.

Other adapters in `wrap/`:

- `wrap/material/*` — MUI component pass-throughs.
- `wrap/iconify/*` — Iconify icon DSL.
- `wrap/lodash.kt` — lodash debounce / throttle bindings.
- `wrap/select/reactSelectDsl.kt` — react-select adapter. Pre-migration this was the last live consumer of `kotlin-react-legacy` types; post-migration its legacy code is fully commented out and no active legacy imports remain anywhere in the JS client.

**MUI `Autocomplete` and `ClickAwayListener`.** Labelled select/filter dropdowns go through `ChildrenBuilder.muiAutocompleteField(...)` (`client/wrap/select/MuiAutocompleteField.kt`) — generic over the option type, floating material label, closes its listbox on click-away natively; prefer it over a bare `reactSelectField` when you want a label + native close. The wrapper API has sharp edges: `Autocomplete` is declared star-projected (`FC<AutocompleteProps<*>>`), so a generic invoke needs `Autocomplete.unsafeCast<FC<AutocompleteProps<T>>>()` — `Autocomplete<T> { }` does not compile; `options` is a `ReadonlyArray` (assign an `Array<T>` via `unsafeCast`); `onChange` is **4-arg** with `value: Any` (narrow with `unsafeCast<T>()`); `renderInput` is required, its params forwarded onto the inner `TextField.create { }` via a non-inline `Object.assign` helper (`js()` is illegal inside inline lambdas). For a floating popover, wrap it in `ClickAwayListener` — its document-level `click` fires *after* React's root-level `onClick`, so an insert path inside the popover wins the race and `onClickAway` becomes a guarded no-op; set `disablePortal = true` on an Autocomplete inside such a wrapper so listbox clicks count as "inside" (a `delay(1)`-deferred cancel does NOT work — it fires mid-click). And a wrapper bug still worked around: the generated `mui.material.ClickAwayListener` lacks `@JsName("default")`, importing a non-existent named export (`undefined` at runtime, React error #130) — the working form is the dedicated file `client/wrap/material/clickAwayListener.kt` with file-level `@file:JsModule("@mui/material/ClickAwayListener")` + `@JsName("default") external val`; a *declaration-level* `@JsModule` on the val does not work (it binds the module-namespace object and `@JsName` is ignored). Sanity-check any other missing-`@JsName("default")` wrapper component the same way before trusting it.

**Icons — self-hosted Iconify, fetched on demand by name.** All icons render through the single DSL `icon("material-symbols:<name>")` in `wrap/iconify/iconifyDsl.kt` (backed by `@iconify/react`'s `<Icon>`). There is **no build-time registry and no icon data in the JS bundle**: the full Material Symbols collection JSON ships as a JVM resource (`kzen-auto-jvm` `copyIconCollection` task → `/icons/material-symbols.json`), and `IconCollectionHandler` serves `GET /icon/material-symbols.json?icons=a,b,c` (the Iconify API protocol). `IconLoader` (installed from `ClientContext.init()`) registers an `@iconify/react` custom loader that fetches missing names from that endpoint via `ClientContext.baseUrl` (so it rides the kzen-shell proxy prefix), batched and cached; `IconLoader.preload()` warms the always-visible chrome icons at startup. An unknown name renders the `texture` fallback (server substitutes it) and is reported in the response's `not_found`.

- **To use an icon:** pick any name from the Material Symbols set (https://icon-sets.iconify.design/material-symbols/) and write `icon("material-symbols:<name>")` in code, or `icon: "material-symbols:<name>"` in notation YAML. No registry edit, no per-icon `@JsModule`. (In notation the value **must be quoted** — the kzen YAML parser treats an unquoted `a:b` scalar as a nested map; see `YamlParser.kt`.)
- **Name resolution** lives in `wrap/iconify/IconNames.kt`: a fully-qualified `set:name` passes through; a legacy `@mui/icons-material` PascalCase name (from notation saved against the old registry) is mapped via `legacyMaterialAlias` for backward compat; any other bare name is treated as a material-symbols name (kebab-cased).
- **Plugins / derived projects** get icons for free by naming `material-symbols:<name>`; a plugin needing another set hosts its own collection JSON and registers a second prefix via `setCustomIconsLoader(loader, "<prefix>")`.

## 6. Worked example: opening a report

1. **User clicks a report** — browser navigates to `/<sibling>/d/<path>`.
2. `NavigationGlobal` parses the URL, fires `Observer.handleNavigation(documentPath, params)`.
3. `ClientStateGlobal` (observing `NavigationGlobal`) updates its internal session state, publishes a new `ClientState` to its observers.
4. `ReportStore` (a `ClientStateGlobal.Observer`) receives `onClientState(clientState)`, derives a new `ReportState` keyed off the document path.
5. `ReportStore.observer.onReportState(reportState)` fires.
6. `ReportController` (the registered observer) calls `setState { this.reportState = reportState }`.
7. React rerenders. Sub-controllers (`ReportInputController`, `ReportFilterController`, …) each receive their portion of `ReportState` as props and similarly observe their sub-stores.

Mutation (user edits a value) follows the same lattice in reverse: a controller calls `store.someMethod(…)`; the store mutates internal state; the store may call `mirroredGraphStore.apply(command)` to ship a `NotationCommand` to the server (see `architecture.md` § 2); the resulting graph change re-enters the observer chain via `LocalGraphStore.Observer` on `ClientStateGlobal`.

## 7. Render discipline & editor-commit patterns

Hard-won rules for the class-component + broadcast-observer world of §2. Each is a pattern that looked right, shipped, and was corrected — treat them as defaults, not suggestions.

### Measuring re-renders: visits are not renders

React DevTools' "Highlight updates when components render" overlay draws a box around every fiber the reconciler *visited* during a commit — including ones that bailed via `shouldComponentUpdate` (React clones the child-fiber list of every ancestor on the path to the updated node, and the overlay highlights those bailed clones). All siblings flashing does NOT mean they re-rendered. Authoritative signals: a `componentDidUpdate` console log (fires only on actual render), the Profiler's "Ranked" view (filters bail-outs entirely), or a Profiler JSON dump (`changeDescriptions` / `fiberActualDurations`). For a live overlay that highlights only genuine renders, kzen-auto ships **react-scan** in dev: a dev-gated CDN `<script>` emitted by `kzen-auto-jvm/.../backend/Pages.kt`, gated on `KzenAutoConfig.developmentMode()` (set only by `FrontendDevelopment`/`BackendDevelopment`) — nothing lands in the JS bundle or JAR. Don't npm-bundle react-scan: its dep tree needs a newer Node than KGP pins, and the script tag sidesteps npm entirely.

### Hover reveals are CSS `:hover`, not React state

A hover-only visual reveal (show/hide via opacity) should be pure CSS, never an `isHovered` state field toggled by `onMouseEnter`/`onMouseLeave` — a hover-state field on one-of-many siblings makes React walk the shared parent down to the hovered child on every mouse move, and CSS produces no commit at all. Pattern: render the element always with `opacity = number(0.0)` and reveal via an `&:hover` rule; target descendants with stable `data-*` markers (emotion class names are hashed — never select on them); nested suppression (an outer slot must not reveal when a nested slot is the real hover target) uses `:has()` — see `ScriptStepSlot` / `StepNameEditor`. Exception: hover that drives more than visibility (z-index lift, expansion, measurement — e.g. `StepScreenshotPreview`) can legitimately stay JS.

### Conditional siblings remount unkeyed components

Conditionally inserting an element (error banner, spinner) as a *sibling before* an unkeyed stateful component shifts the child index; positional reconciliation sees a type mismatch and **remounts** the component, wiping its state via `init()` — e.g. an error banner above a form erasing the very input the user needed to correct. Always render the container element (empty when inactive) so sibling positions are stable, or give the stateful component a stable `key`. Prefer the stable container — it also protects every sibling below.

### Async-hydrating editors must not echo a write on mount

An attribute editor that hydrates async (`componentDidMount` → observe → `onClientState` → `setState`) sees an `undefined → loaded` state transition on its first `componentDidUpdate`; if that hook fires the edit command on value-change, it echoes the just-read value back as a no-op `UpsertAttributeCommand` on every mount/expand. That write is not harmless: `DirectGraphStore.apply` publishes unconditionally and `MirroredGraphStore` fans out to every graph-store observer *and* round-trips to the server — the symptom presents as "expanding X re-renders everything," but the cause is a notation+network write on a pure view action (check the Network tab). Guard it: either initialize state synchronously in `init()` from `ClientContext.clientStateGlobal.current()` (no transition at all — `SelectLogicEditor`), or add an `initialized` flag set by the hydration `setState` and early-return `componentDidUpdate` when `!prevState.initialized` (`SelectStepEditor`, `TargetSpecEditor`).

### Lifting a subscription changes sibling mount timing

A child subscribing to a global observer in its own `componentDidMount` via `async { observe(this) }` runs the subscription in a microtask *after* mounting; siblings' subscriptions batch through the same coroutine batch, so cross-mount invariants (a sibling's constructor setting a shared handle that another child's `componentDidMount` reads) hold implicitly. Lifting that subscription to the *parent* — child receives the value as a prop — compresses the timing: the consumer child mounts already-loaded and its `componentDidMount` fires before any sibling has subscribed at all, breaking those invariants. When making a controller prop-driven, check whether any sibling constructor publishes something the lifted subtree consumes on mount; if so, lift both or neither, or keep that one piece of state self-subscribed.

### Script step affordances: header right cluster vs execution margin

Per-step **editing/status** affordances go inline in `StepHeader.renderRightCluster`, immediately left of the Delete button (the settled order: validation icon · Skipped chip · type chip · delete · chevron) — never absolutely positioned over the step icon, and never in the card's left padding (both were tried and rejected). Right-cluster items are flex items with a small `marginRight`, `stopPropagation` on click (the card owns click-to-expand), and CSS `:hover`-reveal for idle-invisible affordances. **Execution control** (breakpoints, the draggable next-to-run arrow) lives only in the document-level execution margin (see [`architecture.md` § 1](architecture.md#1-paradigm-system), Script move-to) — step headers carry no execution control at all. Margin implementation invariants: both affordances anchor on the step's **header row** (`StepHeader.stepHeaderRowAttribute`, `data-step-header`, measured via `querySelector` — exact across leaf cards, branch header slabs, and `DoWhileStepDisplay` without a second registry); breakpoint bands are **fixed-height at the anchor**, never row-height (a container step's row DOM-contains every nested step's row, so full-height bands would overlap); drag hit-testing is **nearest anchor line**, not rect containment (containment returned the first row in parent-before-descendant order, so a step nested in an `If` branch could never be dropped on — don't reintroduce it); a pointer-up under 4 px of travel is a click that toggles the step's breakpoint (a breakpoint on the next-to-run step renders as a ring so the arrow stays readable); bands cover every executable step via `ScriptNestingAnalysis.orderedExecutableStepPaths` — binding rows (`parameters`, `item`) share `StepRowRefRegistry` with step rows, so that filter is load-bearing (`ScriptExecutableStepsTest`); and the margin does **not** observe `ScriptStore` — its root is `inset: 0`, so expand/collapse changes the stage height and its `ResizeObserver` re-anchors everything, one subscription fewer.

### Editor commits: `DebouncedSubmitter` semantics

Debounced attribute submits go through the shared `DebouncedSubmitter` (`objects/document/common/edit/`); its rules:

- **On unmount, `flush()` — never `cancel()`.** The debounce is buffering the user's pending edit before it reaches the server; cancelling silently discards their input. `cancel()` is only right for idempotent refresh-style callbacks with no user input at stake. Flushing from a non-suspend lifecycle method is fine even when the callback launches a coroutine — the launch is synchronous and the coroutine outlives the component.
- **No `.pending()` on a lodash debounce.** The bundled lodash's debounced function has no `.pending()` — it throws, and inside a `util.async` Promise the throw is swallowed, presenting as a stuck-busy indicator. `DebouncedSubmitter` detects a keystroke-re-armed-mid-commit with an explicit `scheduleSequence` counter instead; the `pending()` binding was removed from `wrap/Lodash.kt` as dead + broken. Don't reintroduce it.
- **Lossy round-trips compare semantically, and never overwrite user input.** Where the source of truth is a parsed model and deparse is lossy (the raw-YAML editor: `unparseDocument` drops comments/whitespace/key order), the "modified" check must be `parse(editorValue) != serverNotation` — not text equality — and the controller must never write a regenerated text form back into the editor, even on save success. Unparseable input counts as modified; external updates refresh the editor only when the user has no pending edits.

## 8. Critical files to read first

If you're starting work on the JS client cold:

1. `wrap/React.kt` — the wrapper layer; understand `RComponent`/`RPureComponent`/`setState`.
2. `service/ClientContext.kt` — composition root; service graph at a glance.
3. `service/global/ClientStateGlobal.kt` — top observer hub.
4. `objects/document/report/model/ReportStore.kt` — canonical store (with 7 sub-stores).
5. `objects/document/report/model/ReportState.kt` — canonical state composition.
6. `objects/document/report/ReportController.kt` — canonical top-level controller (mount, observer, render, plus the inner `Wrapper: DocumentController` for dynamic mounting).
7. `objects/ProjectController.kt` — top-level project orchestrator (where all document types get mounted via `DocumentController`s).
8. `objects/document/script/model/ScriptStepStore.kt` + `model/ScriptStoreKey.kt` — reference for keyed-map dynamic sub-state and bridge-keyed store propagation (see § 3).
9. `objects/document/bridge/DocumentBridge.kt` — the per-document header↔stage hub every document type reaches its stores and channels through (see § 4).
