<template>
  <div class="income-daily" v-loading="loading">
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">有效订单数</div>
          <div class="summary-value">{{ summary.orderCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">有效订单金额</div>
          <div class="summary-value">¥{{ formatAmount(summary.orderAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">打手收入</div>
          <div class="summary-value">¥{{ formatAmount(summary.playerIncome) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card highlight">
          <div class="summary-label">平台抽成</div>
          <div class="summary-value">¥{{ formatAmount(summary.commissionIncome) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover">
      <template #header>
        <div class="table-header">
          <span>每日收益表</span>
          <span class="table-range">{{ dateLabel }}</span>
        </div>
      </template>

      <el-table :data="tableData" stripe row-key="statDate">
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="income-detail">
              <div class="detail-title">
                <span>{{ row.statDate }} 收益组成详情</span>
                <el-tag size="small" type="info">有效订单 {{ row.orders?.length || 0 }} 笔</el-tag>
              </div>
              <el-empty v-if="!row.orders?.length" description="当天没有有效订单" :image-size="72" />
              <el-table v-else :data="row.orders" size="small" border class="detail-table">
                <el-table-column prop="orderNo" label="订单号" min-width="170" />
                <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
                <el-table-column prop="createdAt" label="下单时间" min-width="170">
                  <template #default="{ row: order }">{{ formatDateTime(order.createdAt) }}</template>
                </el-table-column>
                <el-table-column prop="userNickname" label="用户" min-width="110">
                  <template #default="{ row: order }">{{ order.userNickname || '-' }}</template>
                </el-table-column>
                <el-table-column prop="playerNickname" label="打手" min-width="110">
                  <template #default="{ row: order }">{{ order.playerNickname || '-' }}</template>
                </el-table-column>
                <el-table-column label="订单金额" min-width="110" align="right">
                  <template #default="{ row: order }">¥{{ formatAmount(order.amount) }}</template>
                </el-table-column>
                <el-table-column label="打手收入" min-width="110" align="right">
                  <template #default="{ row: order }">¥{{ formatAmount(order.playerIncome) }}</template>
                </el-table-column>
                <el-table-column label="平台抽成" min-width="110" align="right">
                  <template #default="{ row: order }">¥{{ formatAmount(order.commissionIncome) }}</template>
                </el-table-column>
                <el-table-column prop="settleTime" label="结算时间" min-width="170">
                  <template #default="{ row: order }">{{ formatDateTime(order.settleTime) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="statDate" label="日期" min-width="140" />
        <el-table-column prop="orderCount" label="有效订单数" min-width="120" />
        <el-table-column label="有效订单金额" min-width="140">
          <template #default="{ row }">¥{{ formatAmount(row.orderAmount) }}</template>
        </el-table-column>
        <el-table-column label="打手收入" min-width="140">
          <template #default="{ row }">¥{{ formatAmount(row.playerIncome) }}</template>
        </el-table-column>
        <el-table-column label="平台抽成" min-width="140">
          <template #default="{ row }">¥{{ formatAmount(row.commissionIncome) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getStatisticsIncomeDaily } from '@/api/dashboard'

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getDefaultRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 29)
  return [formatDate(start), formatDate(end)]
}

const loading = ref(false)
const tableData = ref([])
const summary = ref({})
const query = reactive({
  dateRange: getDefaultRange()
})

const dateLabel = computed(() => {
  const [startDate, endDate] = query.dateRange || []
  if (!startDate || !endDate) return ''
  return `${startDate} 至 ${endDate}`
})

function formatAmount(value) {
  if (value == null || value === '') return '0.00'
  return Number(value).toFixed(2)
}

function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function fetchData() {
  loading.value = true
  try {
    const [startDate, endDate] = query.dateRange || []
    const res = await getStatisticsIncomeDaily({ startDate, endDate })
    const data = res.data || {}
    tableData.value = data.list || []
    summary.value = data.summary || {}
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.dateRange = getDefaultRange()
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.income-daily {
  min-height: 100%;
}

.filter-card {
  margin-bottom: 16px;
}

.summary-row {
  margin-bottom: 16px;
}

.summary-card {
  .summary-label {
    font-size: 13px;
    color: #909399;
    margin-bottom: 10px;
  }

  .summary-value {
    font-size: 28px;
    line-height: 1.2;
    font-weight: 700;
    color: #303133;
  }

  &.highlight {
    .summary-value {
      color: #e6a23c;
    }
  }
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-range {
  font-size: 12px;
  color: #909399;
}

.income-detail {
  padding: 14px 18px 18px 48px;
  background: #f8fafc;
}

.detail-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: #172033;
  font-weight: 700;
}

.detail-table {
  border-radius: 8px;
}
</style>
