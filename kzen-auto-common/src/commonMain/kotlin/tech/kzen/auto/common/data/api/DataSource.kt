package tech.kzen.auto.common.data.api

import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A notation-discovered data capability that resolves its configured query into a point-in-time manifest.
 * Sources run both at design time through `DataSourceActions` and at run time through a reader; the methods
 * are suspend because either context may expose suspend-only blocking or hosting operations (analysis §4).
 * Implementations are discovered by inheritance capability, never by concrete class name.
 *
 * This is not [tech.kzen.auto.server.data.FlatDataSource], the JVM byte-stream seam used by file readers.
 */
interface DataSource {
    suspend fun resolve(context: DataContext): DataResolveResult


    fun staticShape(role: DataRole?): DataShape? = null


    /** Weak definition references whose content affects the manifest and therefore live-run migration. */
    fun definitionDependencies(): List<ObjectLocation> = emptyList()
}
