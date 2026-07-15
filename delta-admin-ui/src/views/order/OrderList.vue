<template>
  <div class="page-container">
    <el-card>
      <template #header><span>订单管理</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item><el-input v-model="query.orderNo" placeholder="订单号" clearable @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="订单状态" clearable @change="onStatusChange">
            <el-option v-for="(v, k) in orderStatusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            @change="onDateRangeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="query.userId"
            filterable
            remote
            clearable
            placeholder="用户"
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="min-width: 200px"
          >
            <el-option v-for="u in userOptions" :key="u.id" :label="formatUserLabel(u)" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="query.playerId"
            filterable
            remote
            clearable
            placeholder="打手"
            :remote-method="searchPlayersForFilter"
            :loading="playerFilterLoading"
            style="min-width: 200px"
          >
            <el-option v-for="p in playerFilterOptions" :key="p.id" :label="formatPlayerFilterLabel(p)" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="query.productId"
            filterable
            remote
            clearable
            placeholder="商品"
            :remote-method="searchProducts"
            :loading="productSearchLoading"
            style="min-width: 220px"
          >
            <el-option v-for="p in productOptions" :key="p.id" :label="formatProductLabel(p)" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="quick-filters">
        <span class="quick-label">快捷：</span>
        <el-button :type="activeQuick === 'unassigned' ? 'primary' : 'default'" size="small" @click="applyQuick('unassigned')">待指派</el-button>
        <el-button :type="activeQuick === 'inProgress' ? 'primary' : 'default'" size="small" @click="applyQuick('inProgress')">进行中</el-button>
        <el-button :type="activeQuick === 'afterSale' ? 'primary' : 'default'" size="small" @click="applyQuick('afterSale')">售后相关</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
        <el-table-column prop="userNickname" label="用户" width="120">
          <template #default="{ row }">{{ row.userNickname || ('ID: ' + row.userId) }}</template>
        </el-table-column>
        <el-table-column prop="playerName" label="主接打手" width="120">
          <template #default="{ row }">{{ row.playerName || (row.playerId ? 'ID: ' + row.playerId : '未指派') }}</template>
        </el-table-column>
        <el-table-column prop="playerName2" label="辅助打手" width="120">
          <template #default="{ row }">{{ row.playerName2 || (row.playerId2 ? 'ID: ' + row.playerId2 : '-') }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="100"><template #default="{ row }">¥{{ row.amount }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><el-tag :type="orderTagType(row.status)" size="small">{{ orderStatusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button v-if="canAssign(row)" link type="warning" @click="openAssignDialog(row)">{{ row.status === 'ASSIGNED' ? '重新指派' : '指派' }}</el-button>
            <el-button v-if="['ASSIGNED','IN_PROGRESS'].includes(row.status)" link type="danger" @click="handleRefund(row)">退款</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="700px">
      <el-descriptions :column="2" border v-if="detail" v-loading="detailLoading">
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="orderTagType(detail.status)" size="small">{{ orderStatusLabel(detail.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="商品">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ detail.variantName || detail.specInfo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单价">{{ detail.unitPrice != null ? '¥' + detail.unitPrice : '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ detail.quantity > 1 ? detail.quantity : (detail.quantity || 1) }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ detail.amount }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.userNickname || ('ID: ' + detail.userId) }}</el-descriptions-item>
        <el-descriptions-item label="主接打手">{{ detail.playerName || (detail.playerId ? 'ID: ' + detail.playerId : '未指派') }}</el-descriptions-item>
        <el-descriptions-item label="辅助打手">{{ detail.playerName2 || (detail.playerId2 ? 'ID: ' + detail.playerId2 : '-') }}</el-descriptions-item>
        <el-descriptions-item label="游戏账号">{{ detail.gameAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ detail.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="指派时间">{{ detail.assignTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detail.completeTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="parsedExtraFields(detail).length" label="动态字段" :span="2">
          <div v-for="f in parsedExtraFields(detail)" :key="f.key" style="margin-bottom:4px">
            <span style="color:#909399">{{ f.key }}：</span>{{ f.value }}
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="detail" class="detail-actions">
        <el-button v-if="detail.status === 'COMPLETED'" type="primary" :loading="confirmLoading" @click="handleConfirm(detail)">结单</el-button>
      </div>
      <div v-if="progressList.length" class="progress-section">
        <h4 style="margin:20px 0 12px;color:#303133">订单进度</h4>
        <el-timeline>
          <el-timeline-item v-for="(p, idx) in progressList" :key="idx" :timestamp="p.createdAt" placement="top"
                            :type="idx === progressList.length - 1 ? 'primary' : ''">
            <span>{{ p.content || p.description || orderStatusLabel(p.toStatus) || p.toStatus }}</span>
            <span v-if="p.operatorType" style="margin-left:8px;color:#909399;font-size:12px">({{ { USER: '用户', PLAYER: '打手', CS: '客服', SYSTEM: '系统', ADMIN: '管理员' }[p.operatorType] || p.operatorType }})</span>
            <div v-if="p.images" class="progress-images">
              <el-image
                  v-for="(img, imgIdx) in p.images.split(',')"
                  :key="img + imgIdx"
                  :src="img"
                  :preview-src-list="p.images.split(',')"
                  :initial-index="imgIdx"
                  fit="cover"
                  class="progress-image"
              />
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>
    <!-- 指派弹窗 -->
    <el-dialog v-model="assignVisible" :title="assignMode === 'reassign' ? '重新指派打手' : '指派打手'" width="700px">
      <!-- 已选择的打手显示 -->
      <div style="margin-bottom: 16px; padding: 12px; background: #f5f7fa; border-radius: 8px;">
        <div style="display: flex; gap: 20px; flex-wrap: wrap;">
          <div>
            <span style="color: #909399;">主接打手：</span>
            <span style="color: #303133; font-weight: bold;">
              {{ formatSelectedPlayer(selectedMainPlayerName, selectedMainPlayer) }}
            </span>
          </div>
          <div>
            <span style="color: #909399;">辅助打手：</span>
            <span style="color: #303133; font-weight: bold;">
              {{ formatSelectedPlayer(selectedAssistPlayerName, selectedAssistPlayer) }}
            </span>
          </div>
        </div>
      </div>
      <el-tabs v-model="assignTab">
        <el-tab-pane label="主接打手" name="main">
          <el-form :inline="true" style="margin-bottom:12px">
            <el-form-item><el-input v-model="playerSearch" placeholder="搜索打手昵称/手机号" clearable @keyup.enter="searchPlayers" /></el-form-item>
            <el-form-item><el-button type="primary" @click="searchPlayers">搜索</el-button></el-form-item>
          </el-form>
          <el-table :data="playerList" v-loading="playerLoading" stripe size="small" max-height="300" :row-class-name="playerRowClass">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column label="在线" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isOnline === 1 ? 'success' : 'info'" size="small">{{ row.isOnline === 1 ? '在线' : '离线' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="{ ACTIVE: 'success', PENDING: 'warning', REJECTED: 'danger', FROZEN: 'info' }[row.status] || 'info'" size="small">{{ { PENDING: '待审核', ACTIVE: '正常', REJECTED: '已驳回', FROZEN: '已冻结' }[row.status] || row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="row.isOnline !== 1" @click="selectMainPlayer(row)">选择</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="playerTotal > playerPageSize"
            v-model:current-page="playerPageNum"
            :page-size="playerPageSize"
            :total="playerTotal"
            layout="total, prev, pager, next"
            small
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="fetchPlayers"
          />
        </el-tab-pane>
        <el-tab-pane label="辅助打手" name="assist">
          <el-form :inline="true" style="margin-bottom:12px">
            <el-form-item><el-input v-model="playerSearch2" placeholder="搜索打手" clearable @keyup.enter="searchPlayers2" /></el-form-item>
            <el-form-item><el-button type="primary" @click="searchPlayers2">搜索</el-button></el-form-item>
          </el-form>
          <el-table :data="playerList2" v-loading="playerLoading2" stripe size="small" max-height="300" :row-class-name="playerRowClass">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column label="在线" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isOnline === 1 ? 'success' : 'info'" size="small">{{ row.isOnline === 1 ? '在线' : '离线' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="{ ACTIVE: 'success', PENDING: 'warning', REJECTED: 'danger', FROZEN: 'info' }[row.status] || 'info'" size="small">{{ { PENDING: '待审核', ACTIVE: '正常', REJECTED: '已驳回', FROZEN: '已冻结' }[row.status] || row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="row.isOnline !== 1" @click="selectAssistPlayer(row)">选择</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="playerTotal2 > playerPageSize"
            v-model:current-page="playerPageNum2"
            :page-size="playerPageSize"
            :total="playerTotal2"
            layout="total, prev, pager, next"
            small
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="fetchPlayers2"
          />
        </el-tab-pane>
      </el-tabs>
      <div style="margin-top: 16px; text-align: center;">
        <el-button type="primary" :disabled="!selectedMainPlayer" @click="submitAssign">确认指派</el-button>
        <el-button @click="assignVisible = false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminOrderList, adminOrderDetail, adminOrderAssign, adminOrderRefund, adminOrderConfirm, adminOrderProgress, playerAssignList, csOrderList, csOrderDetail, csOrderAssign, csOrderRefund, csOrderConfirm, csOrderProgress, adminUserList, csUserList } from '@/api/business'
import { getProductList } from '@/api/product'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'

// 兼容后端大写和前端小写枚举
const orderStatusMap = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ASSIGNED: '已指派', ACCEPTED: '已接单',
  WAITING_TEAMMATE: '组队中', IN_PROGRESS: '进行中', COMPLETED: '待确认',
  CONFIRMED: '已完成', REVIEWED: '已评价', CANCELLED: '已取消',
  REFUNDING: '退款中', REFUNDED: '已退款', DISPUTED: '争议中', ARBITRATED: '已仲裁'
}
const orderTagTypeMap = {
  PENDING_PAYMENT: 'info', PAID: '', ASSIGNED: '', ACCEPTED: '',
  WAITING_TEAMMATE: 'warning', IN_PROGRESS: 'warning', COMPLETED: 'success',
  CONFIRMED: 'success', REVIEWED: 'success', CANCELLED: 'info',
  REFUNDING: 'danger', REFUNDED: 'info', DISPUTED: 'danger', ARBITRATED: 'info'
}
function orderStatusLabel(s) { return orderStatusMap[s] || s }
function orderTagType(s) { return orderTagTypeMap[s] || '' }
function parsedExtraFields(order) {
  if (!order?.extraFields) return []
  try {
    const obj = typeof order.extraFields === 'string' ? JSON.parse(order.extraFields) : order.extraFields
    return Object.entries(obj).map(([key, value]) => ({ key, value }))
  } catch { return [] }
}

const loading = ref(false), list = ref([]), total = ref(0)
const dateRange = ref(null)
const activeQuick = ref('')
const userOptions = ref([]), userSearchLoading = ref(false)
const playerFilterOptions = ref([]), playerFilterLoading = ref(false)
const productOptions = ref([]), productSearchLoading = ref(false)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  orderNo: '',
  status: '',
  statusIn: '',
  userId: null,
  playerId: null,
  productId: null,
  createdAtStart: '',
  createdAtEnd: '',
  unassigned: false,
})

function formatUserLabel(u) {
  const phone = u.phone ? ` (${u.phone})` : ''
  return `${u.nickname || '用户'}${phone} / ID: ${u.id}`
}

function formatPlayerFilterLabel(p) {
  const name = p.nickname || p.realName || `ID: ${p.id}`
  return `${name} (ID: ${p.id})`
}

function formatProductLabel(p) {
  const offShelf = p.status !== 1 ? ' (已下架)' : ''
  return `${p.name} (ID: ${p.id})${offShelf}`
}

function onDateRangeChange(val) {
  if (!val || val.length !== 2) {
    query.createdAtStart = ''
    query.createdAtEnd = ''
    return
  }
  query.createdAtStart = val[0]
  query.createdAtEnd = val[1]
}

function clearQuickFilters() {
  activeQuick.value = ''
  query.statusIn = ''
  query.unassigned = false
}

function onStatusChange() {
  clearQuickFilters()
}

async function searchUsers(keyword) {
  if (!keyword) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const fn = isAdmin ? adminUserList : csUserList
    const res = await fn({ pageNum: 1, pageSize: 20, keyword })
    userOptions.value = res.data?.records || []
  } finally {
    userSearchLoading.value = false
  }
}

async function searchPlayersForFilter(keyword) {
  if (!keyword) {
    playerFilterOptions.value = []
    return
  }
  playerFilterLoading.value = true
  try {
    const res = await playerAssignList({ pageNum: 1, pageSize: 20, keyword })
    const page = res.data?.players || {}
    playerFilterOptions.value = page.records || []
  } finally {
    playerFilterLoading.value = false
  }
}

async function searchProducts(keyword) {
  if (!keyword) {
    productOptions.value = []
    return
  }
  productSearchLoading.value = true
  try {
    const res = await getProductList({ pageNum: 1, pageSize: 20, keyword })
    productOptions.value = res.data?.records || []
  } finally {
    productSearchLoading.value = false
  }
}

function buildOrderParams() {
  const params = { pageNum: query.pageNum, pageSize: query.pageSize }
  if (query.orderNo) params.orderNo = query.orderNo
  if (query.status) params.status = query.status
  else if (query.statusIn) params.statusIn = query.statusIn
  if (query.userId) params.userId = query.userId
  if (query.playerId) params.playerId = query.playerId
  if (query.productId) params.productId = query.productId
  if (query.createdAtStart) params.createdAtStart = query.createdAtStart
  if (query.createdAtEnd) params.createdAtEnd = query.createdAtEnd
  if (query.unassigned) params.unassigned = true
  return params
}

function handleSearch() {
  query.pageNum = 1
  fetchData()
}

function resetQuery() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    orderNo: '',
    status: '',
    statusIn: '',
    userId: null,
    playerId: null,
    productId: null,
    createdAtStart: '',
    createdAtEnd: '',
    unassigned: false,
  })
  dateRange.value = null
  activeQuick.value = ''
  userOptions.value = []
  playerFilterOptions.value = []
  productOptions.value = []
  fetchData()
}

