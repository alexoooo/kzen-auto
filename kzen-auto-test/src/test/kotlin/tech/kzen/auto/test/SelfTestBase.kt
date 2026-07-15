package tech.kzen.auto.test

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import tech.kzen.auto.test.harness.TesterClient
import tech.kzen.auto.test.server.process.FreePort
import tech.kzen.auto.test.server.process.KzenAutoProcess
import java.nio.file.Paths


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class SelfTestBase {
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

        // Ephemeral by default, deliberately NOT TesterMain.TESTER_PORT: that port belongs to a
        //  developer's own interactive tester (the one AGENTS.md tells you to browse), which is
        //  routinely left running. Taking a free port instead means a self-test run can never contend
        //  with it — nor with a concurrent self-test run. Pin with -PtesterPort=<n> when you want to
        //  open THIS tester in a browser mid-run.
        val testerPort = System.getProperty("testerPort")?.toInt()
            ?: FreePort.next()
        println("[selfTest] tester port: $testerPort")

        tester = KzenAutoProcess.startFromClasspath(
            name = "tester",
            classpath = testerClasspath,
            mainClass = testerMainClass,
            cwd = Paths.get("").toAbsolutePath(),
            port = testerPort,
            jvmArgs = listOf("-DkzenAutoJar=$kzenAutoJar"))
        testerClient = TesterClient(testerPort)
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
