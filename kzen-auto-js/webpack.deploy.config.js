// https://github.com/webpack-contrib/copy-webpack-plugin/issues/59
var fs = require('fs');
var gracefulFs = require('graceful-fs');
gracefulFs.gracefulify(fs);


const webpack = require("webpack");
const path = require("path");

const dist = path.resolve(__dirname, "build/dist");

module.exports = {
    entry: {
        main: "main"
    },
    output: {
        filename: "[name].bundle.js",
        path: dist,
        publicPath: ""
    },
    module: {
        rules: [{
            test: /\.css$/,
            use: [
                'style-loader',
                'css-loader'
            ]
        }]
    },
    resolve: {
        modules: [
            path.resolve(__dirname, "build/kotlin-js-min/main"),
            path.resolve(__dirname, "src/main/web/"),
            path.resolve(__dirname, "node_modules/")
        ]
    },
    plugins: [
//        new HtmlWebpackPlugin({
//            title: 'Kzen Auto'
//        }),
//        new UglifyJSPlugin({
//            sourceMap: true,
////            sourceMap: false,
////            minimize: true,
////            output: {
////                comments: false
////            }
//        })
    ]
};
