package tech.kzen.auto.common.objects.document.report.listing


data class FilteredHeaderListing(
    val values: HeaderLabelMap<Boolean>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        @Suppress("ConstPropertyName")
        private const val includedDelimiter = "|"

        fun ofAll(headerListing: HeaderListing): FilteredHeaderListing {
            return FilteredHeaderListing(HeaderLabelMap(
                headerListing.values.associateWith { true }))
        }

        fun ofCollection(collection: List<String>): FilteredHeaderListing {
            val values = collection.associate {
                val delimiterIndex = it.indexOf(includedDelimiter)

                val included = it.substring(0, delimiterIndex).toBoolean()

                val headerLabelAsString = it.substring(delimiterIndex + 1)
                val headerLabel = HeaderLabel.ofString(headerLabelAsString)

                headerLabel to included
            }
            return FilteredHeaderListing(HeaderLabelMap(values))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun allHeaderListing(): HeaderListing {
        return values.keyHeaderListing
    }


    fun includedHeaderListing(): HeaderListing {
        val includedHeaderLabels = values
            .map
            .filterValues { it }
            .map { it.key }

        return HeaderListing(includedHeaderLabels)
    }


    fun append(addend: FilteredHeaderListing): FilteredHeaderListing {
        return FilteredHeaderListing(values.append(addend.values))
    }


    fun asCollection(): List<String> {
        return values.map.map { "${it.value}$includedDelimiter${it.key.asString()}" }
    }
}