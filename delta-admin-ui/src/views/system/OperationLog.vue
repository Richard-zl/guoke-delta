<template>
  <div class="page-container">
    <el-card>
      <template #header><span>操作日志</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item>
          <el-select v-model="query.module" placeholder="模块" clearable>
            <el-option label="商品" value="product" /><el-option label="订单" value="order" />
            <el-option label="用户" value="user" /><el-option label="打手" value="player" />
            <el-option label="提现" value="withdraw" /><el-option label="投诉" value="complaint" />
            <el-option label="系统" value="system" /><el-option label="登录" value="auth" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.operatorType" placeholder="操作者类型" clearable>
            <el-option label="管理员" value="ADMIN" /><el-option label="客服" value="CS" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button><el-button @click="query.module='';query.operatorType='';fetchData()">重置</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="operation" label="操作" width="150" />
        <el-table-column prop="operatorType" label="操作者类型" width="100" />
        <el-table-column prop="operatorName" label="操作者" width="120" />
        <el-table-column prop="targetType" label="目标类型" width="100" />
        <el-table-column prop="targetId" label="目标ID" width="80" />
        <el-table-column prop="detail" label="详情" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getOperationLogList } from '@/api/system'
import Pagination from '@/components/Pagination.vue'
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 20, module: '', operatorType: '' })
async function fetchData() {
  loading.value = true
  try { const res = await getOperationLogList(query); list.value = res.data.records; total.value = Number(res.data.total) }
  finally { loading.value = false }
}
onMounted(fetchData)
</script>
<style scoped>.search-form { margin-bottom: 16px; }</style>
