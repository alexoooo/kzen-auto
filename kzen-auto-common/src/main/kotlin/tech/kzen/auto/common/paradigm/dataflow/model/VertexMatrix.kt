package tech.kzen.auto.common.paradigm.dataflow.model


data class VertexMatrix(
        val rows: List<List<VertexInfo>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofUnorderedInfos(unorderedInfos: List<VertexInfo>): VertexMatrix {
            val sortedByRowThenColumn = unorderedInfos.sortedWith(VertexInfo.byRowThenColumn)

            val matrix = mutableListOf<List<VertexInfo>>()
            val row = mutableListOf<VertexInfo>()
            var previousRow = -1
            for (vertexInfo in sortedByRowThenColumn) {
                if (/*previousRow != -1 &&*/
                        previousRow != vertexInfo.row &&
                        row.isNotEmpty()) {
//                    for (skipped in previousRow until vertexInfo.row) {
//                        matrix.add(listOf())
//                    }
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


    //-----------------------------------------------------------------------------------------------------------------
    val usedRows: Int
            get() = rows.lastOrNull()?.first()?.row?.let { it + 1 } ?: 0


    val usedColumns: Int = rows
            .map { it.last().column }
            .max()
            ?.let { it + 1 }
            ?: 0


    fun isEmpty(): Boolean {
        return rows.isEmpty()
    }


    fun get(rowIndex: Int, columnIndex: Int): VertexInfo? {
        for (row in rows) {
            if (row.first().row != rowIndex) {
                continue
            }

            for (cell in row) {
                if (cell.column != columnIndex) {
                    continue
                }
                return cell
            }

            return null
        }

        return null
    }
}