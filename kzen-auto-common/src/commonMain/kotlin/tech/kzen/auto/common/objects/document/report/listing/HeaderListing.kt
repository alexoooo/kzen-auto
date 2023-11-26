package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class HeaderListing(
    val values: List<String>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = HeaderListing(listOf())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var digestCache: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    init {
        val duplicates = values.groupBy { it }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicates detected: $duplicates"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun append(addend: HeaderListing): HeaderListing {
        return HeaderListing(values + addend.values)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(): Digest {
        val existing = digestCache
        if (existing != null) {
            return existing
        }

        val computed = Digest.build {
            addCollection(values) { addUtf8(it) }
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