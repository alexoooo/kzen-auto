package tech.kzen.auto.server.service.webdriver

import com.google.common.io.ByteStreams
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.slf4j.LoggerFactory
import tech.kzen.auto.server.service.DownloadClient
import tech.kzen.auto.server.service.webdriver.model.BrowserLauncher
import tech.kzen.auto.server.service.webdriver.model.DownloadFormat
import tech.kzen.auto.server.service.webdriver.model.WebDriverOption
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


// see: https://github.com/Ardesco/selenium-standalone-server-plugin
class WebDriverInstaller(
        private val downloadClient: DownloadClient
) {
    companion object {
        private val logger = LoggerFactory.getLogger(WebDriverInstaller::class.java)!!

        private val extractRoot = Paths.get(
                "../work/kzen-auto"
        ).toAbsolutePath().normalize()


        // https://askubuntu.com/questions/638796/what-is-meaning-of-755-permissions-in-samba-share
        private val executablePermissions = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,

                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.GROUP_READ,

                PosixFilePermission.OTHERS_EXECUTE,
                PosixFilePermission.OTHERS_READ)
    }


    fun install(webDriverOption: WebDriverOption): Path {
        val dir = extractRoot.resolve(webDriverOption.key())

        if (! Files.exists(dir)) {
            downloadAndExtract(webDriverOption.download, dir, webDriverOption.browserLauncher)
        }

        return findBinary(dir, webDriverOption.browserLauncher)
    }


    private fun findBinary(
            dir: Path,
            browserLauncher: BrowserLauncher
    ): Path {
        for (filename in browserLauncher.binaryFilenames) {
            val execPath = dir.resolve(filename)
            if (Files.exists(execPath)) {
                return execPath
            }
        }

        throw IllegalArgumentException("Unable to find binary: $dir - $browserLauncher")
    }


    private fun downloadAndExtract(
            download: URI,
            destinationDir: Path,
            browserLauncher: BrowserLauncher
    ) {
        val bytes = downloadClient.download(download)
        val format = DownloadFormat.parse(download)
        extract(bytes, destinationDir, format, browserLauncher)
    }


    private fun extract(
            byteArray: ByteArray,
            destinationDir: Path,
            format: DownloadFormat,
            launcher: BrowserLauncher
    ) {
        Files.createDirectories(destinationDir)

        when (format) {
            DownloadFormat.Zip -> {
                extractZip(byteArray, destinationDir)
            }

            DownloadFormat.TarGz -> {
                extractTarGz(byteArray, destinationDir)
            }
        }

        val binaryPath = findBinary(destinationDir, launcher)

        try {
            Files.setPosixFilePermissions(binaryPath, executablePermissions)
        }
        catch (e: UnsupportedOperationException) {
            // https://stackoverflow.com/questions/14415960/java-lang-unsupportedoperationexception-posixpermissions-not-supported-as-in
            val asFile = binaryPath.toFile()
            asFile.setExecutable(true)
        }
    }


    private fun extractZip(byteArray: ByteArray, destinationDir: Path) {
        val zipIn = ZipInputStream(ByteArrayInputStream(byteArray))

        while (true) {
            val entry: ZipEntry =
                    zipIn.nextEntry
                    ?: break

            val filePath = destinationDir.resolve(entry.name)

            if (entry.isDirectory) {
                Files.createDirectories(filePath)
            }
            else {
                Files.createDirectories(filePath.parent)
                Files.newOutputStream(filePath).use {
                    ByteStreams.copy(zipIn, it)
                }
            }
            zipIn.closeEntry()
        }
    }


    private fun extractTarGz(byteArray: ByteArray, destinationDir: Path) {
        val gzipIn = GzipCompressorInputStream(ByteArrayInputStream(byteArray))

        // https://stackoverflow.com/a/41853978
        TarArchiveInputStream(gzipIn).use { tarIn ->
            var entry: TarArchiveEntry

            while (true) {
                entry = tarIn.nextEntry as? TarArchiveEntry
                        ?: break

                if (entry.isDirectory) {
                    val dirPath = destinationDir.resolve(entry.name)
                    Files.createDirectories(dirPath)
                }
                else {
                    val filePath = destinationDir.resolve(entry.name).toFile()

                    var count: Int
                    val data = ByteArray(4096)
                    val fos = FileOutputStream(filePath, false)
                    BufferedOutputStream(fos, 4096).use { dest ->
                        while (true) {
                            count = tarIn.read(data, 0, 4096)
                            if (count == -1) {
                                break
                            }

                            dest.write(data, 0, count)
                        }
                    }
                }
            }
        }
    }
}