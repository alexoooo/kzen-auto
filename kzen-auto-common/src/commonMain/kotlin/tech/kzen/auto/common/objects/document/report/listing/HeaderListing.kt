package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class HeaderListing(
    val values: List<HeaderLabel>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = HeaderListing(listOf())


        fun ofUnique(headers: List<String>): HeaderListing {
            require(headers.size == headers.toSet().size) {
                "Duplicate: $headers"
            }

            val uniqueValues = headers.map { HeaderLabel(it, 0) }
            return HeaderListing(uniqueValues)
        }


        fun of(headers: List<String>): HeaderListing {
            val occurrenceCounter = mutableMapOf<String, Int>()

            val values = mutableListOf<HeaderLabel>()
            for (header in headers) {
                val nextOccurrence = (occurrenceCounter[header] ?: -1) + 1
                occurrenceCounter[header] = nextOccurrence
                values.add(HeaderLabel(header, nextOccurrence))
            }

            return HeaderListing(values)
        }


        fun validateUniqueOrdered(headerLabels: List<HeaderLabel>) {
            val duplicates = headerLabels.groupBy { it }.filterValues { it.size > 1 }.keys
            require(duplicates.isEmpty()) {
                "Duplicates detected: $duplicates - $headerLabels"
            }

            val byText = headerLabels.groupBy { it.text }
            for (e in byText) {
                require(e.value == e.value.sortedBy { it.occurrence }) {
                    "Out of order: ${e.key} - ${e.value.map { it.occurrence }} - $headerLabels"
                }
            }
        }


        fun ofCollection(collection: List<String>): HeaderListing {
            return HeaderListing(collection.map { HeaderLabel.ofString(it) })
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var digestCache: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    init {
        validateUniqueOrdered(values)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun append(addend: HeaderListing): HeaderListing {
        val maxIndexLeft: Map<String, Int> = values
            .groupBy { it.text }
            .mapValues { e -> e.value.maxOf { it.occurrence } }

        val rightWithOffset: List<HeaderLabel> = addend.values.map {
            val leftIndex: Int = maxIndexLeft[it.text] ?: -1
            val offset = leftIndex + 1
            it.copy(occurrence = it.occurrence + offset)
        }

        return HeaderListing(values + rightWithOffset)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asCollection(): List<String> {
        return values.map { it.asString() }
    }


    fun render(): String {
        return values.map { it.render() }.toString()
    }


    override fun digest(): Digest {
        val existing = digestCache
        if (existing != null) {
            return existing
        }

        val computed = Digest.build {
            addDigestibleList(values)
        }

        digestCache = computed
        return computed
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigest(digest())
    }


    override fun hashCode(): Int {
        return digest().hashCode()
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HeaderListing

        return digest() == other.digest()
    }
}