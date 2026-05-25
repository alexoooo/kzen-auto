const productionMode = config.mode === "production";
config.watch = ! productionMode;


// https://youtrack.jetbrains.com/issue/KT-50826
config.performance = {
    hints: false
};


// materialIcons.kt resolves icons dynamically via require.context('@mui/icons-material', ...).
// @mui/icons-material's package.json `exports` map blocks webpack from scanning the package
// directory, so the context build fails. Alias the bare module to its filesystem path so
// require.context can enumerate files directly.
config.resolve = config.resolve || {};
config.resolve.alias = config.resolve.alias || {};
config.resolve.alias['@mui/icons-material$'] = require('path').resolve(
    __dirname, '../../node_modules/@mui/icons-material');