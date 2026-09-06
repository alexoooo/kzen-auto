package tech.kzen.auto.server.context.runtime

import java.nio.file.Path


/**
 * Process-wide claims on live contexts' work roots. A context creates its root, canonicalizes it with
 * `toRealPath()` (symlinks and case differences defeat string comparison) and claims that path here in one
 * atomic registration before any boot sweep; a root another live context holds, or one nested inside or
 * containing a held root (its cleanup paths would overlap), fails by name. The claim is released only after
 * the context's server has stopped and its run has joined, so a root is never reused while its previous
 * owner could still write.
 */
class WorkRootRegistry {
    private val claims = mutableMapOf<Path, Claim>()


    /** A held claim; [release] returns the root to circulation exactly once. */
    inner class Claim internal constructor(
        val realPath: Path,
        val token: String
    ) {
        @Volatile
        private var released = false

        fun release() {
            synchronized(this@WorkRootRegistry) {
                if (!released) {
                    released = true
                    claims.remove(realPath)
                }
            }
        }

        val isReleased: Boolean
            get() = released
    }


    /** @param realPath the canonical (`toRealPath()`) root, already created */
    fun claim(realPath: Path): Claim {
        require(realPath.isAbsolute) { "Work root must be canonical and absolute: $realPath" }
        synchronized(this) {
            for ((held, claim) in claims) {
                if (held == realPath) {
                    throw IllegalStateException("Work root $realPath is already claimed by a live context (${claim.token})")
                }
                if (realPath.startsWith(held) || held.startsWith(realPath)) {
                    throw IllegalStateException("Work root $realPath overlaps the live context root $held (${claim.token}): " +
                        "their cleanup paths would erase each other's state")
                }
            }
            val claim = Claim(realPath, "claim-" + (claims.size + 1) + "-" + System.identityHashCode(this))
            claims[realPath] = claim
            return claim
        }
    }


    fun isClaimed(realPath: Path): Boolean {
        synchronized(this) {
            return claims.containsKey(realPath)
        }
    }


    fun claimedRoots(): List<Path> {
        synchronized(this) {
            return claims.keys.sorted()
        }
    }
}
