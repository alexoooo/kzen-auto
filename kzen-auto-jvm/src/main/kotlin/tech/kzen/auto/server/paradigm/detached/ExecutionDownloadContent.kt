package tech.kzen.auto.server.paradigm.detached

import java.io.OutputStream
import java.nio.file.Path


/**
 * The body of a download, named by what the producer actually has, so ownership of any resource behind it
 * is never implicit.
 */
sealed interface ExecutionDownloadContent {
    /**
     * Already on disk. Nothing is opened here, so there is no handle to own or leak - the consumer serves
     * the file and is the only thing that ever opens it.
     */
    data class OfFile(
        val path: Path
    ): ExecutionDownloadContent


    /**
     * Generated on demand, written straight to the response. Acquire whatever the generation needs inside
     * [write] so it is released on the same stack, rather than across the gap to response streaming.
     */
    data class OfWriter(
        val write: suspend (OutputStream) -> Unit
    ): ExecutionDownloadContent
}
