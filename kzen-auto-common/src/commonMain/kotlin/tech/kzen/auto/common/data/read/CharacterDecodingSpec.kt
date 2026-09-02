package tech.kzen.auto.common.data.read


data class CharacterDecodingSpec(
    val charset: String,
    val bom: String,
    val malformed: String,
    val unmappable: String
)
