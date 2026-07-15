<template>
  <div class="error-page">
    <h1>403</h1>
    <p>抱歉，您没有权限访问该页面</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>
<style scoped>
.error-page { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; }
.error-page h1 { font-size: 120px; color: #dcdfe6; margin: 0; }
.error-page p { font-size: 18px; color: #606266; margin: 20px 0; }
</style>
