package tech.kzen.auto.server.objects.report.pipeline.output.pivot.store

import com.google.common.util.concurrent.Uninterruptibles
import java.io.RandomAccessFile
import java.io.SyncFailedException
import java.util.concurrent.TimeUnit


object StoreUtils {
    private const val releaseRetryAttempts = 64

    fun flush(handle: RandomAccessFile) {
        for (tryCount in 0 until releaseRetryAttempts) {
            try {
                handle.fd.sync()
                break
            }
            catch (e: SyncFailedException) {
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS)
            }
        }
    }


    fun flushAndClose(handle: RandomAccessFile) {
        flush(handle)
        handle.close()
    }
}