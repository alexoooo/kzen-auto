package tech.kzen.auto.client.objects.document.target

import tech.kzen.lib.common.exec.RequestParams


/**
 * The Target document's routed sub-pages: the `section` hash param selects View or Add,
 * defaulting to View when there are patches to show and Add when the document is empty.
 */
object TargetSection {
    const val parameterKey = "section"
    const val view = "view"
    const val add = "add"


    fun active(parameters: RequestParams?, hasCrops: Boolean): String {
        val requested = parameters?.get(parameterKey)

        if (requested == view || requested == add) {
            return requested
        }

        return if (hasCrops) { view } else { add }
    }
}
