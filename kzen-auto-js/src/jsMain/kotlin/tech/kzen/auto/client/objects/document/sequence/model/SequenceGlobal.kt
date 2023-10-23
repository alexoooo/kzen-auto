package tech.kzen.auto.client.objects.document.sequence.model

import js.core.WeakRef


// TODO: use React context instead?
object SequenceGlobal {
    private var sequenceStore: WeakRef<SequenceStore>? = null


    fun upsertWeak(sequenceStore: SequenceStore) {
        this.sequenceStore = WeakRef(sequenceStore)
//        if (this.sequenceStore)
//
//        check(this.sequenceStore == null) { "Already set" }
//        this.sequenceStore = sequenceStore
//        println("^^^^ set")
    }


    fun get(): SequenceStore {
        val ref = sequenceStore
            ?: throw IllegalStateException("Never set")

        return ref.deref()
            ?: throw IllegalStateException("Not set anymore")
    }


//    fun clear() {
//        check(sequenceStore != null) { "Already clear" }
//        sequenceStore = null
//        println("^^^^ clear")
//    }
}