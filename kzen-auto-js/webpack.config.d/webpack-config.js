// NOTE: this module is bundled by esbuild (jsEsbuildBundle), not webpack — the webpack tasks are
// disabled in build.gradle.kts. This config only applies if webpack is re-enabled as a fallback.

const productionMode = config.mode === "production";
config.watch = ! productionMode;


// https://youtrack.jetbrains.com/issue/KT-50826
config.performance = {
    hints: false
};
