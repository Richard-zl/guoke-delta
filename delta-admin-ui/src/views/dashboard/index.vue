<template>
  <div class="dashboard" v-loading="loading">
    <!-- ==================== 管理员仪表盘 ==================== -->
    <template v-if="isAdmin">
      <h2 class="dashboard-title">管理员仪表盘</h2>

      <!-- 今日核心数据 -->
      <div class="section-label">今日数据</div>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-blue"><el-icon :size="28"><List /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayOrders ?? '-' }}</div>
              <div class="stat-label">今日订单</div>
              <div class="stat-compare" v-if="stats.yesterdayOrders != null">
                昨日 {{ stats.yesterdayOrders }}
                <span :class="diffClass(stats.todayOrders, stats.yesterdayOrders)">{{ diffText(stats.todayOrders, stats.yesterdayOrders) }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-green"><el-icon :size="28"><Money /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">¥{{ formatAmount(stats.todayAmount) }}</div>
              <div class="stat-label">今日成交额</div>
              <div class="stat-compare" v-if="stats.yesterdayAmount != null">
                昨日 ¥{{ formatAmount(stats.yesterdayAmount) }}
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-purple"><el-icon :size="28"><User /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayNewUsers ?? '-' }}</div>
              <div class="stat-label">今日新增用户</div>
              <div class="stat-compare" v-if="stats.yesterdayNewUsers != null">
                昨日 {{ stats.yesterdayNewUsers }}
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-cyan"><el-icon :size="28"><Avatar /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayNewPlayers ?? '-' }}</div>
              <div class="stat-label">今日新增打手</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 待办事项 -->
      <div class="section-label">待办事项</div>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card alert-card" :class="{ 'has-alert': stats.pendingComplaints > 0 }">
            <div class="stat-icon bg-red"><el-icon :size="28"><Warning /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value text-danger">{{ stats.pendingComplaints ?? '-' }}</div>
              <div class="stat-label">待处理投诉</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card alert-card" :class="{ 'has-alert': stats.pendingWithdraws > 0 }">
            <div class="stat-icon bg-orange"><el-icon :size="28"><Wallet /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value text-warning">{{ stats.pendingWithdraws ?? '-' }}</div>
              <div class="stat-label">待审核提现</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-yellow"><el-icon :size="28"><Clock /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value text-warning">{{ stats.pendingAssign ?? '-' }}</div>
              <div class="stat-label">待指派订单</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-blue"><el-icon :size="28"><Loading /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value text-primary">{{ stats.inProgress ?? '-' }}</div>
              <div class="stat-label">进行中订单</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 累计数据 -->
      <div class="section-label">累计数据</div>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card mini">
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalUsers ?? '-' }}</div>
              <div class="stat-label">总用户数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card mini">
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalPlayers ?? '-' }}</div>
              <div class="stat-label">总打手数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card mini">
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalOrders ?? '-' }}</div>
              <div class="stat-label">总订单数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card mini">
            <div class="stat-info">
              <div class="stat-value">¥{{ formatAmount(stats.totalAmount) }}</div>
              <div class="stat-label">总成交额</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 近7天订单趋势 + 订单状态分布 -->
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="14">
          <el-card shadow="hover">
            <template #header><span>近7天订单趋势</span></template>
            <el-table :data="stats.orderTrend || []" stripe size="small">
              <el-table-column prop="date" label="日期" width="120" />
              <el-table-column prop="orders" label="订单数" width="100" />
              <el-table-column label="成交额">
                <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="趋势" width="200">
                <template #default="{ row }">
                  <div class="bar-wrapper">
                    <div class="bar" :style="{ width: barWidth(row.orders) }"></div>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="hover">
            <template #header><span>订单状态分布</span></template>
            <el-table :data="stats.statusDistribution || []" stripe size="small">
              <el-table-column label="状态">
                <template #default="{ row }">
                  <el-tag :type="orderStatusType(row.status)" size="small">{{ orderStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="count" label="数量" width="80" />
              <el-table-column label="占比" width="100">
                <template #default="{ row }">{{ statusPercent(row.count) }}%</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <!-- 用户统计 + 打手统计 -->
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <el-card shadow="hover" v-loading="userLoading">
            <template #header><span>用户统计</span></template>
            <div class="stat-extra" v-if="paidUserRate != null">付费用户占比：<strong>{{ paidUserRate.toFixed(1) }}%</strong></div>
            <el-table :data="newUserTrend" stripe size="small" max-height="300">
              <el-table-column prop="dt" label="日期" width="120" />
              <el-table-column prop="cnt" label="新增用户" />
              <el-table-column label="趋势" width="150">
                <template #default="{ row }">
                  <div class="bar-wrapper">
                    <div class="bar bar-purple" :style="{ width: (row.cnt / maxUserCnt * 100) + '%' }"></div>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" v-loading="playerLoading">
            <template #header><span>打手统计</span></template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="总打手数">{{ playerStats.totalPlayers ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="活跃打手">{{ playerStats.activePlayers ?? '-' }}</el-descriptions-item>
            </el-descriptions>
            <div style="margin-top:12px;font-weight:600;font-size:13px">评分分布</div>
            <el-table :data="playerStats.ratingDistribution || []" stripe size="small" style="margin-top:8px">
              <el-table-column prop="rating_level" label="评分" width="80">
                <template #default="{ row }">{{ row.rating_level }}★</template>
              </el-table-column>
              <el-table-column prop="cnt" label="人数" />
            </el-table>
            <div style="margin-top:12px;font-weight:600;font-size:13px">收入排行 TOP10</div>
            <el-table :data="playerStats.incomeRank || []" stripe size="small" style="margin-top:8px">
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="nickname" label="昵称" />
              <el-table-column label="总收入">
                <template #default="{ row }">¥{{ formatAmount(row.total_income) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- ==================== 客服工作台 ==================== -->
    <template v-else>
      <h2 class="dashboard-title">客服工作台</h2>

      <!-- 待办事项 -->
      <div class="section-label">待办事项</div>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card alert-card" :class="{ 'has-alert': stats.pendingAssign > 0 }">
            <div class="stat-icon bg-yellow"><el-icon :size="28"><Clock /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value text-warning">{{ stats.pendingAssign ?? '-' }}</div>
              <div class="stat-label">待指派订单</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card alert-card" :class="{ 'has-alert': stats.pendingComplaints > 0 }">
            <div class="stat-icon bg-red"><el-icon :size="28"><Warning /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value text-danger">{{ stats.pendingComplaints ?? '-' }}</div>
              <div class="stat-label">待处理投诉</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-orange"><el-icon :size="28"><EditPen /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value text-warning">{{ stats.processingComplaints ?? '-' }}</div>
              <div class="stat-label">进行中仲裁</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-purple"><el-icon :size="28"><ChatDotRound /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value text-primary">{{ stats.pendingChatSessions ?? '-' }}</div>
              <div class="stat-label">待回复会话</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 今日统计 -->
      <div class="section-label">今日统计</div>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-blue"><el-icon :size="28"><List /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayOrders ?? '-' }}</div>
              <div class="stat-label">今日新增订单</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-green"><el-icon :size="28"><CircleCheck /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayCompleted ?? '-' }}</div>
              <div class="stat-label">今日完成订单</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-red"><el-icon :size="28"><Warning /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayComplaints ?? '-' }}</div>
              <div class="stat-label">今日投诉</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon bg-cyan"><el-icon :size="28"><Avatar /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.activePlayers ?? '-' }}</div>
              <div class="stat-label">在线打手</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 概览数据 -->
      <div class="section-label">平台概览</div>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card mini">
            <div class="stat-info">
              <div class="stat-value">¥{{ formatAmount(stats.todayAmount) }}</div>
              <div class="stat-label">今日成交额</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card mini">
            <div class="stat-info">
              <div class="stat-value text-warning">{{ stats.pendingWithdraws ?? '-' }}</div>
              <div class="stat-label">待审核提现</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card mini">
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalUsers ?? '-' }}</div>
              <div class="stat-label">总用户数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card mini">
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalPlayers ?? '-' }}</div>
              <div class="stat-label">总打手数</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 待指派订单列表 -->
      <el-card shadow="hover" style="margin-top: 16px" v-if="stats.pendingOrders && stats.pendingOrders.length">
        <template #header><span>待指派订单 <el-tag type="danger" size="small">{{ stats.pendingAssign }}</el-tag></span></template>
        <el-table :data="stats.pendingOrders" stripe size="small">
          <el-table-column prop="orderNo" label="订单号" width="180" />
          <el-table-column prop="productName" label="商品" show-overflow-tooltip />
          <el-table-column prop="amount" label="金额" width="100">
            <template #default="{ row }">¥{{ row.amount }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="下单时间" width="170" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getAdminDashboard, getCsDashboard, getStatisticsUser, getStatisticsPlayer } from '@/api/dashboard'
