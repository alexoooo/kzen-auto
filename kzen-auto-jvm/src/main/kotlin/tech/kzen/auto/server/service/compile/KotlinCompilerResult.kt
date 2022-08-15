package tech.kzen.auto.server.service.compile

import java.nio.file.Path


sealed interface KotlinCompilerResult


data class KotlinCompilerError(
    val error: String
): KotlinCompilerResult


data class KotlinCompilerSuccess(
    val jarFile: Path,
    val classNamePrefix: String
): KotlinCompilerResult
