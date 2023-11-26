package tech.kzen.auto.server.paradigm.detached

import java.io.InputStream


// TODO: what is the kotlin multiplatform equivalent of InputStream?
data class ExecutionDownloadResult(
    val data: InputStream,
    val fileName: String,
    val mimeType: String
)