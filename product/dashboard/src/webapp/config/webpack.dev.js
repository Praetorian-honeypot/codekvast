process.env.CODEKVAST_VERSION = 'dev';
process.env.ENV = 'development';

var webpackMerge = require('webpack-merge');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var commonConfig = require('./webpack.common.js');
var helpers = require('./helpers');

module.exports = webpackMerge(commonConfig, {
    devtool: 'cheap-module-eval-source-map',

    output: {
        path: helpers.root('dist'),
        publicPath: '/',
        filename: '[name].js',
        chunkFilename: '[id].chunk.js'
    },

    plugins: [
        new ExtractTextPlugin('[name].css')
    ],

    devServer: {
        inline: true,
        port: 8089,
        historyApiFallback: {
            disableDotRule: true
        },
        stats: 'minimal',
        headers: {
            'Access-Control-Allow-Origin': 'http://localhost:8088',
            'Access-Control-Allow-Headers': 'Content-Type',
            'Access-Control-Allow-Credentials': true
        },
        proxy: {
            '/api-docs': 'http://localhost:8081',
            '/dashboard': 'http://localhost:8081',
            '/swagger': 'http://localhost:8081',
            '/webjars': 'http://localhost:8081'
        }
    }
});
