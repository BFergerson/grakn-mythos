module.exports = {
    entry: {
        "app": [
            './src/main/javascript/index.js',
            './src/main/javascript/echarts-theme.js',
            './src/main/javascript/grakn-graph.js'
        ],
        "info": [
            './src/main/javascript/info.js'
        ],
        "grakn.editor": './src/main/javascript/grakn-editor.js',
        "editor.worker": 'monaco-editor/esm/vs/editor/editor.worker.js',
        "editor.loader": 'monaco-editor/min/vs/loader.js'
    },
    output: {
        filename: '[name].bundle.js',
    },
    module: {
        rules: [{
            test: /\.tsx?$/,
            use: 'ts-loader',
            exclude: /node_modules/
        }, {
            test: /\.css$/,
            use: ['style-loader', 'css-loader']
        }, {
            test: /\.ttf$/,
            use: ['file-loader']
        }]
    },
    resolve: {
        modules: ['node_modules'],
        extensions: ['.tsx', '.ts', '.js', '.ttf']
    },
    mode: 'production',
    node: {
        fs: 'empty',
        global: true,
        crypto: 'empty',
        tls: 'empty',
        net: 'empty',
        process: true,
        module: false,
        clearImmediate: false,
        setImmediate: false
    }
}