function applyQuick(type) {
  if (activeQuick.value === type) {
    resetQuery()
    return
  }
  activeQuick.value = type
  query.pageNum = 1
  query.orderNo = ''
  query.status = ''
  query.statusIn = ''
  query.unassigned = false
  query.userId = null
  query.playerId = null
  query.productId = null
  query.createdAtStart = ''
  query.createdAtEnd = ''
  dateRange.value = null
  userOptions.value = []
  playerFilterOptions.value = []
  productOptions.value = []

  if (type === 'unassigned') {
    query.unassigned = true
  } else if (type === 'inProgress') {
    query.statusIn = 'ASSIGNED,ACCEPTED,WAITING_TEAMMATE,IN_PROGRESS'
  } else if (type === 'afterSale') {
    query.statusIn = 'REFUNDING,REFUNDED,DISPUTED,ARBITRATED'
  }
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    const fn = isAdmin ? adminOrderList : csOrderList
    const res = await fn(buildOrderParams())
    list.value = res.data.records; total.value = Number(res.data.total)
  } finally { loading.value = false }
}

// 详情
const detailVisible = ref(false), detail = ref(null), detailLoading = ref(false), progressList = ref([])
const confirmLoading = ref(false)
async function showDetail(id) {
  detailVisible.value = true; detailLoading.value = true; detail.value = null; progressList.value = []
  try {
    const detailFn = isAdmin ? adminOrderDetail : csOrderDetail
    const progressFn = isAdmin ? adminOrderProgress : csOrderProgress
    const [detailRes, progressRes] = await Promise.all([detailFn(id), progressFn(id)])
    detail.value = detailRes.data
    progressList.value = progressRes.data || []
  } finally { detailLoading.value = false }
}
async function handleConfirm(row) {
  await ElMessageBox.confirm('确定手动结单？订单将变为已确认状态。', '结单确认', { type: 'warning' })
  confirmLoading.value = true
  try {
    const fn = isAdmin ? adminOrderConfirm : csOrderConfirm
    await fn(row.id)
    ElMessage.success('结单成功')
    await showDetail(row.id)
    fetchData()
  } finally {
    confirmLoading.value = false
  }
}

