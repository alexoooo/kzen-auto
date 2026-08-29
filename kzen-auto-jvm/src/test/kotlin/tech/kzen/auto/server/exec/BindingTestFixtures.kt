package tech.kzen.auto.server.exec

import tech.kzen.auto.common.objects.document.logic.BindingSignatureDefiner
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


internal val emptyBindings: DataBindings = DataBindings.bind(BindingSchema.empty)

internal val mainBindingSchema: BindingSchema = BindingSchema.of(BindingDefinition(
    BindingName("main"), DataContract(DataType.Dynamic(nullable = true))))

internal fun bindingSchemaOfMain(type: TypeMetadata): BindingSchema = BindingSchema.of(
    BindingDefinition(BindingName("main"), BindingSignatureDefiner.contract(type)))

internal fun bindingSchemaOf(vararg definitions: Pair<String, TypeMetadata>): BindingSchema =
    BindingSchema.of(definitions.map { (name, type) ->
        BindingDefinition(BindingName(name), BindingSignatureDefiner.contract(type))
    })

internal fun bindingsOfMain(value: Any?): DataBindings = DataBindings.bind(
    mainBindingSchema,
    BindingName("main") to JobDataValues.lift(value, mainBindingSchema[BindingName("main")].contract))

internal fun bindingsOf(schema: BindingSchema, vararg values: Pair<String, Any?>): DataBindings =
    DataBindings.bind(schema, values.map { (name, value) ->
        val bindingName = BindingName(name)
        bindingName to JobDataValues.lift(value, schema[bindingName].contract)
    })

internal fun DataBindings.mainBoundaryValue(): Any? =
    JobDataValues.boundary(requireValue(BindingName("main")))
