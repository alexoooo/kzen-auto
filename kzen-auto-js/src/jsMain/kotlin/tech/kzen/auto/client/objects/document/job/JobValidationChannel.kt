package tech.kzen.auto.client.objects.document.job

import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.auto.common.objects.document.job.model.JobValidation


/**
 * Per-document broadcast of the Job's latest server-side validation (the static payload-type walk's per-Worker
 * output contracts and errors), published by [JobController] each time its fetch lands. An attribute editor
 * that needs an UPSTREAM Worker's output contract — the path picker walking what its input carries — renders
 * deep under the generic AttributeEditorManager (objectLocation + attributeName only), so it reads the
 * validation off this self-constructing [tech.kzen.auto.client.objects.document.bridge.DocumentBridge] channel
 * (the [JobSummaryStore] precedent) instead of through props. Nothing here is Worker-type-specific (CC-17).
 */
class JobValidationChannel {
    object Key: BridgeKey<JobValidationChannel> {
        override fun create(): JobValidationChannel = JobValidationChannel()
    }


    interface Observer {
        fun onJobValidation(validation: JobValidation?)
    }


    private val observers = mutableListOf<Observer>()
    private var validation: JobValidation? = null


    fun observe(observer: Observer) {
        observers.add(observer)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    fun current(): JobValidation? = validation


    /** Value-gated: an unchanged validation (a data class) notifies nobody. */
    fun publish(next: JobValidation?) {
        if (next == validation) {
            return
        }
        validation = next
        for (observer in observers.toList()) {
            observer.onJobValidation(next)
        }
    }
}
