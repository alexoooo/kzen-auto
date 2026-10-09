package tech.kzen.auto.server.context

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap


/**
 * Names every [KzenAutoContext.forTest] context still open when the JVM exits, by the code that created it. An
 * unclosed test context leaves its temporary work root behind. With [reportDirProperty] set (kzen-auto-jvm's test
 * tasks set it), the names are also written to a file in that directory, which fails the test task.
 */
internal object TestContextLeakGuard {
    //-----------------------------------------------------------------------------------------------------------------
    const val reportDirProperty = "kzen.test.unclosedContextReportDir"

    // Enough frames to reach the test method through a helper or two
    private const val originFrameLimit = 6

    // Frames of the test framework, reflection and coroutines say nothing about which test opened a context
    private val frameworkPackages = listOf(
        "java.", "jdk.", "sun.", "kotlin.", "kotlinx.", "org.junit.", "org.gradle.", "worker.org.gradle.")

    private val logger = LoggerFactory.getLogger(TestContextLeakGuard::class.java)


    //-----------------------------------------------------------------------------------------------------------------
    // Keyed by work root rather than by context, so a leaked context is not kept reachable for the rest of the JVM
    private val openByWorkRoot = ConcurrentHashMap<Path, String>()


    init {
        Runtime.getRuntime().addShutdownHook(Thread(::reportOpen, "kzen-test-context-leak-guard"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun opened(workRoot: Path) {
        val origin = Throwable().stackTrace
            .filterNot { frame ->
                frame.className.startsWith(KzenAutoContext::class.java.name) ||
                    frame.className == TestContextLeakGuard::class.java.name ||
                    frameworkPackages.any { frame.className.startsWith(it) }
            }
            .take(originFrameLimit)
            .joinToString(" <- ")
        openByWorkRoot[workRoot] = origin
    }


    fun closed(workRoot: Path) {
        openByWorkRoot.remove(workRoot)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reportOpen() {
        if (openByWorkRoot.isEmpty()) {
            return
        }

        val report = openByWorkRoot.entries.joinToString("\n") { (workRoot, origin) ->
            "$workRoot opened by $origin"
        }
        logger.error("KzenAutoContext.forTest() contexts never closed:\n{}", report)

        val reportDir = System.getProperty(reportDirProperty)
            ?: return
        Files.createDirectories(Path.of(reportDir))
        Files.writeString(Path.of(reportDir, "${ProcessHandle.current().pid()}.txt"), report + "\n")
    }
}
