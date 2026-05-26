package tech.kzen.auto.client.wrap.select

import kotlinx.browser.document
import react.ChildrenBuilder
import tech.kzen.auto.client.wrap.react
import kotlin.js.Json
import kotlin.js.json


// NB: ReactSelect wrapper that applies the shared styling and portal-target conventions
// (transparent control background, document.body menu portal) used by Script document editors.
// Callers pass options + selection + onSelect; the wrapper owns the rest of the prop surface.
fun ChildrenBuilder.reactSelectField(
    selectedOption: ReactSelectOption?,
    options: Array<ReactSelectOption>,
    onSelect: (ReactSelectOption) -> Unit
) {
    ReactSelect::class.react {
        value = selectedOption
        this.options = options

        onChange = {
            onSelect(it)
        }

        // https://stackoverflow.com/a/51844542/1941359
        val styleTransformer: (Json, Json) -> Json = { base, _ ->
            val transformed = json()
            transformed.add(base)
            transformed["background"] = "transparent"
            transformed
        }

        val reactStyles = json()
        reactStyles["control"] = styleTransformer
        styles = reactStyles

        // NB: prevents clipping when ReactSelect lives inside an overflow-hidden container —
        //     e.g. ConditionalStepDisplay's table. See https://react-select.com/advanced#portaling
        menuPortalTarget = document.body!!
    }
}
