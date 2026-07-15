<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <span>聊天记录</span>
        <span class="header-tip">只读，用于投诉核查与合规审计，不可修改或删除</span>
      </template>
      <el-table :data="list" v-loading="loading" stripe @row-click="goDetail">
        <el-table-column prop="id" label="会话ID" width="100" />
        <el-table-column prop="targetName" label="参与者" min-width="220" show-overflow-tooltip />
        <el-table-column prop="lastMessage" label="最后消息" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastMessage || '暂无' }}</template>
        </el-table-column>
        <el-table-column prop="lastMessageAt" label="最后消息时间" width="170" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><el-tag size="small">{{ row.status === 'ACTIVE' ? '进行中' : (row.status || '-') }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" @click.stop>
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">查看记录</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminChatSessionList, csChatSessionAllList } from '@/api/business'
import { useUserStore } from '@/stores/user'
import Pagination from '@/components/Pagination.vue'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })

async function fetchData() {
  loading.value = true
  try {
    const fn = isAdmin ? adminChatSessionList : csChatSessionAllList
    const res = await fn({
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    const data = res.data || {}
    list.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function goDetail(row) {
  router.push({ name: 'ChatRecordDetail', params: { id: row.id } })
}

onMounted(fetchData)
</script>

<style scoped>
.header-tip { margin-left: 12px; font-size: 12px; color: #909399; }
</style>
