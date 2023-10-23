const productionMode = config.mode === "production";
config.watch = ! productionMode;


// https://youtrack.jetbrains.com/issue/KT-50826
config.performance = {
    hints: false
};