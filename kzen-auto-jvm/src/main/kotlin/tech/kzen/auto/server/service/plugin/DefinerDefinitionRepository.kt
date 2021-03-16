package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.plugin.definition.ProcessorDefiner
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.lib.platform.ClassName


class DefinerDefinitionRepository(
    definers: List<ProcessorDefiner<*>>
):
    ProcessorDefinitionRepository
{
    //-----------------------------------------------------------------------------------------------------------------
    private val metadata = definers
        .map { ProcessorDefinitionMetadata(
            it.info(),
            ClassName(it.define().processorDataDefinition.outputModelType.name))
        }


    private val byName = definers
        .groupBy { it.info().name }
        .mapValues { it.value.single() }


    //-----------------------------------------------------------------------------------------------------------------
    override fun contains(name: String): Boolean {
        return name in byName
    }


    override fun define(name: String): ProcessorDefinition<*> {
        val definer = byName[name]
            ?: throw IllegalArgumentException("Missing: $name")

        return definer.define()
    }


    override fun listMetadata(): List<ProcessorDefinitionMetadata> {
        return metadata
    }
}