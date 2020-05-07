package tech.kzen.auto.common.objects.document.filter

//import tech.kzen.lib.common.api.AttributeCreator
//import tech.kzen.lib.common.model.attribute.AttributeName
//import tech.kzen.lib.common.model.definition.ListAttributeDefinition
//import tech.kzen.lib.common.model.definition.MapAttributeDefinition
//import tech.kzen.lib.common.model.definition.ObjectDefinition
//import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
//import tech.kzen.lib.common.model.instance.GraphInstance
//import tech.kzen.lib.common.model.locate.ObjectLocation
//import tech.kzen.lib.common.model.structure.GraphStructure
//import tech.kzen.lib.common.reflect.Reflect
//
//
//@Reflect
//object CriteriaSpecCreator: AttributeCreator {
//    override fun create(
//        objectLocation: ObjectLocation,
//        attributeName: AttributeName,
//        graphStructure: GraphStructure,
//        objectDefinition: ObjectDefinition,
//        partialGraphInstance: GraphInstance
//    ): Any? {
//        val attributeDefinition = objectDefinition
//            .attributeDefinitions
//            .values[attributeName] as? MapAttributeDefinition
//            ?: throw IllegalArgumentException("Attribute definition missing: $objectLocation - $attributeName")
//
//        val builder = mutableMapOf<String, Set<String>>()
//
//        for (e in attributeDefinition.values) {
//            val columnBuilder = mutableSetOf<String>()
//
//            val valueDefinition = e.value as ListAttributeDefinition
//            for (value in valueDefinition.values) {
//                val stringValue = (value as ValueAttributeDefinition).value as String
//                columnBuilder.add(stringValue)
//            }
//
//            builder[e.key] = columnBuilder
//        }
//
//        return CriteriaSpec(builder)
//    }
//}