// 指派
const assignVisible = ref(false), assignOrderId = ref(null), assignMode = ref('assign')
const assignTab = ref('main')
const playerSearch = ref(''), playerList = ref([]), playerLoading = ref(false)
const playerPageNum = ref(1), playerTotal = ref(0)
const playerSearch2 = ref(''), playerList2 = ref([]), playerLoading2 = ref(false)
const playerPageNum2 = ref(1), playerTotal2 = ref(0)
const playerPageSize = 20
const selectedMainPlayer = ref(null)
const selectedMainPlayerName = ref('')
const selectedAssistPlayer = ref(null)
const selectedAssistPlayerName = ref('')

function canAssign(row) {
  return (row.status === 'PAID' && !row.playerId) || row.status === 'ASSIGNED'
}

/** 已选打手展示：优先昵称，无昵称时再显示 ID */
function formatSelectedPlayer(name, id) {
  if (name) return name
  if (id) return `ID: ${id}`
  return '未选择'
}

function resolvePlayerName(player) {
  return player.nickname || player.realName || ''
}

function openAssignDialog(row) {
  assignOrderId.value = row.id
  assignMode.value = row.status === 'ASSIGNED' ? 'reassign' : 'assign'
  selectedMainPlayer.value = row.playerId || null
  selectedMainPlayerName.value = row.playerName || ''
  selectedAssistPlayer.value = row.playerId2 || null
  selectedAssistPlayerName.value = row.playerName2 || ''
  playerSearch.value = ''
  playerSearch2.value = ''
  playerList.value = []
  playerList2.value = []
  playerPageNum.value = 1
  playerPageNum2.value = 1
  playerTotal.value = 0
  playerTotal2.value = 0
  assignTab.value = 'main'
  assignVisible.value = true
  fetchPlayers()
  fetchPlayers2()
}

