// https://github.com/webpack-contrib/copy-webpack-plugin/issues/59
var fs = require('fs');
var gracefulFs = require('graceful-fs');
gracefulFs.gracefulify(fs);


module.exports = function (config) {
    config.set({
        frameworks: ['mocha', 'browserify'],
        reporters: ['mocha'],
        files: [
            'build/node_modules/*.js',
            'build/classes/kotlin/main/*.js',
            'build/classes/kotlin/test/*.js'
        ],
        exclude: [],
        colors: true,
        autoWatch: false,
        browsers: [
            'ChromeHeadlessNoSandbox'
        ],
        customLaunchers: {
            ChromeHeadlessNoSandbox: {
                base: 'ChromeHeadless',
                flags: ['--no-sandbox']
            }
        },
        captureTimeout: 10000,
        singleRun: true,
        reportSlowerThan: 500,
        preprocessors: {
            'build/**/*.js': ['browserify'],
        }
    })
};