import {
  List, Money, User, Avatar, Warning, Wallet, Clock, Loading,
  EditPen, ChatDotRound, CircleCheck
} from '@element-plus/icons-vue'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'admin')
const loading = ref(false)
const stats = ref({})

// 用户统计 & 打手统计（admin 独有）
const userLoading = ref(false)
const newUserTrend = ref([])
const paidUserRate = ref(null)
const maxUserCnt = computed(() => Math.max(...newUserTrend.value.map(r => r.cnt), 1))

const playerLoading = ref(false)
const playerStats = ref({})

// 订单状态映射
const statusLabelMap = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ASSIGNED: '已指派', ACCEPTED: '已接单',
  WAITING_TEAMMATE: '组队中', IN_PROGRESS: '进行中', COMPLETED: '待确认',
  CONFIRMED: '已完成', REVIEWED: '已评价', CANCELLED: '已取消',
  REFUNDING: '退款中', REFUNDED: '已退款', DISPUTED: '争议中', ARBITRATED: '已仲裁'
}
const statusTypeMap = {
  PENDING_PAYMENT: 'info', PAID: '', ASSIGNED: '', ACCEPTED: '',
  WAITING_TEAMMATE: 'warning', IN_PROGRESS: 'warning', COMPLETED: 'success',
  CONFIRMED: 'success', REVIEWED: 'success', CANCELLED: 'info',
  REFUNDING: 'danger', REFUNDED: 'info', DISPUTED: 'danger', ARBITRATED: 'info'
}
function orderStatusLabel(s) { return statusLabelMap[s] || s }
function orderStatusType(s) { return statusTypeMap[s] || '' }

