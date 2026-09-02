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
                writer.append("station,value\n")
            }
            repeat(rows) { index ->
                val station = "s${(index % stationCount).toString().padStart(3, '0')}"
                val tenths = random.nextInt(-999, 1_000)
                writer.append(station)
                writer.append(',')
                writer.append((tenths / 10.0).toString())
                writer.append('\n')
            }
        }
    }

    fun writeWide(rows: Int, path: Path) {
        val random = Random(randomSeed)
        write(path) { writer ->
            writer.append("id,flag,cat,qty,price,c5,c6,c7,c8,c9,c10,c11\n")
            repeat(rows) { index ->
                val flag = if (index % 2 == 0) "yes" else "no"
                val category = "cat${index % 8}"
                val quantity = index % 20 + 1
                val price = random.nextInt(1, 10_000) / 100.0
                writer.append("$index,$flag,$category,$quantity,$price")
                for (column in 5..11) {
                    writer.append(",v${column}_$index")
                }
                writer.append('\n')
            }
        }
    }

    fun writeFlagged(rows: Int, path: Path) {
        write(path) { writer ->
            writer.append("id,flag,value\n")
            repeat(rows) { index ->
                val flag = if (index % 2 == 0) "yes" else "no"
                writer.append("$index,$flag,v$index\n")
            }
        }
    }

    private fun write(path: Path, block: (BufferedWriter) -> Unit) {
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use(block)
    }
}
