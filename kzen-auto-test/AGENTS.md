# kzen-auto-test — AI agent guide

Blackbox end-to-end self-test for kzen-auto. Dogfoods kzen-auto's own Script browser-automation feature to drive a separate kzen-auto instance. Lives as a `kzen-auto` subproject (sibling of `kzen-auto-jvm`, `kzen-auto-js`, etc.), so all invocations below assume the working directory is `kzen-auto/`.

## Architecture

Two JVM processes participate; the tester's Script orchestrates the second.

1. **Tester** — `tech.kzen.auto.test.TesterMain` (this module's entry point). Same Ktor surface as vanilla kzen-auto plus the SUT-lifecycle Step catalogue (Start/Stop). Launch-context-independent: it locates the `kzen-auto-test/` module root from its own code source (classes dir or jar) and resolves the test-suite Script YAMLs at `src/main/resources/notation/test-suite/` against it, so the working directory doesn't matter. Also hosts the Chrome WebDriver. Launched from a bare IDE gutter run (interactive development; see Workflows) or by `SelfTestBase.startTester` inside the `selfTest` Gradle task (CI).
2. **SUT (System Under Test)** — vanilla `kzen-auto-jvm-*.jar`, spawned by `StartKzenAutoStep` inside the tester's Script YAML. Cwd is a per-run temp dir copied from `fixtures/<name>/`. Killed by a matching `StopKzenAutoStep` (or the tester's JVM shutdown hook if the script aborts). It is also spawned with a **managed-child lifeline** (`--managed.lifeline=stdin --parent.pid=<tester>`) so it self-reaps if the tester dies for any reason — see the shutdown-hook note below.

The tester sees the test suite via kzen-lib's `GradleLocator` (`kzen-lib-jvm/.../GradleLocator.kt`) pointed at an explicit module root: `TesterMain` locates it with `GradleLocator.moduleRootOfCodeSource` (shared kzen-lib helper — walks up from a class's code source to the directory containing `src/main/resources/notation`) and passes `--module.root=<located root>` (a general kzen-auto-jvm arg, parsed into `KzenAutoConfig.moduleRoot`), bypassing the locator's cwd heuristic. The SUT sees only its (initially empty) fixture project via the unchanged cwd heuristic (its cwd is the per-run fixture temp dir), alongside kzen-auto's classpath-bundled system notation.

`TesterMain` pre-registers `KzenAutoTestModule` (KSP-generated from `@Reflect`-annotated step classes) into `ReflectionRegistry.global` before delegating to `tech.kzen.auto.server.main`, so the tester's notation loader can resolve `is: StartKzenAutoStep` / `is: StopKzenAutoStep` references in the test-suite YAMLs.

## Step catalogue

Tester-only steps registered by this subproject:

- **`StartKzenAutoStep`** (`name: String`, `fixture: String`, `port: Int`) — Copies `<fixture>` (resolved against the `@Service`-injected `KzenAutoConfig.moduleRoot`; cwd-relative only if no module root was given) to a temp dir, spawns `java -jar <kzenAutoJar> --server.port=<port>` against it, blocks until HTTP 200 on `/`. Registers the handle in `KzenAutoSubprocessRegistry` under `name`. Reads the SUT jar path from system property `kzenAutoJar` (set by the `selfTest`/`runTester` Gradle tasks; when absent, `TesterMain` defaults it to the newest locally-built `kzen-auto-jvm/build/libs/kzen-auto-jvm-*.jar`).
- **`StopKzenAutoStep`** (`name: String`) — Looks up the named subprocess, shuts it down gracefully via the stdin lifeline (hard-kills only if it overstays 15s), deletes the temp dir. No-op if not found (traces a warning).

