package tech.kzen.auto.test.server.process

import tech.kzen.auto.server.context.KzenAutoConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit


class KzenAutoProcess private constructor(
    val name: String,
    val port: Int,
    private val process: Process,
    private val drain: Thread
):
    AutoCloseable
{
    companion object {
        private const val pollIntervalMs = 250L
        private const val startupTimeoutMs = 90_000L


        fun startFromJar(
            name: String,
            jar: Path,
            cwd: Path,
            port: Int,
            jvmArgs: List<String> = emptyList()
        ): KzenAutoProcess {
            require(jar.toFile().isFile) {
                "kzen-auto jar not found: ${jar.toAbsolutePath()} " +
                    "(build it with: cd ../kzen-auto && ./gradlew :kzen-auto-jvm:jar)"
            }
            val command = buildList {
                add(javaBin())
                addAll(jvmArgs)
                add("-jar")
                add(jar.toAbsolutePath().normalize().toString())
                add("--server.port=$port")
                addAll(managedChildArgs())
            }
            return spawn(name, command, cwd, port)
        }


        fun startFromClasspath(
            name: String,
            classpath: String,
            mainClass: String,
            cwd: Path,
            port: Int,
            jvmArgs: List<String> = emptyList()
        ): KzenAutoProcess {
            require(classpath.isNotBlank()) {
                "classpath must be non-empty for $name"
            }
            val command = buildList {
                add(javaBin())
                addAll(jvmArgs)
                add("-cp")
                add(classpath)
                add(mainClass)
                add("--server.port=$port")
                addAll(managedChildArgs())
            }
            return spawn(name, command, cwd, port)
        }


        private fun spawn(name: String, command: List<String>, cwd: Path, port: Int): KzenAutoProcess {
            require(cwd.toFile().isDirectory) {
                "cwd does not exist: ${cwd.toAbsolutePath()}"
            }

            val process = ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start()

            val drain = Thread({ drainTo(process, name) }, "kzen-auto-drain-$name").apply {
                isDaemon = true
                start()
            }

            try {
                waitUntilAvailable(port, startupTimeoutMs)
            }
            catch (e: Throwable) {
                process.destroyForcibly()
                throw e
            }

            return KzenAutoProcess(name, port, process, drain)
        }


        private fun javaBin(): String =
            Path.of(System.getProperty("java.home"), "bin", "java")
                .toAbsolutePath().toString()


        // Bind the spawned child's lifetime to ours: it self-reaps on stdin EOF (our death closes
        //  the inherited pipe on every OS) and, as a backup, when our pid's process exits.
        //  See KzenAutoMain.startManagedLifeline. Our own stdin is left as the default PIPE so
        //  signalShutdown() can close it.
        private fun managedChildArgs(): List<String> = listOf(
            KzenAutoConfig.managedLifelinePrefix + "stdin",
            KzenAutoConfig.parentPidPrefix + ProcessHandle.current().pid())


        private fun drainTo(process: Process, name: String) {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    println("[$name] $line")
                }
            }
        }


        private fun waitUntilAvailable(port: Int, timeoutMs: Long) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (isAvailable(port)) {
                    return
                }
                Thread.sleep(pollIntervalMs)
            }
            throw IllegalStateException(
                "kzen-auto did not respond on port $port within ${timeoutMs}ms")
        }


        private fun isAvailable(port: Int): Boolean {
            return try {
                val connection = URI("http://127.0.0.1:$port/")
                    .toURL()
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 1_000
                connection.readTimeout = 1_000
                connection.connect()
                connection.responseCode == 200
            }
            catch (e: Exception) {
                false
            }
        }
    }


    override fun close() {
        kill()
    }


    fun kill(forceAfter: Duration = Duration.ofSeconds(15)) {
        // Graceful first: signal via the stdin lifeline so the child self-exits, running its own
        // shutdown hook (context.close()) for orderly resource disposal. OS-agnostic — works even on
        // Windows where process.destroy() == TerminateProcess (a hookless hard kill).
        signalShutdown()

        if (!process.waitFor(forceAfter.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroy()
            if (!process.waitFor(forceAfter.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor()
            }
        }
        drain.join(2_000)
    }


    private fun signalShutdown() {
        // Best-effort: a child that already exited (e.g. an abandoned Paused run) gives a broken
        // pipe — fine, a dead child needs no shutdown. Send the "SHUTDOWN" sentinel AND close the
        // stream so the child sees a sentinel line and/or EOF; whichever it reads first wins.
        val childStdin = process.outputStream

        try {
            childStdin.write("SHUTDOWN\n".toByteArray(Charsets.UTF_8))
            childStdin.flush()
        }
        catch (_: IOException) {}

        try {
            childStdin.close()
        }
        catch (_: IOException) {}
    }
}
