package tech.kzen.auto.common.paradigm.common.v1.model

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath


object LogicConventions {
    val logicTraceStoreName = ObjectName("LogicTraceStore")

    private val logicTraceJvmPath = DocumentPath.parse(
        "auto-jvm/logic/logic-trace.yaml")


    val logicTraceStoreLocation = ObjectLocation(
        logicTraceJvmPath,
        ObjectPath(logicTraceStoreName, ObjectNesting.root)
    )


    val runIdKey = "run"
    val executionIdKey = "execution"
    val queryKey = "query"
}