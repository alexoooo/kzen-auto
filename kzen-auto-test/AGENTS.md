# kzen-auto-test — AI agent guide

Blackbox end-to-end self-test for kzen-auto. Dogfoods kzen-auto's own Sequence (Script) browser-automation feature to drive a separate kzen-auto instance. Lives as a `kzen-auto` subproject (sibling of `kzen-auto-jvm`, `kzen-auto-js`, etc.), so all invocations below assume the working directory is `kzen-auto/`.

## Architecture

Two JVM processes participate; the tester's Script orchestrates the second.

1. **Tester** — `tech.kzen.auto.test.TesterMain` (this module's entry point). Same Ktor surface as vanilla kzen-auto plus the SUT-lifecycle Step catalogue (Start/Stop). Runs with cwd = `kzen-auto/kzen-auto-test/`, hosting the test-suite Sequence YAMLs at `src/main/resources/notation/test-suite/`. Also hosts the Chrome WebDriver. Launched either from the **Tester (kzen-auto-test)** IntelliJ run config (interactive development) or by `SelfTestBase.startTester` inside the `selfTest` Gradle task (CI).
2. **SUT (System Under Test)** — vanilla `kzen-auto-jvm-*.jar`, spawned by `StartKzenAutoStep` inside the tester's Sequence YAML. Cwd is a per-run temp dir copied from `fixtures/<name>/`. Killed by a matching `StopKzenAutoStep` (or the tester's JVM shutdown hook if the script aborts).

The tester sees the test suite via kzen-lib's cwd-relative `GradleLocator` (`kzen-lib-jvm/.../GradleLocator.kt`), which scans `./src/main/resources/notation/`. The SUT sees only its (initially empty) fixture project, alongside kzen-auto's classpath-bundled system notation.

`TesterMain` pre-registers `KzenAutoTestModule` (KSP-generated from `@Reflect`-annotated step classes) into `ReflectionRegistry.global` before delegating to `tech.kzen.auto.server.main`, so the tester's notation loader can resolve `is: StartKzenAutoStep` / `is: StopKzenAutoStep` references in the test-suite YAMLs.

## Step catalogue

Tester-only steps registered by this subproject:

- **`StartKzenAutoStep`** (`name: String`, `fixture: String`, `port: Int`) — Copies `<fixture>` (resolved relative to the tester's cwd) to a temp dir, spawns `java -jar <kzenAutoJar> --server.port=<port>` against it, blocks until HTTP 200 on `/`. Registers the handle in `KzenAutoSubprocessRegistry` under `name`. Reads the SUT jar path from system property `kzenAutoJar` (set by the IDE run config or `selfTest` Gradle task).
- **`StopKzenAutoStep`** (`name: String`) — Looks up the named subprocess, `process.destroy()`s it (force-kills after 15s), deletes the temp dir. No-op if not found (traces a warning).

The tester JVM installs a shutdown hook that calls `KzenAutoSubprocessRegistry.closeAll()` — orphan SUTs are reaped if the script aborts or the tester is killed via IDE Stop.

## Prerequisites

- Chrome installed locally (WebDriverManager downloads matching ChromeDriver automatically).
- Java 25 (Gradle's toolchain handles this; tests inherit `java.home`).

The kzen-auto fat jar is wired as an automatic `:kzen-auto-jvm:jar` task dependency from `selfTest` and `runTester`; no manual pre-build needed.

## Workflows

### Interactive (IDE)

1. Open kzen-auto in IntelliJ as its own project (not via the umbrella — composite breaks IDE run/debug; see umbrella AGENTS.md).
2. Run the **Tester (kzen-auto-test)** run config (shared in `.idea/runConfigurations/`). It runs `tech.kzen.auto.test.TesterMain` with the `kzen-auto-test` module classpath, cwd = `kzen-auto-test/`, and `-DkzenAutoJar=$PROJECT_DIR$/kzen-auto-jvm/build/libs/kzen-auto-jvm-0.29.1-SNAPSHOT.jar`. The before-run task builds `:kzen-auto-jvm:jar`.
3. Open `http://127.0.0.1:18081/` in a browser. Navigate to `test-suite/smoke/OpenWelcome.yaml` and Run the Sequence — Start spawns the SUT, the Browser steps drive it, Stop tears down.
4. Edit the Sequence in the browser (add steps, change parameters). Saves write through to `kzen-auto-test/src/main/resources/notation/test-suite/.../`, so edits land in the source-controlled tree.

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

`SelfTestBase.TESTER_PORT = 18081`. The SUT port is whatever the Sequence YAML's `StartKzenAutoStep` and the subsequent `BrowserGetStep.location` agree on — currently `18082` in the smoke test. Hardcoded because `BrowserGetStep.location` is a static YAML field with no Sequence-variable interpolation. If those ports are occupied locally, startup fails loudly (`ProcessAwaitUtil`-style HTTP poll times out after 90s). Free ports + variable injection is the deferred open issue.

## Adding a test

1. Add a Sequence YAML under `src/main/resources/notation/test-suite/<area>/<Name>.yaml`. Mirror `test-suite/smoke/OpenWelcome.yaml`: lead with `StartKzenAutoStep`, end with `StopKzenAutoStep`, put browser interactions in between.
2. Add a `@Test` to a `*SelfTest` class that calls `testerClient.startRun("test-suite/<area>/<Name>.yaml", "main")` and `awaitCompletion()`. No Kotlin SUT plumbing — the script owns it.
3. For a non-empty SUT, create `fixtures/<fixture-name>/src/main/resources/notation/...` and point `StartKzenAutoStep.fixture` at the new path.

## Pass/fail semantics — known limitation

The kzen-auto `/logic/status` endpoint reports `active = "null"` once a run completes, regardless of whether constituent steps threw. A step throwing inside the tester still flips `active` to null shortly after, so silent test passes are possible. Until a typed `Assert*Step` exists (deferred open issue), inspect tester stdout for exceptions and consider a custom step that asserts on browser state.

## Gotchas

- **Tester startup is the slow path.** WebDriverManager downloads ChromeDriver on first run; subsequent runs are cached.
- **`/` returns 302**, so port-readiness polling relies on `HttpURLConnection` following redirects to `/index.html` → 200.
- **Chrome is NOT headless** — `BrowserOpenStep` does not currently set headless mode. CI use needs a kzen-auto-side toggle (deferred open issue).
- **Don't bind `selfTest` to `check`** — that would spawn Chrome on every `:kzen-auto:build` and umbrella `./gradlew build`.
- **The IDE Run config and the `runTester` Gradle task pass `-DkzenAutoJar=...` pointing at the locally-built fat jar.** If you delete `kzen-auto-jvm/build/libs/kzen-auto-jvm-*.jar`, `StartKzenAutoStep` will fail with a clear error. The before-run / `dependsOn(":kzen-auto-jvm:jar")` wiring rebuilds it for you.
- **`KzenAutoSubprocessRegistry` is per-tester-JVM.** Killing the tester via IDE Stop fires the shutdown hook → SUTs reaped. Killing the tester via `taskkill /F` skips the hook → orphan SUTs survive. Check `jps` if a port is suddenly bound.
