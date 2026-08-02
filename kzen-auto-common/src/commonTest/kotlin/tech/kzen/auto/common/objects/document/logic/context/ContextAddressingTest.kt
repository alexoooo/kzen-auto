package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


/**
 * The derived runtime address is a **wire contract**, not a display string: the same declaration must produce
 * the same key on the JVM and in the browser, because a typed reader and a raw plugin call address one
 * registry. This test therefore lives in commonTest and runs on both platforms — a JVM-only pin would let the
 * two drift with a green build.
 */
class ContextAddressingTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("main/Contexts.yaml")


    private fun descriptor(
        name: String,
        type: TypeMetadata,
        qualifier: String = "",
        key: String = ""
    ): ContextDescriptor {
        return ContextDescriptor(
            ObjectLocation(documentPath, ObjectPath.parse(name)),
            type = type,
            qualifier = qualifier,
            key = key,
            title = "",
            icon = "",
            description = "")
    }


    private fun typeOf(className: String, vararg generics: TypeMetadata, nullable: Boolean = false): TypeMetadata {
        return TypeMetadata(ClassName(className), generics.toList(), nullable)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aPlainTypeRendersFullyQualified() {
        assertEquals("kotlin.String", ContextAddressing.canonicalFamily(TypeMetadata.string))
    }


    @Test
    fun genericArgumentsParticipateSoTwoListsDoNotShareAnAddress() {
        // The whole reason the family is the CANONICAL FULL type rather than the class name.
        val listOfString = typeOf("kotlin.collections.List", TypeMetadata.string)
        val listOfInt = typeOf("kotlin.collections.List", TypeMetadata.int)

        assertEquals("kotlin.collections.List<kotlin.String>", ContextAddressing.canonicalFamily(listOfString))
        assertEquals("kotlin.collections.List<kotlin.Int>", ContextAddressing.canonicalFamily(listOfInt))
    }


    @Test
    fun nullabilityAndNestingParticipateToo() {
        val nested = typeOf(
            "kotlin.collections.Map",
            TypeMetadata.string,
            typeOf("kotlin.collections.List", typeOf("kotlin.Int", nullable = true)),
            nullable = true)

        assertEquals(
            "kotlin.collections.Map<kotlin.String,kotlin.collections.List<kotlin.Int?>>?",
            ContextAddressing.canonicalFamily(nested))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun anUnqualifiedDeclarationAddressesItsFamily() {
        val key = ContextAddressing.keyOf(descriptor("Greeting", TypeMetadata.string))

        assertEquals("kotlin.String", key.asString())
        assertEquals(null, key.qualifier)
    }


    @Test
    fun anExplicitKeyReplacesTheDerivedFamily() {
        // The interop alias: a raw plugin call names `browser`, and the typed declaration has to agree.
        val key = ContextAddressing.keyOf(
            descriptor("Browser", typeOf("org.openqa.selenium.remote.RemoteWebDriver"), key = "browser"))

        assertEquals("browser", key.asString())
    }


    @Test
    fun aDeclaredQualifierAddressesOneMemberExactly() {
        val primary = ContextAddressing.keyOf(
            descriptor("Primary Db", typeOf("java.sql.Connection"), qualifier = "primary", key = "db"))
        val reporting = ContextAddressing.keyOf(
            descriptor("Reporting Db", typeOf("java.sql.Connection"), qualifier = "reporting", key = "db"))

        assertEquals("db:primary", primary.asString())
        assertEquals("db:reporting", reporting.asString())
        assertEquals(primary.family, reporting.family, "sharing a family is what makes them siblings")
    }


    @Test
    fun aComputedQualifierAppliesOnlyToAnUnqualifiedDeclaration() {
        val sut = descriptor("Sut", typeOf("tech.kzen.SutHandle"), key = "sut")
        assertEquals("sut:main", ContextAddressing.keyOf(sut, "main").asString())
    }


    @Test
    fun aDeclaredPlusComputedQualifierIsRefusedRatherThanCombined() {
        // Neither override nor concatenation: a declaration that already names its member leaves a run-time
        // value nothing to say, and silently picking one would make the static claim and the runtime address
        // disagree.
        val primary = descriptor("Primary Db", typeOf("java.sql.Connection"), qualifier = "primary", key = "db")

        assertFailsWith<IllegalArgumentException> {
            ContextAddressing.keyOf(primary, "reporting")
        }
    }


    @Test
    fun anEmptyComputedQualifierIsAbsentRatherThanEmpty() {
        val primary = descriptor("Primary Db", typeOf("java.sql.Connection"), qualifier = "primary", key = "db")

        assertEquals("db:primary", ContextAddressing.keyOf(primary, "").asString())
        assertEquals("kotlin.String", ContextAddressing.keyOf(descriptor("G", TypeMetadata.string), "").asString())
    }
}
