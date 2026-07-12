package tech.kzen.auto.server.service.storage


/**
 * Central catalogue of every on-disk storage area the server owns, backing the
 * storage-management screen (and through it, manual deletion and size inspection).
 *
 * Convention: any service that resolves a new root under [tech.kzen.auto.server.util.WorkUtils]
 * (or otherwise writes a server-owned directory) MUST register a corresponding area during
 * [tech.kzen.auto.server.context.KzenAutoContext] init — display-only if its lifecycle is
 * self-managed — so disk usage stays centrally visible.
 */
class ManagedStorageRegistry {
    //-----------------------------------------------------------------------------------------------------------------
    private val areasById = LinkedHashMap<String, ManagedStorageArea>()


    //-----------------------------------------------------------------------------------------------------------------
    fun register(area: ManagedStorageArea) {
        check(area.id !in areasById) { "Already registered: ${area.id}" }
        areasById[area.id] = area
    }


    fun areas(): List<ManagedStorageArea> {
        return areasById.values.toList()
    }


    fun find(id: String): ManagedStorageArea? {
        return areasById[id]
    }
}
