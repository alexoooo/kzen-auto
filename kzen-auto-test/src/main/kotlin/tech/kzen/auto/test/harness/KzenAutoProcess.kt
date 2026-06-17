package tech.kzen.auto.test.harness

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


        fun start(
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
            require(cwd.toFile().isDirectory) {
                "cwd does not exist: ${cwd.toAbsolutePath()}"
            }

            val javaBin = Path.of(System.getProperty("java.home"), "bin", "java")
                .toAbsolutePath().toString()

            val command = buildList {
                add(javaBin)
                addAll(jvmArgs)
                add("-jar")
                add(jar.toAbsolutePath().normalize().toString())
                add("--server.port=$port")
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
        process.destroy()
        if (!process.waitFor(forceAfter.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
        drain.join(2_000)
    }
}
