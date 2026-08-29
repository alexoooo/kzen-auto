package tech.kzen.auto.server.objects.datasource

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/** Resolves an authored child Logic into an ordered manifest of plain [DataUnit]s. */
@Reflect
class LogicDataSource(
    private val instructions: ObjectLocation?,
    arguments: List<String>,
    private val schema: DataSchemaDocument? = null
): DataSource {
    private val arguments = arguments.toList()


    init {
        val duplicate = this.arguments
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicate == null) {
            "Duplicate Logic data source argument: $duplicate"
        }
    }


    override suspend fun resolve(context: DataContext): DataResolveResult {
        val target = requireNotNull(instructions) {
            "Logic data source instructions are not configured"
        }
        val schema = BindingSchema.of(arguments.map { name ->
            BindingDefinition(BindingName(name), DataContract(DataType.Dynamic(nullable = true)))
        })
        val components = schema.definitions.map { definition ->
            definition.name to JobDataValues.lift(context.argument(definition.name.value))
        }
        val result = context.host(target, DataBindings.bind(schema, components))
        val mainName = BindingName("main")
        val main = when {
            result.schema.find(mainName) == null -> null
            else -> when (val state = result[mainName]) {
                BindingState.Unbound -> null
                is BindingState.Bound -> JobDataValues.boundary(state.value)
            }
        } ?: return DataResolveResult(DataManifest(emptyList()), emptyList())

        require(main is Iterable<*>) {
            "Logic data source main result must be an eager Iterable<DataUnit>; found ${typeName(main)}"
        }
        val values = main.toList()
        val units = values.mapIndexed { index, value ->
            require(value is DataUnit) {
                "Logic data source main result element[$index] must be DataUnit; found ${typeName(value)}"
            }
            value
        }
        return DataResolveResult(DataManifest(units), emptyList())
    }


    override fun staticShape(role: DataRole?): DataShape? {
        return if (role == null || role == DataRole.main) schema?.shape() else null
    }


    override fun definitionDependencies(): List<ObjectLocation> = listOfNotNull(instructions)


    private fun typeName(value: Any?): String {
        return value?.let {
            it::class.qualifiedName ?: it::class.simpleName ?: it.javaClass.name
        } ?: "null"
    }
}
