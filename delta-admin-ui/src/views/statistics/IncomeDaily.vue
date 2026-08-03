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

    <!-- 确认口径汇总 -->
    <div class="section-label">
      确认口径汇总
      <el-tooltip content="按确认日统计，含待入账（settled=2）；反映当日确认的有效订单与抽成" placement="top">
        <el-icon class="tip-icon"><QuestionFilled /></el-icon>
      </el-tooltip>
    </div>
    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">确认订单数</div>
          <div class="summary-value">{{ summary.confirmOrderCount ?? summary.orderCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">确认订单金额</div>
          <div class="summary-value">¥{{ formatAmount(summary.confirmOrderAmount ?? summary.orderAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">确认打手收入</div>
          <div class="summary-value">¥{{ formatAmount(summary.confirmPlayerIncome ?? summary.playerIncome) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card highlight">
          <div class="summary-label">确认平台抽成</div>
          <div class="summary-value">¥{{ formatAmount(summary.confirmCommissionIncome ?? summary.commissionIncome) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 已入账口径汇总 -->
    <div class="section-label">
      已入账口径汇总
      <el-tooltip content="按入账日统计，仅 settled=1；反映打手真正到账的金额" placement="top">
        <el-icon class="tip-icon"><QuestionFilled /></el-icon>
      </el-tooltip>
    </div>
    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">已入账订单数</div>
          <div class="summary-value">{{ summary.settledOrderCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">已入账订单金额</div>
          <div class="summary-value">¥{{ formatAmount(summary.settledOrderAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">已入账打手收入</div>
          <div class="summary-value">¥{{ formatAmount(summary.settledPlayerIncome) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card highlight">
          <div class="summary-label">已入账平台抽成</div>
          <div class="summary-value">¥{{ formatAmount(summary.settledCommissionIncome) }}</div>
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
              <div class="detail-block">
                <div class="detail-title">
                  <span>{{ row.statDate }} 确认订单</span>
                  <el-tag size="small" type="info">{{ (row.confirmOrders || row.orders || []).length }} 笔</el-tag>
                </div>
                <el-empty
                  v-if="!(row.confirmOrders || row.orders || []).length"
                  description="当天没有确认订单"
                  :image-size="64"
                />
                <el-table
                  v-else
                  :data="row.confirmOrders || row.orders"
                  size="small"
                  border
                  class="detail-table"
                >
                  <el-table-column prop="orderNo" label="订单号" min-width="170" />
                  <el-table-column prop="productName" label="商品" min-width="160" show-overflow-tooltip />
                  <el-table-column label="状态" min-width="90">
                    <template #default="{ row: order }">
                      <el-tag v-if="Number(order.settled) === 2" size="small" type="warning">待入账</el-tag>
                      <el-tag v-else size="small" type="success">已入账</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="userNickname" label="用户" min-width="100">
                    <template #default="{ row: order }">{{ order.userNickname || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="playerNickname" label="打手" min-width="100">
                    <template #default="{ row: order }">{{ order.playerNickname || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="订单金额" min-width="100" align="right">
                    <template #default="{ row: order }">¥{{ formatAmount(order.amount) }}</template>
                  </el-table-column>
                  <el-table-column label="打手收入" min-width="100" align="right">
                    <template #default="{ row: order }">¥{{ formatAmount(order.playerIncome) }}</template>
                  </el-table-column>
                  <el-table-column label="平台抽成" min-width="100" align="right">
                    <template #default="{ row: order }">¥{{ formatAmount(order.commissionIncome) }}</template>
                  </el-table-column>
                  <el-table-column label="结算时间" min-width="160">
                    <template #default="{ row: order }">
                      {{ order.settleTime ? formatDateTime(order.settleTime) : '待入账' }}
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="detail-block">
                <div class="detail-title">
                  <span>{{ row.statDate }} 入账订单</span>
                  <el-tag size="small" type="success">{{ (row.settledOrders || []).length }} 笔</el-tag>
                </div>
                <el-empty
                  v-if="!(row.settledOrders || []).length"
                  description="当天没有入账订单"
                  :image-size="64"
                />
                <el-table
                  v-else
                  :data="row.settledOrders"
                  size="small"
                  border
                  class="detail-table"
                >
                  <el-table-column prop="orderNo" label="订单号" min-width="170" />
                  <el-table-column prop="productName" label="商品" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="userNickname" label="用户" min-width="100">
                    <template #default="{ row: order }">{{ order.userNickname || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="playerNickname" label="打手" min-width="100">
                    <template #default="{ row: order }">{{ order.playerNickname || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="订单金额" min-width="100" align="right">
                    <template #default="{ row: order }">¥{{ formatAmount(order.amount) }}</template>
                  </el-table-column>
                  <el-table-column label="打手收入" min-width="100" align="right">
                    <template #default="{ row: order }">¥{{ formatAmount(order.playerIncome) }}</template>
                  </el-table-column>
                  <el-table-column label="平台抽成" min-width="100" align="right">
                    <template #default="{ row: order }">¥{{ formatAmount(order.commissionIncome) }}</template>
                  </el-table-column>
                  <el-table-column prop="settleTime" label="结算时间" min-width="160">
                    <template #default="{ row: order }">{{ formatDateTime(order.settleTime) }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="statDate" label="日期" min-width="110" fixed />

        <el-table-column label="确认（含待入账）" align="center">
          <el-table-column label="订单数" min-width="90">
            <template #default="{ row }">{{ row.confirmOrderCount ?? row.orderCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="金额" min-width="110">
            <template #default="{ row }">¥{{ formatAmount(row.confirmOrderAmount ?? row.orderAmount) }}</template>
          </el-table-column>
          <el-table-column label="打手收入" min-width="110">
            <template #default="{ row }">¥{{ formatAmount(row.confirmPlayerIncome ?? row.playerIncome) }}</template>
          </el-table-column>
          <el-table-column label="平台抽成" min-width="110">
            <template #default="{ row }">¥{{ formatAmount(row.confirmCommissionIncome ?? row.commissionIncome) }}</template>
          </el-table-column>
        </el-table-column>

        <el-table-column label="已入账" align="center">
          <el-table-column label="订单数" min-width="90">
            <template #default="{ row }">{{ row.settledOrderCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="金额" min-width="110">
            <template #default="{ row }">¥{{ formatAmount(row.settledOrderAmount) }}</template>
          </el-table-column>
          <el-table-column label="打手收入" min-width="110">
            <template #default="{ row }">¥{{ formatAmount(row.settledPlayerIncome) }}</template>
          </el-table-column>
          <el-table-column label="平台抽成" min-width="110">
            <template #default="{ row }">¥{{ formatAmount(row.settledCommissionIncome) }}</template>
          </el-table-column>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
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

.section-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.tip-icon {
  cursor: help;
  color: var(--el-text-color-secondary);
  vertical-align: middle;
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
    font-size: 24px;
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

.detail-block + .detail-block {
  margin-top: 18px;
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
