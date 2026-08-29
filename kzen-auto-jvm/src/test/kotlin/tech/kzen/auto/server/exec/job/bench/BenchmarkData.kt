package tech.kzen.auto.server.exec.job.bench

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random


object BenchmarkData {
    private const val randomSeed = 42
    private const val stationCount = 400

    fun writeStations(rows: Int, path: Path, header: Boolean) {
        val random = Random(randomSeed)
        write(path) { writer ->
            if (header) {
                writer.appendLine("station,value")
            }
            repeat(rows) { index ->
                val station = "s${(index % stationCount).toString().padStart(3, '0')}"
                val tenths = random.nextInt(-999, 1_000)
                writer.append(station)
                writer.append(',')
                writer.append((tenths / 10.0).toString())
                writer.newLine()
            }
        }
    }

    fun writeWide(rows: Int, path: Path) {
        val random = Random(randomSeed)
        write(path) { writer ->
            writer.appendLine("id,flag,cat,qty,price,c5,c6,c7,c8,c9,c10,c11")
            repeat(rows) { index ->
                val flag = if (index % 2 == 0) "yes" else "no"
                val category = "cat${index % 8}"
                val quantity = index % 20 + 1
                val price = random.nextInt(1, 10_000) / 100.0
                writer.append("$index,$flag,$category,$quantity,$price")
                for (column in 5..11) {
                    writer.append(",v${column}_$index")
                }
                writer.newLine()
            }
        }
    }

    fun writeFlagged(rows: Int, path: Path) {
        write(path) { writer ->
            writer.appendLine("id,flag,value")
            repeat(rows) { index ->
                val flag = if (index % 2 == 0) "yes" else "no"
                writer.appendLine("$index,$flag,v$index")
            }
        }
    }

    private fun write(path: Path, block: (BufferedWriter) -> Unit) {
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use(block)
    }
}
