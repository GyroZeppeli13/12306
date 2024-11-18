const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  // 其他配置项
  devServer:{
    client:{
      overlay: false
    }
  }
})