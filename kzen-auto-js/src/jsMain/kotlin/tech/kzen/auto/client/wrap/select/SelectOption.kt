package tech.kzen.auto.client.wrap.select


// Value/label carrier for the project's select fields (muiAutocompleteField / muiAutocompleteMultiField).
// `value` is the stable identity used for selection + equality; `label` is the display text.
external interface SelectOption {
    var value: String
    var label: String
}
