package tech.kzen.auto.server.exec.script

import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * A Script parameter binding: its value is the run input named [name], or [default] when the run supplies
 * none. [ScriptLogic] records it under [stableId] at run start so expressions can reference it like any
 * other in-scope value.
 */
class ScriptParameter(
    val stableId: ObjectStableId,
    val name: TupleComponentName,
    val default: Any?
)