// 趋势柱状条宽度
function barWidth(val) {
  const trend = stats.value.orderTrend || []
  const max = Math.max(...trend.map(t => Number(t.orders) || 0), 1)
  return Math.round((Number(val) / max) * 100) + '%'
}

// 状态占比
function statusPercent(count) {
  const dist = stats.value.statusDistribution || []
  const total = dist.reduce((s, r) => s + Number(r.count || 0), 0)
  return total > 0 ? ((Number(count) / total) * 100).toFixed(1) : '0.0'
}

// 格式化金额
function formatAmount(val) {
  if (val == null || val === '-') return '0.00'
  return Number(val).toFixed(2)
}

// 对比差值
function diffText(cur, prev) {
  const d = Number(cur || 0) - Number(prev || 0)
  if (d > 0) return `↑${d}`
  if (d < 0) return `↓${Math.abs(d)}`
  return '-'
}
function diffClass(cur, prev) {
  const d = Number(cur || 0) - Number(prev || 0)
  if (d > 0) return 'diff-up'
  if (d < 0) return 'diff-down'
  return ''
}

async function fetchDashboard() {
  loading.value = true
  try {
    const res = isAdmin.value ? await getAdminDashboard() : await getCsDashboard()
    stats.value = res.data || {}
  } catch (e) {
    // request interceptor handles errors
  } finally {
    loading.value = false
  }
}

