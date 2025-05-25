const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');

module.exports = {
    mode: 'development',
    entry: './index.js',
    output: {
        path: path.resolve(__dirname, '../src/main/resources/public'),
        filename: '[name].[contenthash].js',
        clean: true,
        chunkFilename: 'chunks/[name].[contenthash].js',
    },
    optimization: {
        splitChunks: {
            chunks: 'all',
        },
    },
    resolve: {
        alias: {
            'roughjs/bin/rough': 'roughjs/bin/rough.js',
            'roughjs/bin/generator': 'roughjs/bin/generator.js',
            'roughjs/bin/math': 'roughjs/bin/math.js',
            'roughjs/bin/renderer': 'roughjs/bin/renderer.js',
            'roughjs/bin/vectorizer': 'roughjs/bin/vectorizer.js'
        },
        extensions: ['.js', '.jsx', '.json']
    },
    module: {
        rules: [
            {
                test: /\.js$/,
                exclude: /node_modules/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        presets: ['@babel/preset-env', '@babel/preset-react']
                    }
                }
            },
            {
                test: /\.css$/,
                use: [
                    MiniCssExtractPlugin.loader,
                    'css-loader'
                ]
            }
        ]
    },
    plugins: [
        new MiniCssExtractPlugin({
            filename: 'css/[name].[contenthash].css',
            chunkFilename: 'css/[id].[contenthash].css',
        }),
        new HtmlWebpackPlugin({
            template: './index.html',
            filename: 'index.html',
            inject: 'head',
            minify: {
                removeComments: true,
                collapseWhitespace: true,
                removeRedundantAttributes: true,
                useShortDoctype: true,
                removeEmptyAttributes: true,
                removeStyleLinkTypeAttributes: true,
                keepClosingSlash: true,
                minifyJS: true,
                minifyCSS: true,
                minifyURLs: true,
            }
        }),
    ],
    devServer: {
        static: {
            directory: path.join(__dirname, '../src/main/resources/public'),
        },
        compress: true,
        port: 9000,
        hot: true
    }
}; 