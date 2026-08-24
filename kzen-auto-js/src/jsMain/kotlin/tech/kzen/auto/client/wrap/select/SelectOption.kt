package tech.kzen.auto.client.wrap.select


// Value/label carrier for the project's select fields (muiAutocompleteField / muiAutocompleteMultiField).
// `value` is the stable identity used for selection + equality; `label` is the display text.
//
// `detail` is an optional secondary line rendered beneath `label` in the DROPDOWN row only — the closed field
// and the filter text still read `label` alone. `detailTitle` is that line's hover text, for a detail that is
// abbreviated to fit (a simple class name standing in for a qualified one).
external interface SelectOption {
    var value: String
    var label: String
    var detail: String?
    var detailTitle: String?
    var group: String?
}
