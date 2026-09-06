package tech.kzen.auto.common.objects.document.plugin.model


/**
 * A `@Reflect` class this workspace resolved (contributed by a generated module at creation, or first named
 * by notation since): [availability] is one of [available], [unavailable] (with the missing `@Service` types
 * in [detail]) or [unresolvable] (the mirror's reason in [detail]). The compatibility kit's inspect mode, which
 * has no workspace, reports [resolved] with the class's service needs in [detail] instead.
 */
data class PluginClassDetail(
    val className: String,
    val availability: String,
    val detail: String?
) {
    companion object {
        const val available = "available"
        const val unavailable = "unavailable"
        const val unresolvable = "unresolvable"
        const val resolved = "resolved"

        private const val classNameKey = "class"
        private const val availabilityKey = "availability"
        private const val detailKey = "detail"

        fun ofCollection(collection: Map<String, Any?>): PluginClassDetail {
            return PluginClassDetail(
                collection[classNameKey] as String,
                collection[availabilityKey] as String,
                collection[detailKey] as String?)
        }
    }

    fun asCollection(): Map<String, Any?> {
        return mapOf(classNameKey to className, availabilityKey to availability, detailKey to detail)
    }
}
