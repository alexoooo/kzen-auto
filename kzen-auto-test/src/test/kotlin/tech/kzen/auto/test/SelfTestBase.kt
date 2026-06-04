package tech.kzen.auto.test

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import tech.kzen.auto.test.harness.TesterClient
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

        tester = KzenAutoProcess.startFromClasspath(
            name = "tester",
            classpath = testerClasspath,
            mainClass = testerMainClass,
            cwd = Paths.get("").toAbsolutePath(),
            port = TesterMain.TESTER_PORT,
            jvmArgs = listOf("-DkzenAutoJar=$kzenAutoJar"))
        testerClient = TesterClient(TesterMain.TESTER_PORT)
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
