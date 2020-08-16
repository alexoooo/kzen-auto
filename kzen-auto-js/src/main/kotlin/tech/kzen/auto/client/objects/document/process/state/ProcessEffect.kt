package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess
import tech.kzen.lib.platform.collect.persistentListOf


object ProcessEffect {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun effect(
        state: ProcessState,
        prevState: ProcessState,
        action: ProcessAction
    ): ProcessAction? {
        return when (action) {
            InitiateProcessEffect ->
                ListInputsRequest


            ListInputsRequest ->
                loadFileListing(state)

            is ListInputsResponse ->
                ListColumnsRequest


            ListColumnsRequest ->
                loadColumnListing(state)

            is ListColumnsResponse ->
                loadTask(state)


            is ProcessTaskLookupResponse ->
                if (action.taskModel == null) {
                    SummaryLookupRequest
                }
                else {
                    null
                }


            SummaryLookupRequest ->
                lookupSummary(state)

            is ProcessTaskRunRequest ->
                runSummaryTask(state)


            is FilterAddRequest ->
                submitFilterAdd(state, action.columnName)

            is FilterRemoveRequest ->
                submitFilterRemove(state, action.columnName)

            is FilterValueAddRequest ->
                submitFilterValueAdd(state, action.columnName, action.filterValue)

            is FilterValueRemoveRequest ->
                submitFilterValueRemove(state, action.columnName, action.filterValue)

//            is FilterUpdateRequest ->
//                submitFilterUpdate(state, action.columnName, action.filterValues)


            else -> null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadFileListing(
        state: ProcessState
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            FilterConventions.actionParameter to FilterConventions.actionListFiles)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<String>

                ListInputsResult(resultValue)
            }

            is ExecutionFailure -> {
                ListInputsError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadColumnListing(
        state: ProcessState
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            FilterConventions.actionParameter to FilterConventions.actionListColumns)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<String>

                ListColumnsResponse(resultValue)
            }

            is ExecutionFailure -> {
                ListInputsError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadTask(
        state: ProcessState
    ): ProcessAction {
        val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(state.mainLocation)

        if (activeTasks.isEmpty()) {
            return ProcessTaskLookupResponse(null)
        }

        val taskId = activeTasks.single()
        val model = ClientContext.clientRestTaskRepository.query(taskId)!!

        return ProcessTaskLookupResponse(model)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun lookupSummary(
        state: ProcessState
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            FilterConventions.actionParameter to FilterConventions.actionSummaryLookup)

        return SummaryLookupResult(result)
    }


    private suspend fun runSummaryTask(
        state: ProcessState
    ): ProcessAction {
        val result = ClientContext.clientRestTaskRepository.submit(
            state.mainLocation,
            DetachedRequest(
                RequestParams.of(
                    FilterConventions.actionParameter to FilterConventions.actionSummaryTask),
                null))

        return ProcessTaskRunResponse(result)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun submitFilterAdd(
        state: ProcessState,
        columnName: String
    ): ProcessAction {
        val columnAttributeSegment = AttributeSegment.ofKey(columnName)

        val result = ClientContext.mirroredGraphStore.apply(
            InsertMapEntryInAttributeCommand(
                state.mainLocation,
                FilterConventions.criteriaAttributePath,
                PositionIndex(0),
                columnAttributeSegment,
                ListAttributeNotation.empty,
                true
            ))

        return when (result) {
            is MirroredGraphError ->
                FilterAddError(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterAddResponse
        }
    }


    private suspend fun submitFilterRemove(
        state: ProcessState,
        columnName: String
    ): ProcessAction {
        val columnAttributeSegment = AttributeSegment.ofKey(columnName)
        val columnAttributePath = FilterConventions.criteriaAttributePath.copy(
            nesting = AttributeNesting(persistentListOf(columnAttributeSegment)))

        val result = ClientContext.mirroredGraphStore.apply(
            RemoveInAttributeCommand(
                state.mainLocation,
                columnAttributePath,
                true)
        )

        return when (result) {
            is MirroredGraphError ->
                FilterUpdateResult(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterUpdateResult(null)
        }
    }


//    private suspend fun submitFilterUpdate(
//        state: ProcessState,
//        columnName: String,
//        filterValues: List<String>
//    ): ProcessAction {
//        val columnAttributeSegment = AttributeSegment.ofKey(columnName)
//        val columnAttributePath = FilterConventions.criteriaAttributePath.copy(
//            nesting = AttributeNesting(persistentListOf(columnAttributeSegment)))
//
////            ClientContext.mirroredGraphStore.apply(
////                UpdateInAttributeCommand(
////                    props.processState.mainLocation,
////                    props.attributeName,
////                    attributeNotation)
////            )
//
//        ClientContext.mirroredGraphStore.apply(
//            UpdateInAttributeCommand(
//                state.mainLocation,
//                columnAttributePath,
//                ListAttributeNotation(
//                    filterValues
//                        .map { ScalarAttributeNotation(it) }
//                        .toPersistentList())))
//
//        return FilterUpdateResult(null)
//    }


    private suspend fun submitFilterValueAdd(
        state: ProcessState,
        columnName: String,
        filterValue: String
    ): ProcessAction {
        val columnAttributeSegment = AttributeSegment.ofKey(columnName)
        val columnAttributePath = FilterConventions.criteriaAttributePath.copy(
            nesting = AttributeNesting(persistentListOf(columnAttributeSegment)))

        val containingList = state
            .clientState
            .graphStructure()
            .graphNotation
            .transitiveAttribute(state.mainLocation, columnAttributePath)
            as ListAttributeNotation

        val result = ClientContext.mirroredGraphStore.apply(
            InsertListItemInAttributeCommand(
                state.mainLocation,
                columnAttributePath,
                PositionIndex(containingList.values.size),
                ScalarAttributeNotation(filterValue)))

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }


    private suspend fun submitFilterValueRemove(
        state: ProcessState,
        columnName: String,
        filterValue: String
    ): ProcessAction {
        val columnAttributeSegment = AttributeSegment.ofKey(columnName)
        val columnAttributePath = FilterConventions.criteriaAttributePath.copy(
            nesting = AttributeNesting(persistentListOf(columnAttributeSegment)))

        val containingList = state
            .clientState
            .graphStructure()
            .graphNotation
            .transitiveAttribute(state.mainLocation, columnAttributePath)
                as ListAttributeNotation

        val filterValueIndex = containingList.values.indexOfFirst { it.asString() == filterValue }

        val itemPath = columnAttributePath.copy(nesting =
            columnAttributePath.nesting.push(AttributeSegment.ofIndex(filterValueIndex)))

        val result = ClientContext.mirroredGraphStore.apply(
            RemoveInAttributeCommand(
                state.mainLocation,
                itemPath,
                false))

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }
}