function searchPlayers() {
  playerPageNum.value = 1
  fetchPlayers()
}

function searchPlayers2() {
  playerPageNum2.value = 1
  fetchPlayers2()
}

async function fetchPlayers() {
  playerLoading.value = true
  try {
    const res = await playerAssignList({
      pageNum: playerPageNum.value,
      pageSize: playerPageSize,
      keyword: playerSearch.value || undefined,
    })
    const page = res.data?.players || {}
    playerList.value = page.records || []
    playerTotal.value = Number(page.total || 0)
    syncSelectedNameFromList('main')
  } finally { playerLoading.value = false }
}

async function fetchPlayers2() {
  playerLoading2.value = true
  try {
    const res = await playerAssignList({
      pageNum: playerPageNum2.value,
      pageSize: playerPageSize,
      keyword: playerSearch2.value || undefined,
    })
    const page = res.data?.players || {}
    playerList2.value = page.records || []
    playerTotal2.value = Number(page.total || 0)
    syncSelectedNameFromList('assist')
  } finally { playerLoading2.value = false }
}

/** 列表加载后补全昵称（订单行未带昵称时的兜底） */
function syncSelectedNameFromList(type) {
  if (type === 'main' && selectedMainPlayer.value && !selectedMainPlayerName.value) {
    const found = playerList.value.find(p => p.id === selectedMainPlayer.value)
    if (found) selectedMainPlayerName.value = resolvePlayerName(found)
  }
  if (type === 'assist' && selectedAssistPlayer.value && !selectedAssistPlayerName.value) {
    const found = playerList2.value.find(p => p.id === selectedAssistPlayer.value)
    if (found) selectedAssistPlayerName.value = resolvePlayerName(found)
  }
}

