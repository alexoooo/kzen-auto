package tech.kzen.auto.server.service.exec

import tech.kzen.lib.common.model.instance.ObjectCreationFailure
import tech.kzen.lib.common.model.instance.ObjectInstance


/**
 * Outcome of asking [GraphInstanceCache] for one object: the instance, the creation failure that stopped it,
 *  or nothing at all when the location has no (successful, policy-allowed) definition to build from.
 */
sealed class ObjectInstanceAttempt {
    data class Created(
        val objectInstance: ObjectInstance
    ): ObjectInstanceAttempt()


    data class Failed(
        val failure: ObjectCreationFailure
    ): ObjectInstanceAttempt()


    data object Undefined: ObjectInstanceAttempt()
}
