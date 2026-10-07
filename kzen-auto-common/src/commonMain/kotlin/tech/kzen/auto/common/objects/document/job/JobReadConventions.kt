package tech.kzen.auto.common.objects.document.job


/**
 * The keys a reading Worker (one that turns files or other content into items, such as Parse) publishes, so the
 * cards around it can show what it found and how far it got without knowing which Worker it is (CC-17): a File
 * card follows whichever reader takes its files through the [readPathKey] / [readBytesKey] / [readSizeKey] progress
 * keys.
 *
 * - Validation details ([tech.kzen.auto.common.objects.document.logic.StepValidation.details]), before Run.
 * - Run progress (the Worker's opaque progress map), while reading.
 */
object JobReadConventions {
    //-----------------------------------------------------------------------------------------------------------------
    // Validation details

    /** The format automatic detection picked per sampled file: a list of `{file, label}` maps. */
    const val detectedFormatsKey = "detectedFormats"
    const val detectedFileKey = "file"
    const val detectedLabelKey = "label"

    /** True when the Worker's input carries units of named parts, so choosing a part applies. */
    const val readsUnitsKey = "readsUnits"

    /** The distinct part names the sampled units hold, in first-seen order. */
    const val rolesKey = "roles"

    /**
     * The selected files, in order: a list of `{location, path, size}` maps, where [fileLocationKey] is the location
     * as the selection row writes it, [filePathKey] the absolute path a reader names in [readPathKey], and
     * [fileSizeKey] the size in bytes.
     */
    const val filesKey = "files"
    const val fileLocationKey = "location"
    const val filePathKey = "path"
    const val fileSizeKey = "size"


    //-----------------------------------------------------------------------------------------------------------------
    // Run progress

    /** How many inputs (files, units) have been read to the end; published throughout the run. */
    const val readDoneKey = "units"

    // The file (or other content) being read at the moment; absent between files
    const val readNameKey = "readName"
    const val readPathKey = "readPath"
    const val readBytesKey = "readBytes"
    const val readSizeKey = "readSize"
    const val readFormatKey = "readFormat"
}
