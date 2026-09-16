package tech.kzen.auto.server.objects.job.worker.content


/**
 * How long a [Content] stays readable (spike: docs/plans/2026-09-16_borrowed-elements.md, design
 * docs/analysis/2026-09-15_content-streaming-and-containers.md §4.2). [CursorBorrowed] is valid only while the
 * container cursor that produced it is positioned on it; [SingleUse] survives the cursor but opens once;
 * [Reopenable] may be opened again, and concurrently.
 */
enum class ContentLifetime {
    CursorBorrowed,
    SingleUse,
    Reopenable
}