function playerRowClass({ row }) {
  return row.isOnline !== 1 ? 'offline-row' : ''
}

function selectMainPlayer(player) {
  selectedMainPlayer.value = player.id
  selectedMainPlayerName.value = resolvePlayerName(player)
  assignTab.value = 'assist'
  ElMessage.success(`已选择主接打手：${formatSelectedPlayer(selectedMainPlayerName.value, selectedMainPlayer.value)}，请继续选择辅助打手`)
}

function selectAssistPlayer(player) {
  selectedAssistPlayer.value = player.id
  selectedAssistPlayerName.value = resolvePlayerName(player)
  ElMessage.success(`已选择辅助打手：${formatSelectedPlayer(selectedAssistPlayerName.value, selectedAssistPlayer.value)}`)
}

async function submitAssign() {
  if (!selectedMainPlayer.value) {
    ElMessage.warning('请选择主接打手')
    return
  }
  const msg = `确认指派？\n主接打手：${formatSelectedPlayer(selectedMainPlayerName.value, selectedMainPlayer.value)}\n辅助打手：${selectedAssistPlayer.value ? formatSelectedPlayer(selectedAssistPlayerName.value, selectedAssistPlayer.value) : '无'}`
  await ElMessageBox.confirm(msg, '指派确认', { type: 'info' })
  const assignFn = isAdmin ? adminOrderAssign : csOrderAssign
  await assignFn(assignOrderId.value, selectedMainPlayer.value, selectedAssistPlayer.value)
  ElMessage.success(assignMode.value === 'reassign' ? '重新指派成功' : '指派成功')
  assignVisible.value = false
  fetchData()
}

async function handleRefund(row) {
  await ElMessageBox.confirm('确定为该订单退款？订单将被取消并退回支付金额。', '退款确认', { type: 'warning' })
  const fn = isAdmin ? adminOrderRefund : csOrderRefund
  await fn(row.id)
  ElMessage.success('退款已提交')
  fetchData()
}

onMounted(fetchData)
</script>
<style scoped>
.search-form { margin-bottom: 12px; }
.quick-filters { margin-bottom: 16px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.quick-label { color: #909399; font-size: 13px; }
:deep(.offline-row) { opacity: 0.5; }
.detail-actions { margin-top: 16px; text-align: right; }
.progress-images { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.progress-image { width: 72px; height: 72px; border-radius: 6px; overflow: hidden; }
</style>