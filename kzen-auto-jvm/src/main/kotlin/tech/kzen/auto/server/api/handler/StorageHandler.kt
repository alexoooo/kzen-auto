package tech.kzen.auto.server.api.handler

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.util.storage.StorageAreaInfo
import tech.kzen.auto.common.util.storage.StorageBundleInfo
import tech.kzen.auto.server.service.storage.ManagedStorageRegistry


class StorageHandler(
    private val managedStorageRegistry: ManagedStorageRegistry
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun storageSummary(): List<StorageAreaInfo> {
        return managedStorageRegistry.areas().map { area ->
            val bundles = area.bundles()
            StorageAreaInfo(
                area.id,
                area.displayName,
                area.description,
                bundles.sumOf { it.sizeBytes },
                bundles.size,
                area.deletable,
                area.budgetBytes
            )
        }
    }


    fun storageBundleList(parameters: Parameters): List<StorageBundleInfo> {
        val areaId: String = parameters.getParam(CommonRestApi.paramStorageArea) { it }
        val area = managedStorageRegistry.find(areaId)
            ?: error("Unknown storage area: $areaId")

        return area
            .bundles()
            .sortedByDescending { it.sizeBytes }
            .map {
                StorageBundleInfo(it.key, it.displayName, it.sizeBytes, it.lastModifiedMillis, it.active)
            }
    }


    /**
     * @return error message, or empty on success
     */
    fun storageBundleDelete(parameters: Parameters): String {
        val areaId: String = parameters.getParam(CommonRestApi.paramStorageArea) { it }
        val bundleKey: String = parameters.getParam(CommonRestApi.paramStorageBundle) { it }

        val area = managedStorageRegistry.find(areaId)
            ?: return "Unknown storage area: $areaId"

        return area.deleteBundle(bundleKey) ?: ""
    }
}
