package tech.kzen.auto.server.objects.process.pivot.row.digest

import tech.kzen.lib.common.util.Digest


interface DigestIndex {
    fun getOrAddIndex(digest: Digest): Long
}