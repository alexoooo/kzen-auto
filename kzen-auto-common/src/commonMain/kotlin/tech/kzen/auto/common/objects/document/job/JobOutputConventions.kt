package tech.kzen.auto.common.objects.document.job


/**
 * The run progress keys of the Workers that make outputs (`Format`, which encodes records into bytes, and `Write`,
 * which puts bytes in files), read by their cards out of the Worker's opaque progress map.
 */
object JobOutputConventions {
    //-----------------------------------------------------------------------------------------------------------------
    // Format

    /** Records encoded so far. */
    const val formatRecordsKey = "records"

    /** Bytes encoded so far, header and footer included. */
    const val formatBytesKey = "bytes"

    /** Outputs open, one per group; present only when records are grouped, as is [formatGroupsDoneKey]. */
    const val formatGroupsOpenKey = "groupsOpen"

    /** Outputs ended so far, each with its footer. */
    const val formatGroupsDoneKey = "groupsDone"


    //-----------------------------------------------------------------------------------------------------------------
    // Write

    /** Files published so far. */
    const val writeFilesKey = "files"

    /** Outputs left alone because their file already existed. */
    const val writeSkippedKey = "skipped"

    /** Bytes on disk so far: the published files and the outputs still open. */
    const val writeBytesKey = "bytes"

    /** How many outputs are open (being written). */
    const val writeOpenKey = "open"

    /**
     * The files being written and the files published, each a list of `{path, bytes}` maps where [filePathKey] is
     * the file's absolute path and [fileBytesKey] its bytes on disk so far. Present once there is one. A periodic
     * publish carries at most [JobConventions.progressTeaserRowCount] of each (the first open, the latest
     * published), the final one every published file; [writeFilesKey] and [writeOpenKey] are the counts.
     */
    const val writeOpenFilesKey = "openFiles"
    const val writeWrittenFilesKey = "writtenFiles"
    const val filePathKey = "path"
    const val fileBytesKey = "bytes"

    /**
     * The open outputs live edits discarded: a list of `{at, outputs}` maps, one per edit, where [discardAtKey] is
     * when the edit happened (epoch milliseconds) and [discardOutputsKey] how many outputs it restarted.
     */
    const val writeDiscardsKey = "discards"
    const val discardAtKey = "at"
    const val discardOutputsKey = "outputs"
}
