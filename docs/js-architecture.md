# kzen-auto JS client architecture

Patterns and plumbing of `kzen-auto-js`. Complements [`architecture.md`](architecture.md), which focuses on paradigms / server execution / graph sync.

> **Snapshot:** as of `kotlin-wrappers 2026.5.3` (migrated 2026-05-11, in lockstep with kzen-launcher). The custom `RComponent` wrapper layer absorbed most of the migration surface; see [§5](#5-react-dsl-wrapper-layer-wrapreactkt) for the bridges it now carries and [§7](#7-pre-refactor-inventory) for the historical pre-refactor inventory.

## 1. Top-level packages

Under `kzen-auto-js/src/jsMain/kotlin/tech/kzen/auto/client/`:

| Package | Role |
|---------|------|
| `objects/` | All React components, organized by domain. `objects/document/<type>/` for each document type; `objects/ribbon/`, `objects/sidebar/` for chrome. |
| `service/` | Composition root (`ClientContext`); global observer hubs under `service/global/`; REST clients under `service/rest/`; client-side logic under `service/logic/`. |
| `wrap/` | Custom React DSL wrapper (`wrap/React.kt` defines `RComponent`/`RPureComponent`) + adapters for MUI, Iconify, Lodash, react-select. **The layer most affected by wrappers upgrades.** |
| `api/` | `ReactWrapper` interface — composable React-render bridge used by the dynamic `DocumentController` mount machinery. |
| `codegen/` | Auto-generated module registration (`KzenAutoJsModule.kt`). Don't hand-edit — regenerate with `./gradlew :kzen-auto-jvm:runJsCodegen` (see [architecture.md § 8](architecture.md#8-module-registration)). |
| `util/` | Misc client-side helpers. |

## 2. The Controller / Store / State / Observer quartet

Every UI feature in the codebase follows the same four-piece pattern.

| Piece | Role | Example |
|-------|------|---------|
| `<X>State` | Immutable data class. Composed hierarchically — a parent `*State` aggregates child `*State`s. | `ReportState` aggregates `ReportInputState`, `ReportFilterState`, `ReportOutputState`, etc. |
| `<X>Store` | Mutable state holder. Owns sub-stores. Implements `ClientStateGlobal.Observer` (if top-level). Defines its own nested `Observer` interface for downstream subscribers. | `ReportStore: ClientStateGlobal.Observer` owns `input/formula/filter/analysis/previewFiltered/output/run` sub-stores |
| `<X>Controller` | A `RComponent` / `RPureComponent` subclass. Registers as `<X>Store.Observer` in `componentDidMount`; calls `setState` on `onXyzState` callbacks. | `ReportController: RPureComponent<Props, ReportControllerState>, ReportStore.Observer` |
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

The `report/` document is the canonical example — seven sub-stores, deepest nesting. Other document types (graph/, sequence/, data/, plugin/, registry/, feature/) follow the same shape with fewer subdomains.

`custom/` (the `CustomDocument` raw-YAML editor) is the exception to the layout — no sub-stores, no `model/` directory. The controller observes `ClientStateGlobal` directly, holds local `editorValue` / `serverNotation` state, and dispatches `SetDocumentObjectsCommand` through `MirroredGraphStore` on Save. The editor widget itself (`YamlEditor`) lives under `objects/document/common/edit/` because it's reusable; the doc-specific controller, `CustomConventions`, etc. live under `objects/document/custom/`. See [`architecture.md` § 6](architecture.md#6-document-types-in-the-ui) for the parse/save flow and power-tool semantics.

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

    val navigationGlobal = NavigationGlobal(visualDataflowLoop)
    val clientLogicGlobal = ClientLogicGlobal(restClient)
    val clientStateGlobal = ClientStateGlobal()

    fun init() { /* register modules */ }
    suspend fun initAsync() { /* wire observers */ }
}
```

Notable globals under `service/global/`:

| Singleton | Role |
|-----------|------|
| `ClientStateGlobal` | Top observer hub. Observes `NavigationGlobal`, `ClientLogicGlobal`, `LocalGraphStore`. Publishes a `ClientState` to its subscribers (typically the top-level `<Doc>Store`s). |
| `NavigationGlobal` | URL routing → current `DocumentPath` + parameters. |
| `ExecutionIntentGlobal` | Tracks user-initiated execution intents. |
| `InsertionGlobal` | Tracks insertions into the graph (e.g. paste, drop). |

`ClientLogicGlobal` (under `service/logic/`) bridges the Logic paradigm — controllers that need step/pause/resume state subscribe through it.

`mirroredGraphStore` is the bridge to kzen-lib's CQRS layer. See [`architecture.md` § 2](architecture.md#2-client-server-graph-synchronization).

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

So `setState { … }` is a Kotlin-friendly builder. `init` is the equivalent of constructor-time default state. `ChildrenBuilder.render()` is the modern (non-legacy) render API. The three migration bridges (`RPureComponent`'s shallow-compare `shouldComponentUpdate`, `KClass<…>.react`, top-level `createRef`) all live in this file — the umbrella `AGENTS.md` has more on why each was needed.

Other adapters in `wrap/`:

- `wrap/material/*` — MUI component pass-throughs.
- `wrap/iconify/*` — Iconify icon DSL.
- `wrap/lodash.kt` — lodash debounce / throttle bindings.
- `wrap/select/reactSelectDsl.kt` — react-select adapter. Pre-migration this was the last live consumer of `kotlin-react-legacy` types; post-migration its legacy code is fully commented out and no active legacy imports remain anywhere in the JS client.

**Gotcha — adding a new Material icon needs a dual registration.** Icons referenced from notation YAML `icon:` fields (e.g. `icon: Code` in `common-document.yaml`) are resolved by a hard-coded `when` block in `iconClassForName(name)` at `wrap/material/materialIconsDsl.kt`. Names not in the `when` fall through to `TextureIcon` — what looks like a "wrong icon" in the sidebar is usually a missing entry, not a bad YAML name. To register a new icon you need both: an `@JsName("<MuiName>") external class <Name>Icon: Component<IconProps, react.State>` declaration in `wrap/material/materialIcons.kt` (so the Kotlin/JS side knows about the `@mui/icons-material` export), AND a matching `"<Name>" -> <Name>Icon::class` arm in `materialIconsDsl.kt`. The TODO at the top of `materialIconsDsl.kt` flags this dual registration as a known maintenance burden; replacing it with the font-based `<Icon>name</Icon>` MUI pattern is unfinished work, not a deliberate design choice.

## 6. Worked example: opening a report

1. **User clicks a report** — browser navigates to `/<sibling>/d/<path>`.
2. `NavigationGlobal` parses the URL, fires `Observer.handleNavigation(documentPath, params)`.
3. `ClientStateGlobal` (observing `NavigationGlobal`) updates its internal session state, publishes a new `ClientState` to its observers.
4. `ReportStore` (a `ClientStateGlobal.Observer`) receives `onClientState(clientState)`, derives a new `ReportState` keyed off the document path.
5. `ReportStore.observer.onReportState(reportState)` fires.
6. `ReportController` (the registered observer) calls `setState { this.reportState = reportState }`.
7. React rerenders. Sub-controllers (`ReportInputController`, `ReportFilterController`, …) each receive their portion of `ReportState` as props and similarly observe their sub-stores.

Mutation (user edits a value) follows the same lattice in reverse: a controller calls `store.someMethod(…)`; the store mutates internal state; the store may call `mirroredGraphStore.apply(command)` to ship a `NotationCommand` to the server (see `architecture.md` § 2); the resulting graph change re-enters the observer chain via `LocalGraphStore.Observer` on `ClientStateGlobal`.

## 7. Pre-refactor inventory (historical)

Snapshot captured 2026-05-11 *before* the kotlin-wrappers `2025.12.11` → `2026.5.3` bump landed the same day. Kept here as a record of how the surface looked going in.

| Metric | Count |
|--------|-------|
| Total `.kt` files in `kzen-auto-js` | 161 |
| Files using the custom `RComponent`/`RPureComponent` | ~50–70 classes across ~55 files |
| Files using `setState` | 55 (262 occurrences total) |
| Files using `react.FC` / `useState` / `useEffect` | **0** — fully class-based |
| Files importing **kotlin-react-legacy types** directly (`RBuilder` etc.) | **1** (`wrap/select/reactSelectDsl.kt`) |
| Files using string-literal `key = "..."` | 2 — `objects/document/report/output/OutputTableController.kt`, `objects/document/graph/GraphController.kt` |
| Files using single-type-arg `ChangeEvent<C>` | 2 — `objects/document/common/AttributePathValueEditor.kt`, `objects/document/graph/edit/AttributePathValueEditorOld.kt` |
| `…Old.kt` files (stale, candidates for deletion before refactor) | 5 under `objects/document/graph/edit/` |
| Top builder/setState hotspots | `objects/document/feature/FeatureController.kt` (14), `objects/document/graph/GraphController.kt` (11), `objects/ProjectController.kt` (11) |

**Implications for the wrappers bump (what we expected):**

- The custom `RComponent` wrapper already uses modern `react.Component` — class components don't need to become functional components just because of the wrappers bump.
- Direct legacy-type breakage is **one file**, not the codebase. The catalog removal of `kotlin-react-legacy` still requires either keeping it as an explicit dependency (if available) or rewriting `reactSelectDsl.kt`.
- Real surface area: the 4 files with `key` / `ChangeEvent` breakage, the 1 legacy file, plus whatever the wrappers bump touches incidentally in MUI / cssom / unsafeJso / etc. — the latter only knowable by attempting the bump.

**What the migration actually touched (post-2026-05-11):**

The "≈4 files plus 1 legacy" estimate held for the categories that were inventoried, but the migration also surfaced three larger categories the inventory missed entirely:

- **~30 `key = "stringExpr"` sites** (the inventory found 2). The new wrappers require wrapping in `react.Key(...)`.
- **~60 files** needed an explicit `import tech.kzen.auto.client.wrap.setState` — `setState { … }` used to resolve via a removed `PureComponent.setState` extension, so the inventory's "55 files using setState" all needed an import touched.
- **~73 files** needed `import react.react` swapped to `import tech.kzen.auto.client.wrap.react` after the `KClass<…>.react` bridge moved in-house.

Plus the three §5 in-house bridges (`RPureComponent`, `KClass.react`, `createRef`) had to be authored fresh.

Lesson for the next refactor: anchored grep counts are fine for the categories you know to search, but they only tell you about the breakage categories you anticipated. A scratch build against the candidate library version surfaces the unknown unknowns faster than incremental inventory.

## 8. Critical files to read first

If you're starting work on the JS client cold:

1. `wrap/React.kt` — the wrapper layer; understand `RComponent`/`RPureComponent`/`setState`.
2. `service/ClientContext.kt` — composition root; service graph at a glance.
3. `service/global/ClientStateGlobal.kt` — top observer hub.
4. `objects/document/report/model/ReportStore.kt` — canonical store (with 7 sub-stores).
5. `objects/document/report/model/ReportState.kt` — canonical state composition.
6. `objects/document/report/ReportController.kt` — canonical top-level controller (mount, observer, render, plus the inner `Wrapper: DocumentController` for dynamic mounting).
7. `objects/ProjectController.kt` — top-level project orchestrator (where all document types get mounted via `DocumentController`s).
