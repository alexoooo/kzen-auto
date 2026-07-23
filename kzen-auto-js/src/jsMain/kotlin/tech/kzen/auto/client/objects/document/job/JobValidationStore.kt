package tech.kzen.auto.client.objects.document.job

import tech.kzen.auto.client.objects.document.common.valid.ServerValidationFetch
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.util.digest.Digest


/**
 * Fetches a Job document's server-side validation — the static payload-type walk's per-Worker inferred
 * payload types and expression compile errors — via the JobValidator detached action (the
 * ScriptValidationStore precedent, sized to JobController's store-less shape: the controller triggers a
 * fetch on notation change and holds the result in its own state). Null on failure, and equally on a result
 * the [ServerValidationFetch] digest gate rejects as computed against other-than-current notation — the cards
 * then simply show no validation, and the next notation change retries.
 */
class JobValidationStore(
    private val restClient: ClientRestApi
) {
    suspend fun fetch(documentPath: DocumentPath, currentDigest: () -> Digest?): JobValidation? {
        val outcome = ServerValidationFetch.fetchCurrent(
            currentDigest = currentDigest,
            perform = {
                restClient.performDetached(
                    JobConventions.jobValidatorLocation,
                    CommonRestApi.paramHostDocumentPath to documentPath.asString())
            },
            parse = { JobValidation.ofExecutionValue(it) })

        return (outcome as? ServerValidationFetch.Outcome.Current)?.value
    }
}
