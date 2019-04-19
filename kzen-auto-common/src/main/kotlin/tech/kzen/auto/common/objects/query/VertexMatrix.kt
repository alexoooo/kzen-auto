package tech.kzen.auto.common.objects.query


data class VertexMatrix(
        val rows: List<List<VertexInfo>>
) {
    companion object {
        fun ofUnorderedInfos(unorderedInfos: List<VertexInfo>): VertexMatrix {
            val sortedByRowThenColumn = unorderedInfos.sortedWith(VertexInfo.byRowThenColumn)

            val matrix = mutableListOf<List<VertexInfo>>()
            val row = mutableListOf<VertexInfo>()
            var previousRow = -1
            for (vertexInfo in sortedByRowThenColumn) {
                if (previousRow != vertexInfo.row && row.isNotEmpty()) {
                    matrix.add(row.toList())
                    row.clear()
                }
                row.add(vertexInfo)
                previousRow = vertexInfo.row
            }
            if (row.isNotEmpty()) {
                matrix.add(row)
            }
            return VertexMatrix(matrix)
        }
    }


    val usedRows: Int
            get() = rows.size

    val usedColumns: Int = rows
            .map { it.last().column }
            .max()
            ?.let { it + 1 }
            ?: 0


    fun isEmpty(): Boolean {
        return rows.isEmpty()
    }
}