package tech.kzen.auto.plugin.api


/**
 * The plugin SPI compatibility version a plugin's `META-INF/kzen/plugin.yaml` may declare (`spi: 1`). A plugin
 * built against another version is refused at boot by name, before any linkage failure could occur. Bump when
 * a published SPI type changes incompatibly.
 */
object PluginSpiVersion {
    const val current: Int = 1
}
