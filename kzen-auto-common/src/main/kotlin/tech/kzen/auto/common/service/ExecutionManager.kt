package tech.kzen.auto.common.service

import tech.kzen.auto.common.exec.ExecutionFrame
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.edit.ObjectRenamedEvent
import tech.kzen.lib.common.edit.ProjectEvent
import tech.kzen.lib.common.notation.model.ProjectPath


class ExecutionManager
    : ModelManager.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun handleExecution(executionModel: ExecutionModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Subscriber>()
    private val model: ExecutionModel = ExecutionModel(mutableListOf())
//    private var projectModel: ProjectModel? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)
        subscriber.handleExecution(model)
    }


    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }


    private fun publish() {
        for (subscriber in subscribers) {
            subscriber.handleExecution(model)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleModel(autoModel: ProjectModel, event: ProjectEvent?) {
        if (event == null) {
            return
        }

        val changed = when (event) {
            is ObjectRenamedEvent ->
                model.rename(event.objectName, event.newName)

            else ->
                false
        }

        if (changed) {
            publish()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun reset() {
        model.frames.clear()

        publish()
    }


    fun start(main: ProjectPath, projectModel: ProjectModel) {
        val packageNotation = projectModel.projectNotation.packages[main]!!

        val values = mutableMapOf<String, ExecutionStatus>()

        for (e in packageNotation.objects) {
            values[e.key] = ExecutionStatus.Pending
        }

        val frame = ExecutionFrame(main, values)

        model.frames.clear()
        model.frames.add(frame)

        publish()
    }


    //-----------------------------------------------------------------------------------------------------------------


    fun onExecution(objectName: String, success: Boolean) {


        val existingFrame = model.findLast(objectName)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        val status =
                if (success) {
                    ExecutionStatus.Success
                }
                else {
                    ExecutionStatus.Failed
                }

        upsertFrame.values[objectName] = status

        publish()

//        if (upsertFrame.current == objectName) {
//            if (success) {
//                val packageNotation = projectModel!!.projectNotation.packages[upsertFrame.path]!!
//
//                val index = packageNotation.indexOf(objectName)
//                val nextIndex = index + 1
//
//                if (nextIndex >= packageNotation.objects.size) {
//                    upsertFrame.status = ExecutionStatus.Done
//                }
//                else {
//                    val nextName = packageNotation.nameAt(nextIndex)
//                    upsertFrame.current = nextName
//                    upsertFrame.status = ExecutionStatus.Pending
//                }
//            }
//            else {
//                upsertFrame.status = ExecutionStatus.Failed
//            }
//        }
    }
}
