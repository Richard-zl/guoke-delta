<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户消费榜单</span>
          <el-select v-model="limit" style="width:120px" @change="fetchData">
            <el-option :value="20" label="TOP 20" />
            <el-option :value="50" label="TOP 50" />
            <el-option :value="100" label="TOP 100" />
          </el-select>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="排名" width="70" type="index" :index="i => i + 1">
          <template #default="{ $index }">
            <span :class="['rank', $index < 3 ? 'rank-top' : '']">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :src="row.avatar" :size="32" />
              <span class="nickname">{{ row.nickname || ('ID: ' + row.id) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="order_count" label="订单数" width="100" />
        <el-table-column label="消费总额" width="140">
          <template #default="{ row }"><span class="amount">¥{{ Number(row.total_amount || 0).toFixed(2) }}</span></template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getSpendingRank } from '@/api/dashboard'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const list = ref([])
const limit = ref(50)

async function fetchData() {
  loading.value = true
  try {
    const res = await getSpendingRank({ limit: limit.value }, userStore.role)
    list.value = res.data || []
  } finally { loading.value = false }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.nickname { font-weight: 500; }
.amount { color: #ff6b2b; font-weight: 600; }
.rank { display: inline-block; width: 28px; height: 28px; line-height: 28px; text-align: center; border-radius: 50%; font-size: 13px; font-weight: bold; background: #f0f2f5; color: #606266; }
.rank-top { background: linear-gradient(135deg, #ff9a44, #ff6b2b); color: #fff; }
</style>
