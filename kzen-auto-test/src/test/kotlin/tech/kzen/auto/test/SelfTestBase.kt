package tech.kzen.auto.test

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import tech.kzen.auto.test.harness.TesterClient
import tech.kzen.auto.test.server.process.KzenAutoProcess
import java.nio.file.Paths


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class SelfTestBase {
    companion object {
        // Hardcoded port — BrowserGetStep.location is a static YAML field that cannot
        // yet interpolate Sequence parameters, so the tester URL must match what the
        // test-suite YAMLs hard-code. Swap to FreePort once interpolation lands.
        const val TESTER_PORT = 18081
    }


    protected lateinit var tester: KzenAutoProcess
    protected lateinit var testerClient: TesterClient


    @BeforeAll
    fun startTester() {
        val testerClasspath = System.getProperty("testerClasspath")
            ?: error("System property 'testerClasspath' not set; the selfTest Gradle task supplies this.")
        val testerMainClass = System.getProperty("testerMainClass")
            ?: error("System property 'testerMainClass' not set; the selfTest Gradle task supplies this.")
        val kzenAutoJar = System.getProperty("kzenAutoJar")
            ?: error("System property 'kzenAutoJar' not set; the selfTest Gradle task supplies this.")

        tester = KzenAutoProcess.startFromClasspath(
            name = "tester",
            classpath = testerClasspath,
            mainClass = testerMainClass,
            cwd = Paths.get("").toAbsolutePath(),
            port = TESTER_PORT,
            jvmArgs = listOf("-DkzenAutoJar=$kzenAutoJar"))
        testerClient = TesterClient(TESTER_PORT)
    }


    @AfterAll
    fun stopTester() {
        if (::testerClient.isInitialized) {
            testerClient.close()
        }
        if (::tester.isInitialized) {
            tester.close()
        }
    }
}