The tester JVM installs a shutdown hook that calls `KzenAutoSubprocessRegistry.closeAll()` — a graceful reaper for clean exits (script abort, IDE Stop). **The OS-agnostic backstop is the managed-child lifeline**: every SUT (and the tester itself, when spawned by `selfTest`) is launched with `--managed.lifeline=stdin` and `--parent.pid=<parent>`, so it self-terminates on stdin EOF (the OS closes the inherited pipe when the parent dies — on *any* platform, including a Windows `taskkill /F` that skips shutdown hooks) or via a `ProcessHandle.onExit` watchdog backup. The lifeline lives in `KzenAutoMain` (gated by the flag; inert for interactive `java -jar` runs); the tester signals a graceful stop by writing `SHUTDOWN` + closing the SUT's stdin (`KzenAutoProcess.kill`), and the EOF cascades transitively (Gradle test worker → tester → SUT).

## Prerequisites

- Chrome installed locally (WebDriverManager downloads matching ChromeDriver automatically).
- Java 25 (Gradle's toolchain handles this; tests inherit `java.home`).

The kzen-auto fat jar is wired as an automatic `:kzen-auto-jvm:jar` task dependency from `selfTest` and `runTester`; no manual pre-build needed.

## Workflows

### Interactive (IDE)

1. Open kzen-auto in IntelliJ as its own project (not via the umbrella — composite breaks IDE run/debug; see umbrella AGENTS.md).
2. Run `tech.kzen.auto.test.TesterMain` from the gutter — zero run-config setup. Everything launch-specific lives in code: the module root is located from the class's code source (any working directory works), the port defaults to `TesterMain.TESTER_PORT` (18081) when no `--server.port=` arg is given, and `kzenAutoJar` defaults to the newest locally-built `kzen-auto-jvm/build/libs/kzen-auto-jvm-*.jar` (build it once via `:kzen-auto-jvm:jar`, or add it as a before-run task). NB: run configs live in `.idea/` which is gitignored (machine-local, lost on a project-model reset — happened 2026-06-01), which is why none of this may depend on a "shared" run config.
3. Open `http://127.0.0.1:18081/` in a browser. Navigate to `test-suite/smoke/OpenWelcome.yaml` and Run the Script — Start spawns the SUT, the Browser steps drive it, Stop tears down.
4. Edit the Script in the browser (add steps, change parameters). Saves write through to `kzen-auto-test/src/main/resources/notation/test-suite/.../`, so edits land in the source-controlled tree.

### CLI

```powershell
./gradlew :kzen-auto-test:compileKotlin :kzen-auto-test:compileTestKotlin   # cheap, no spawning
./gradlew :kzen-auto-test:test                                              # excludes *SelfTest.class; intentionally near-empty for now
./gradlew :kzen-auto-test:runTester                                         # same as the IDE run config, for terminal users
./gradlew :kzen-auto-test:selfTest                                          # the actual end-to-end suite (opens Chrome, spawns SUT per test)
```

`selfTest` is deliberately NOT bound to `check` — so `:kzen-auto:build` and umbrella `./gradlew build` do not pull this in.

Override the kzen-auto jar (e.g. point at a CI-cached jar to skip the local `:kzen-auto-jvm:jar` rebuild):
```powershell
./gradlew :kzen-auto-test:selfTest -PkzenAutoJar=C:/path/to/kzen-auto-jvm-0.29.1-SNAPSHOT.jar
```

## Port pinning

`TesterMain.TESTER_PORT = 18081` — the tester binds it by default when no `--server.port=` arg is given (`SelfTestBase` passes it explicitly). The SUT port is whatever the Script YAML's `StartKzenAutoStep` and the subsequent `BrowserGetStep.location` agree on — currently `18082` in the smoke test. Hardcoded because `BrowserGetStep.location` is a static YAML field with no Script-variable interpolation. If those ports are occupied locally, startup fails loudly (`ProcessAwaitUtil`-style HTTP poll times out after 90s). Free ports + variable injection is the deferred open issue.

## Adding a test

1. Add a Script YAML tree under `src/main/resources/notation/main/<Area>/`. Mirror `main/FizzBuzz/`: a root Script of `RunStep`s that leads with an "Open" sub-script (`StartKzenAutoStep` + `BrowserOpenStep` + `BrowserGetStep`), drives the SUT via Browser steps in the middle, and ends with a "Close" sub-script (`BrowserCloseStep` + `StopKzenAutoStep`).
2. Add a `@Test` to a `*SelfTest` class that calls `testerClient.startRun("main/<Area>/<Root>.yaml", "main")` then `testerClient.awaitSuccess("main/<Area>/<Root>.yaml")` (asserts no step errored — see Pass/fail semantics). Read any value the orchestration captured with `testerClient.readDisplayedValue(...)` and assert on it. No Kotlin SUT plumbing — the script owns it.
3. For a non-empty SUT, create `fixtures/<fixture-name>/src/main/resources/notation/main/...` and point `StartKzenAutoStep.fixture` at `fixtures/<fixture-name>`. Give the SUT a port + `name` distinct from sibling tests so two `@Test`s can share one tester JVM (each opens and closes its own SUT). Example: `main/FormulaError/` preloads a throwing-Formula Script via `fixtures/formula-error` and asserts the failure is surfaced.

## Pass/fail semantics

The kzen-auto `/logic/status` endpoint reports `active = "null"` once a run completes, regardless of whether constituent steps threw — so a bare `active == null` check can silently pass on a failed run. Use `TesterClient.awaitSuccess(documentPath)` / `awaitFailure(documentPath)` instead: they await settle, then inspect every step's traced `StepTrace.error` across the whole run (via the `LogicTraceEndpoint` `lookup-run` action with an empty-prefix `LogicTraceQuery`, which merges all sub-script executions). This identifies success/failure from the actual traces, independent of the run's `pauseOnError` mode and at any nesting depth; `runStepErrors(documentPath)` returns the raw list if you need it.

This covers failures in the **tester orchestration's own steps**. A failure inside the **SUT** lives in the SUT process's trace store, not the tester's, so it is observed via the browser (read the SUT's `div[title='Error']`, rendered by `ScriptStepDisplayDefault.renderError`) and re-traced as an orchestration read-step value — see `main/FormulaError/`, the negative self-test.