async function loadUserStats() {
  userLoading.value = true
  try {
    const res = await getStatisticsUser()
    const d = res.data || {}
    newUserTrend.value = d.newUserTrend || []
    paidUserRate.value = d.paidUserRate ?? null
  } finally { userLoading.value = false }
}

async function loadPlayerStats() {
  playerLoading.value = true
  try { const res = await getStatisticsPlayer(); playerStats.value = res.data || {} }
  finally { playerLoading.value = false }
}

onMounted(() => {
  fetchDashboard()
  if (isAdmin.value) {
    loadUserStats()
    loadPlayerStats()
  }
})
</script>

<style lang="scss" scoped>
.dashboard {
  min-height: 100%;
}
.dashboard-title {
  position: relative;
  margin: 0 0 18px;
  padding-left: 12px;
  font-size: 22px;
  line-height: 1.25;
  color: #172033;
  font-weight: 800;
  &::before {
    content: "";
    position: absolute;
    left: 0;
    top: 3px;
    width: 4px;
    height: 22px;
    border-radius: 999px;
    background: #1d4ed8;
  }
}
.section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  margin: 20px 0 10px;
  font-weight: 700;
  letter-spacing: 0;
  &::after {
    content: "";
    flex: 1;
    height: 1px;
    background: #e3eaf3;
  }
  &:first-of-type { margin-top: 0; }
}

// 统计卡片
.stat-card {
  display: flex;
  align-items: center;
  margin-bottom: 0;
  border: 1px solid #e3eaf3;
  border-radius: 8px;
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 18px;
    width: 100%;
    min-height: 104px;
    background: linear-gradient(180deg, #ffffff, #fbfdff);
  }
  &:hover {
    transform: translateY(-2px);
    border-color: #cbd8e8;
  }
  &.mini :deep(.el-card__body) {
    justify-content: center;
    text-align: center;
    min-height: 92px;
  }
}
.alert-card.has-alert {
  border-left: 4px solid #ef4444;
}
.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.bg-blue { background: linear-gradient(135deg, #2563eb, #1d4ed8); }
.bg-green { background: linear-gradient(135deg, #16a34a, #15803d); }
.bg-purple { background: linear-gradient(135deg, #7c3aed, #5b21b6); }
.bg-cyan { background: linear-gradient(135deg, #0891b2, #0e7490); }
.bg-red { background: linear-gradient(135deg, #ef4444, #b91c1c); }
.bg-orange { background: linear-gradient(135deg, #f59e0b, #b45309); }
.bg-yellow { background: linear-gradient(135deg, #eab308, #ca8a04); }

.stat-info {
  flex: 1;
  min-width: 0;
}
.stat-value {
  font-size: 27px;
  font-weight: 800;
  line-height: 1.2;
  color: #172033;
}
.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
  font-weight: 600;
}
.stat-compare {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}
.diff-up { color: #f56c6c; margin-left: 4px; }
.diff-down { color: #67c23a; margin-left: 4px; }

.text-danger { color: #f56c6c !important; }
.text-warning { color: #e6a23c !important; }
.text-primary { color: #409eff !important; }

// 趋势条
.bar-wrapper {
  background: #eef3f9;
  border-radius: 999px;
  height: 14px;
  overflow: hidden;
}
.bar {
  height: 100%;
  background: linear-gradient(90deg, #60a5fa, #1d4ed8);
  border-radius: 999px;
  min-width: 4px;
  transition: width 0.3s;
  &.bar-purple {
    background: linear-gradient(90deg, #a78bfa, #6d28d9);
  }
}
.stat-extra {
  padding: 8px 0 12px;
  color: #42526e;
  font-size: 13px;
}

:deep(.el-card:not(.stat-card)) {
  border-radius: 8px;
}

:deep(.el-card__header) {
  font-weight: 700;
}

@media (max-width: 1200px) {
  :deep(.el-col-6) {
    max-width: 50%;
    flex: 0 0 50%;
    margin-bottom: 12px;
  }
}
</style>
