package tech.kzen.auto.server.objects.datasource

import tech.kzen.auto.common.data.api.DataSource


/** Graph object that owns a data source whose configuration is authored at the host's object location. */
interface DataSourceHost {
    val hostedDataSource: DataSource
}
