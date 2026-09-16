package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.server.data.content.SequentialByteContent


/**
 * A handle to bytes the Job can stream: an entry inside a container, a file, an HTTP body (spike, design §4;
 * see docs/plans/2026-09-16_borrowed-elements.md). Deliberately exposes no properties: the plain-object
 * shape convention would otherwise describe it as a record and lift its members, whereas a content must
 * travel as one opaque native so that the ownership ledger can key holds on it and never inlines it into a
 * snapshot. [reference] is the stable address of the content when the source has one (a file path), and null
 * for a borrowed container entry that can only be reached through its cursor.
 */
interface Content {
    fun descriptor(): ContentDescriptor
    fun lifetime(): ContentLifetime
    fun reference(): DataRef? = null
    fun open(): SequentialByteContent
}
