package tech.kzen.auto.server.objects.report.pipeline.output.pivot.store

import com.google.common.util.concurrent.Uninterruptibles
import org.slf4j.LoggerFactory
import java.io.RandomAccessFile
import java.io.SyncFailedException
import java.util.concurrent.TimeUnit


object StoreUtils {
    private val logger = LoggerFactory.getLogger(StoreUtils::class.java)

    private const val releaseRetryAttempts = 64

    fun flush(handle: RandomAccessFile, location: String) {
        for (tryCount in 0 until releaseRetryAttempts) {
            try {
                handle.fd.sync()
                break
            }
            catch (e: SyncFailedException) {
                logger.info("Failed to sync (retry ${tryCount + 1}): $location - ${e.message}")
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS)
            }
        }
    }


    fun flushAndClose(handle: RandomAccessFile, location: String) {
        flush(handle, location)
        handle.close()
    }
}