//;(function (config) {
//    const shouldRunServer = config.mode !== "production"
////    const serverUrl = 'http://localhost:8081'
////
//    if (shouldRunServer) {
//        config.watch = true;
//
////        config.devServer = config.devServer || {};
////        config.devServer.proxy = {
////            '/': {
////                target: serverUrl,
////                secure: false
////            }
////        }
//    }
//
//    // https://stackoverflow.com/questions/61009367/how-to-make-ktor-reload-js-changes-at-runtime
//    // https://discuss.kotlinlang.org/t/webpack-watch-in-ktor-js-project/18428/5
////    config.watch = true;
//})(config);

//config.watch = false;

const productionMode = config.mode === "production";
config.watch = ! productionMode;