## Gotchas

- **Tester startup is the slow path.** WebDriverManager downloads ChromeDriver on first run; subsequent runs are cached.
- **`/` returns 302**, so port-readiness polling relies on `HttpURLConnection` following redirects to `/index.html` → 200.
- **Chrome is NOT headless** — `BrowserOpenStep` does not currently set headless mode. CI use needs a kzen-auto-side toggle (deferred open issue).
- **Don't bind `selfTest` to `check`** — that would spawn Chrome on every `:kzen-auto:build` and umbrella `./gradlew build`.
- **`kzenAutoJar` resolution: explicit beats fallback.** The `runTester`/`selfTest` Gradle tasks pass `-DkzenAutoJar=...` (overridable via `-PkzenAutoJar`) pointing at the locally-built fat jar, and their `dependsOn(":kzen-auto-jvm:jar")` keeps it fresh. When the property is absent (bare IDE run), `TesterMain` falls back to the newest `kzen-auto-jvm/build/libs/kzen-auto-jvm-*.jar` — which can be stale, and if no jar was ever built, `StartKzenAutoStep` fails with a clear error at step-run time.
- **Logback logs still follow the cwd.** A gutter-launched tester (cwd = repo root) writes `kzen-auto/logs/run.log`, not `kzen-auto-test/logs/` — check both when reconstructing which process ran where.
- **`KzenAutoSubprocessRegistry` is per-tester-JVM**, but is no longer the only reaper. Killing the tester via IDE Stop fires the shutdown hook → SUTs reaped gracefully. Killing it via `taskkill /F` skips the hook, but the SUT's managed-child lifeline still reaps it (stdin EOF when the tester's handles close, plus the `--parent.pid` watchdog) — so orphan SUTs no longer survive a hard kill or an abandoned pause-on-error run. If a port *does* stay bound, check `jps` (e.g. a stale SUT from before this mechanism, or one launched without the lifeline flag).
