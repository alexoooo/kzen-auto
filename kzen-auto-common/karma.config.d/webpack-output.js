// karma-webpack bundles each run into a new os.tmpdir()/_karma_webpack_<random> that nothing deletes; bundle into
// one directory under this test package in build/ instead, emptied at the start of each run
;(function(config) {
    const fs = require('fs');
    const path = require('path');
    const outputPath = path.resolve(config.basePath, 'karma-webpack');
    fs.rmSync(outputPath, {recursive: true, force: true});
    config.webpack.output = Object.assign({}, config.webpack.output, {path: outputPath});
})(config);
