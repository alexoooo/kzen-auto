package tech.kzen.auto.server.objects.custom.test

import tech.kzen.lib.common.reflect.Reflect

@Reflect
class AdhocNamedImpl(
    private val name: String
): AdhocNamed {
    override fun name(): String =
        name
}