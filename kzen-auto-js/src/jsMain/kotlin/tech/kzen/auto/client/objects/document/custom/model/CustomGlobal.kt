package tech.kzen.auto.client.objects.document.custom.model

import js.memory.WeakRef


object CustomGlobal {
    private var ref: WeakRef<CustomStore>? = null


    fun upsertWeak(store: CustomStore) {
        ref = WeakRef(store)
    }


    fun get(): CustomStore {
        val current = ref
            ?: error("CustomGlobal never set")
        return current.deref()
            ?: error("CustomGlobal store no longer available")
    }
}
