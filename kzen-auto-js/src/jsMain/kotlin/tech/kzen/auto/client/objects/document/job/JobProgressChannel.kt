package tech.kzen.auto.client.objects.document.job

import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Per-document broadcast of every Worker's live run progress, published by [JobController] each time its poll
 * lands. A card receives only its own Worker's progress through props; one that follows ANOTHER Worker's progress
 * — the File table showing how far the reader taking its files has got — reads it off this self-constructing
 * [tech.kzen.auto.client.objects.document.bridge.DocumentBridge] channel instead, as [JobValidationChannel] does
 * for validation. Nothing here is Worker-type-specific (CC-17).
 */
class JobProgressChannel {
    object Key: BridgeKey<JobProgressChannel> {
        override fun create(): JobProgressChannel = JobProgressChannel()
    }


    interface Observer {
        fun onJobProgress(progress: Map<ObjectLocation, JobWorkerProgress>)
    }


    private val observers = mutableListOf<Observer>()
    private var progress: Map<ObjectLocation, JobWorkerProgress> = mapOf()


    fun observe(observer: Observer) {
        observers.add(observer)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    fun current(): Map<ObjectLocation, JobWorkerProgress> = progress


    /** Value-gated: an unchanged progress map notifies nobody. */
    fun publish(next: Map<ObjectLocation, JobWorkerProgress>) {
        if (next == progress) {
            return
        }
        progress = next
        for (observer in observers.toList()) {
            observer.onJobProgress(next)
        }
    }
}
