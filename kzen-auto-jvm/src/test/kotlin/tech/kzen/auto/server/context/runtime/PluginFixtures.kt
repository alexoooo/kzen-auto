package tech.kzen.auto.server.context.runtime


/** Java sources for folder-plugin fixtures compiled at test time by [PluginUniverseBuilder]. */
object PluginFixtures {
    /** A `ReaderCapability` implemented from Java, including the suspend methods' continuation signatures. */
    fun readerCapability(packageName: String, simpleName: String, namespace: String, readerName: String, throwing: Boolean = false): String {
        val ctor = if (throwing) "throw new IllegalStateException(\"$simpleName cannot be constructed\");" else ""
        return """
            package $packageName;

            import tech.kzen.auto.common.data.read.ContentCapabilityIdentity;
            import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity;
            import tech.kzen.auto.common.data.read.ReaderConfig;
            import tech.kzen.auto.plugin.api.data.ReaderCapability;
            import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest;
            import tech.kzen.auto.plugin.api.data.ReaderOpenRequest;
            import tech.kzen.lib.common.exec.ExecutionValue;
            import tech.kzen.lib.common.exec.MapExecutionValue;
            import kotlin.coroutines.Continuation;
            import java.util.Map;

            public class $simpleName implements ReaderCapability {
                public static final ReaderCapabilityIdentity IDENTITY = new ReaderCapabilityIdentity("$namespace", "$readerName", "1");
                private static final ReaderConfig CONFIG = new ReaderConfig() {};

                public $simpleName() { $ctor }

                @Override public ReaderCapabilityIdentity getIdentity() { return IDENTITY; }
                @Override public ReaderConfig decode(ExecutionValue config) { return CONFIG; }
                @Override public void validate(ReaderConfig config) {}
                @Override public ReaderConfig canonicalize(ReaderConfig config) { return CONFIG; }
                @Override public ExecutionValue encode(ReaderConfig config) { return new MapExecutionValue(Map.of()); }
                @Override public ContentCapabilityIdentity requiredContent(ReaderConfig config) { return ContentCapabilityIdentity.Companion.getSequentialBytes(); }
                @Override public Object open(ReaderOpenRequest request, Continuation<? super tech.kzen.auto.common.data.api.DataCursor> c) { throw new UnsupportedOperationException(); }
                @Override public Object inspect(ReaderInspectionRequest request, Continuation<? super tech.kzen.lib.common.exec.data.shape.DataShape> c) { throw new UnsupportedOperationException(); }
            }
        """.trimIndent()
    }

    fun servicesEntry(): String = "META-INF/services/tech.kzen.auto.plugin.api.data.ReaderCapability"


    /** A `@Reflect` class plus a hand-written `ModuleReflection` registering it, the KSP-generated shape. */
    fun reflectClass(packageName: String, simpleName: String): String = """
        package $packageName;

        @tech.kzen.lib.common.reflect.Reflect
        public class $simpleName {
            private final String label;
            public $simpleName(String label) { this.label = label; }
            public String label() { return label; }
        }
    """.trimIndent()

    fun moduleReflection(packageName: String, simpleName: String, reflectClassSimpleName: String): String = """
        package $packageName;

        import tech.kzen.lib.common.reflect.ModuleReflection;
        import tech.kzen.lib.common.reflect.ReflectionRegistry;
        import java.util.List;
        import java.util.Map;

        public class $simpleName implements ModuleReflection {
            @Override
            public void register(ReflectionRegistry registry) {
                registry.put("$packageName.$reflectClassSimpleName", List.of("label"), Map.of(),
                        args -> new $reflectClassSimpleName((String) args.get(0)));
            }
        }
    """.trimIndent()

    fun moduleReflectionServicesEntry(): String = "META-INF/services/tech.kzen.lib.common.reflect.ModuleReflection"
}
