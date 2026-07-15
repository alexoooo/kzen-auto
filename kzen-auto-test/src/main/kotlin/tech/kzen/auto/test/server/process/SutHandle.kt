package tech.kzen.auto.test.server.process


/**
 * The live handle `StartKzenAutoStep` registers with the engine for a running SUT (under
 * [KzenAutoSubprocessRegistry.resourceKey]), read back by later steps via `StepExecution.resource` —
 * the same ancestor-chain seam `BrowserGetStep` uses to reach the WebDriver its host opened.
 *
 * This is what lets a SUT's port be chosen at run time: a step addresses the SUT by [name] and reads
 * [baseUrl] from here, so no YAML has to repeat the port as a URL literal.
 */
data class SutHandle(
    val name: String,
    val port: Int
) {
    /** Loopback, matching what the child binds (`KzenAutoConfig.host`). No trailing slash. */
    val baseUrl: String
        get() = "http://127.0.0.1:$port"
}
