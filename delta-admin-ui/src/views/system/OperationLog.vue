<template>
  <div class="page-container">
    <el-card>
      <template #header><span>操作日志</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item>
          <el-select v-model="query.module" placeholder="全部模块" clearable>
            <el-option label="全部" value="" />
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.operatorType" placeholder="全部角色" clearable>
            <el-option label="全部" value="" />
            <el-option v-for="item in operatorOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="模块" width="100">
          <template #default="{ row }">{{ moduleLabel(row.module) }}</template>
        </el-table-column>
        <el-table-column prop="operation" label="操作" width="150" />
        <el-table-column label="操作者类型" width="110">
          <template #default="{ row }">{{ operatorLabel(row.operatorType) }}</template>
        </el-table-column>
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

const moduleOptions = [
  { label: '商品', value: 'product' },
  { label: '订单', value: 'order' },
  { label: '用户', value: 'user' },
  { label: '打手', value: 'player' },
  { label: '提现', value: 'withdraw' },
  { label: '投诉', value: 'complaint' },
  { label: '系统', value: 'system' },
  { label: '登录', value: 'auth' },
]
const operatorOptions = [
  { label: '管理员', value: 'ADMIN' },
  { label: '客服', value: 'CS' },
  { label: '运营', value: 'OPERATOR' },
  { label: '店长', value: 'MANAGER' },
]
const moduleMap = Object.fromEntries(moduleOptions.map((item) => [item.value, item.label]))
const operatorMap = Object.fromEntries(operatorOptions.map((item) => [item.value, item.label]))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 20, module: '', operatorType: '' })

function moduleLabel(value) {
  return moduleMap[value] || value || '-'
}
function operatorLabel(value) {
  return operatorMap[value] || value || '-'
}
function resetQuery() {
  query.module = ''
  query.operatorType = ''
  query.pageNum = 1
  fetchData()
}
async function fetchData() {
  loading.value = true
  try {
    const res = await getOperationLogList(query)
    list.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}
onMounted(fetchData)
</script>
<style scoped>.search-form { margin-bottom: 16px; }</style>
