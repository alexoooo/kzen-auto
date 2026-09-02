package tech.kzen.auto.common.data.read


internal fun requirePositive(value: Number?, name: String) {
    require(value == null || value.toLong() > 0) { "$name must be positive when configured" }
}
