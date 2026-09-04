package tech.kzen.auto.plugin.api.data


data class StrictCharacterView(
    val encoding: String,
    val text: String
) {
    init {
        require(encoding.isNotBlank()) { "Character-view encoding must not be blank" }
    }
}
