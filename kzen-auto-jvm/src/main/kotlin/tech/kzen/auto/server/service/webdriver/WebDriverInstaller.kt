package tech.kzen.auto.server.service.webdriver

import com.google.common.io.ByteStreams
import org.slf4j.LoggerFactory
import tech.kzen.auto.server.service.webdriver.model.BrowserLauncher
import tech.kzen.auto.server.service.webdriver.model.DownloadFormat
import tech.kzen.auto.server.service.webdriver.model.WebDriverOption
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


// see: https://github.com/Ardesco/selenium-standalone-server-plugin
class WebDriverInstaller {
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
        val bytes = download(download)
        val format = DownloadFormat.parse(download)
        extract(bytes, destinationDir, format, browserLauncher)
    }


    private fun download(download: URI): ByteArray {
        logger.info("downloading: {}", download)

        val downloadBytes = download
                .toURL()
                .openStream()
                .use { ByteStreams.toByteArray(it) }

        logger.info("download complete: {}", downloadBytes.size)

        return downloadBytes
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
}