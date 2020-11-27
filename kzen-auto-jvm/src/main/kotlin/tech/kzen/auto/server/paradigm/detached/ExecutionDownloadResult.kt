package tech.kzen.auto.server.paradigm.detached

import java.io.InputStream


data class ExecutionDownloadResult(
    val data: InputStream,
    val fileName: String,
    val mimeType: String
)