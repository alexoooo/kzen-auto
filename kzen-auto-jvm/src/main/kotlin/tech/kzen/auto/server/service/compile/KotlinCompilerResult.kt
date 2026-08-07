package tech.kzen.auto.server.service.compile

import java.nio.file.Path


sealed interface KotlinCompilerResult


/**
 * [userCodeOffset] is a character offset into the USER's expression — already relative to
 * [KotlinCode.UserCodeRegion], so no consumer needs the generated source. Null when the diagnostic carries no
 * position, or when its position lands in generated code rather than in the user's own text.
 */
data class KotlinCompilerError(
    val error: String,
    val userCodeOffset: Int? = null
): KotlinCompilerResult


data class KotlinCompilerSuccess(
    val jarFile: Path,
    val classNamePrefix: String
): KotlinCompilerResult
