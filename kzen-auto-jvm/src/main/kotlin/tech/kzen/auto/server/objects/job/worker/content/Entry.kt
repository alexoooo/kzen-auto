package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.server.objects.job.worker.LentElement


/**
 * One file inside a container, as a Job element (design §5.3): the metadata a Filter or Formula binds by name,
 * plus the [content] handle. Lifted through the plain-object shape convention, so [content] (an interface
 * without properties) is an opaque native and the record as a whole is owned by the run; [parent] is the
 * descriptor of the container the entry was read from, never a second content handle. [size] is non-null because
 * every container format the spike reads carries it in the header, so a Filter can compare it without a
 * null-safe call; a format that lacks it would report it in the descriptor as absent, not here.
 *
 * A lent element (docs/plans/2026-09-16_borrowed-elements.md §3.1): the run adopts it at the source's pull,
 * and its [close] — the last hold's release — is the source's cue to advance, which invalidates [content].
 */
class Entry(
    val name: String,
    val size: Long,
    val modifiedEpochMillis: Long?,
    val kind: String,
    val content: Content,
    val parent: ContentDescriptor
): LentElement {
    companion object {
        const val kindFile = "file"
        const val kindDirectory = "directory"
        const val kindSymlink = "symlink"
    }


    override fun lentName(): String = "entry '$name'"

    override fun lender(): String = "'${parent.name}'"


    /** The release: the content is invalid from here on; the cursor may advance. */
    override fun close() {
        content.release()
    }


    override fun toString(): String = "Entry($name, size=$size, kind=$kind, parent=${parent.name})"
}
