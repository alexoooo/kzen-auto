package tech.kzen.auto.common.objects.document.target.model


/**
 * Where the target editor's working screenshot comes from: a fresh desktop capture, or the latest traced run's
 * browser screenshots (bit-identical to what matching saw while that run was going).
 */
enum class TargetScreenshotSource(val key: String) {
    Screen("screen"),
    Browser("browser");


    companion object {
        fun ofKeyOrNull(key: String): TargetScreenshotSource? {
            return entries.find { it.key == key }
        }
    }
}
