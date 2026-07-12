package tech.kzen.auto.server.service.target

import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetSpec


/**
 * One target type's server-side locate handling. Built-ins are constructed with the
 * [TargetLocator] service; a third-party type calls [TargetLocator.register] (e.g. from its
 * module's initialization) — no edit to any shared file. The notation-side counterpart is
 * [tech.kzen.auto.common.objects.document.target.TargetSpecType].
 */
interface TargetTypeLocator {
    fun canLocate(spec: TargetSpec): Boolean


    /**
     * [context] provides the shared machinery: visual matching over a Target document's crops
     * ([TargetLocator.locateElement]) and policy selection ([TargetLocator.selectByPolicy]).
     */
    suspend fun locate(
        spec: TargetSpec,
        driver: RemoteWebDriver,
        context: TargetLocator
    ): TargetLocator.Result
}
