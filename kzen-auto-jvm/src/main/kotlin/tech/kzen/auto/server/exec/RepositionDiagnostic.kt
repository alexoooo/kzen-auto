package tech.kzen.auto.server.exec

import tech.kzen.lib.common.exec.engine.Repositionable
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * The user-facing reason a [Repositionable] refuses one of its two move-to (Set Next Statement) roles, so a
 * refused move can say WHY rather than only that it was refused. Optional alongside [Repositionable]: a flavour
 * that declares no diagnostic is refused with a generic reason naming its document.
 *
 * Each answer must agree with the [Repositionable] member it explains — null exactly when that member accepts —
 * so the two can never disagree about whether the move is possible.
 */
interface RepositionDiagnostic {
    /** Why [Repositionable.canMoveTo] refuses [target], or null when it accepts. */
    fun moveToRefusal(target: ObjectStableId): String?


    /** Why [Repositionable.canDescendThrough] refuses [callSite], or null when it accepts. */
    fun descendRefusal(callSite: ObjectStableId): String?
}
