<template>
  <div class="page-container">
    <el-card>
      <template #header><span>打手管理</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item><el-input v-model="query.keyword" placeholder="搜索打手" clearable @keyup.enter="fetchData" /></el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="待审核" value="PENDING" /><el-option label="正常" value="ACTIVE" />
            <el-option label="已驳回" value="REJECTED" /><el-option label="已冻结" value="FROZEN" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button><el-button @click="query.keyword='';query.status='';fetchData()">重置</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="头像" width="80">
          <template #default="{ row }">
            <el-avatar :src="row.avatar" :size="32">{{ (row.nickname || '')[0] || '打' }}</el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="realName" label="真实姓名" width="100" />
        <el-table-column prop="level" label="等级" width="80" />
        <el-table-column prop="completedOrders" label="完成订单" width="100" />
        <el-table-column prop="balance" label="余额" width="100"><template #default="{ row }">¥{{ row.balance || 0 }}</template></el-table-column>
        <el-table-column prop="depositPaymentNo" label="押金订单号" width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.depositPaymentNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="150">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            <div v-if="row.status === 'FROZEN' && row.frozenUntil" style="font-size:12px;color:#999;margin-top:2px">解冻时间：{{ row.frozenUntil }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" />
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row)">{{ isAdmin ? '编辑' : '改名' }}</el-button>
            <el-button link type="info" @click="showPlayerOrders(row)">订单</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" @click="handleApprove(row.id)">通过</el-button>
              <el-button link type="danger" @click="openRejectDialog(row.id)">驳回</el-button>
            </template>
            <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="openFreezeDialog(row)">冻结</el-button>
            <el-popconfirm v-if="row.status === 'FROZEN'" title="确认解冻？" @confirm="handleUnfreeze(row.id)">
              <template #reference><el-button link type="success">解冻</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回原因" width="400px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
      <template #footer><el-button @click="rejectDialogVisible = false">取消</el-button><el-button type="primary" @click="handleReject">确定驳回</el-button></template>
    </el-dialog>
    <!-- 打手详情弹窗（精简版） -->
    <el-dialog v-model="detailVisible" title="打手详情" width="980px" destroy-on-close>
      <div v-loading="detailLoading" class="player-detail">
        <template v-if="detailData">
        <div class="player-detail-header">
          <el-avatar :src="detailData.avatar" :size="56">{{ (detailData.nickname || '')[0] || '打' }}</el-avatar>
          <div class="player-detail-main">
            <div class="player-detail-name">{{ detailData.nickname || ('ID: ' + detailData.id) }}</div>
            <div class="player-detail-sub">
              <span class="player-detail-id">ID: {{ detailData.id }}</span>
              <el-tag :type="statusTagType(detailData.status)" size="small" style="margin-left:8px">
                {{ statusText(detailData.status) }}
              </el-tag>
            </div>
          </div>
        </div>
        <el-descriptions :column="2" border size="small" style="margin-top:16px">
          <el-descriptions-item label="手机号">{{ detailData.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="等级">{{ detailData.level || '-' }}</el-descriptions-item>
          <el-descriptions-item label="余额">¥{{ detailData.balance || 0 }}</el-descriptions-item>
          <el-descriptions-item label="完成订单">{{ detailData.completedOrders || 0 }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ detailData.avgRating || '-' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间" :span="2">{{ detailData.createdAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.depositPaymentNo" label="押金订单号" :span="2">
            {{ detailData.depositPaymentNo }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.rejectReason" label="驳回原因" :span="2">
            {{ detailData.rejectReason }}
          </el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">金额变化详情</el-divider>
        <el-table :data="playerTransactions" v-loading="transactionLoading" stripe size="small" max-height="360">
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column prop="type" label="类型" width="130">
            <template #default="{ row }">{{ transactionTypeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="110">
            <template #default="{ row }">
              <span :class="transactionAmountClass(row.amount)">¥{{ formatTransactionAmount(row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="balanceBefore" label="变动前" width="110">
            <template #default="{ row }">¥{{ formatTransactionAmount(row.balanceBefore) }}</template>
          </el-table-column>
          <el-table-column prop="balanceAfter" label="变动后" width="110">
            <template #default="{ row }">¥{{ formatTransactionAmount(row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
        </el-table>
        <Pagination
          :total="transactionTotal"
          v-model:page="transactionQuery.pageNum"
          v-model:limit="transactionQuery.pageSize"
          @pagination="fetchPlayerTransactions"
        />
        </template>
      </div>
    </el-dialog>
    <!-- 打手编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="isAdmin ? '编辑打手信息' : '修改昵称'" width="400px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" placeholder="请输入昵称" maxlength="20" />
        </el-form-item>
        <template v-if="isAdmin">
          <el-form-item label="真实姓名">
            <el-input v-model="editForm.realName" placeholder="请输入真实姓名" maxlength="20" />
          </el-form-item>
          <el-form-item label="等级">
            <el-input v-model.number="editForm.level" type="number" placeholder="等级" min="0" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEditSave">保存</el-button>
      </template>
    </el-dialog>
    <!-- 打手订单弹窗 -->
    <el-dialog v-model="ordersVisible" :title="ordersPlayerName + ' 的订单'" width="800px" destroy-on-close>
      <el-table :data="playerOrders" v-loading="ordersLoading" stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="90">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderTagType(row.status)" size="small">{{ orderStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" />
      </el-table>
      <Pagination :total="ordersTotal" v-model:page="ordersQuery.pageNum" v-model:limit="ordersQuery.pageSize" @pagination="fetchPlayerOrders" />
    </el-dialog>
    <!-- 冻结弹窗 -->
    <el-dialog v-model="freezeVisible" title="冻结打手" width="450px">
      <p v-if="freezeRow" style="margin-bottom:16px">打手：<b>{{ freezeRow.nickname }}</b>（{{ freezeRow.phone || '-' }}）</p>
      <el-form label-width="100px">
        <el-form-item label="冻结时长">
          <el-select v-model="freezeDuration" placeholder="选择冻结时长" style="width:100%">
            <el-option label="1天" :value="1" />
            <el-option label="3天" :value="3" />
            <el-option label="7天" :value="7" />
            <el-option label="15天" :value="15" />
            <el-option label="30天" :value="30" />
            <el-option label="永久冻结" :value="-1" />
            <el-option label="自定义" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="freezeDuration === 0" label="解冻时间">
          <el-date-picker v-model="freezeCustomDate" type="datetime" placeholder="选择解冻时间" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freezeVisible = false">取消</el-button>
        <el-button type="primary" :loading="freezeSubmitting" @click="handleFreeze">确认冻结</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminPlayerList, adminPlayerDetail, adminPlayerTransactions, adminPlayerApprove, adminPlayerReject, adminPlayerUpdateStatus, adminPlayerFreeze, adminPlayerUnfreeze, adminPlayerUpdate, csPlayerList, csPlayerDetail, csPlayerTransactions, csPlayerAudit, csPlayerFreeze, csPlayerUpdateNickname, adminOrderList, csOrderList } from '@/api/business'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })
const statusMap = { PENDING: '待审核', ACTIVE: '正常', REJECTED: '已驳回', FROZEN: '已冻结' }
const statusTagMap = { PENDING: 'warning', ACTIVE: 'success', REJECTED: 'danger', FROZEN: 'info' }
function statusText(s) { return statusMap[s] || s }
function statusTagType(s) { return statusTagMap[s] || '' }
async function fetchData() {
  loading.value = true
  try {
    const fn = isAdmin ? adminPlayerList : csPlayerList
    const res = await fn(query)
    list.value = res.data.records; total.value = Number(res.data.total)
  } finally { loading.value = false }
}
async function handleApprove(id) {
  if (isAdmin) await adminPlayerApprove(id)
  else await csPlayerAudit(id, 'ACTIVE', '')
  ElMessage.success('已通过'); fetchData()
}
const rejectDialogVisible = ref(false), rejectId = ref(null), rejectReason = ref('')
function openRejectDialog(id) { rejectId.value = id; rejectReason.value = ''; rejectDialogVisible.value = true }
async function handleReject() {
  if (isAdmin) await adminPlayerReject(rejectId.value, rejectReason.value)
  else await csPlayerAudit(rejectId.value, 'REJECTED', rejectReason.value)
  ElMessage.success('已驳回'); rejectDialogVisible.value = false; fetchData()
}
// 冻结弹窗
const freezeVisible = ref(false), freezeRow = ref(null), freezeDuration = ref(7), freezeCustomDate = ref(''), freezeSubmitting = ref(false)
function openFreezeDialog(row) {
  freezeRow.value = row; freezeDuration.value = 7; freezeCustomDate.value = ''; freezeVisible.value = true
}
async function handleFreeze() {
  let frozenUntil = null
  if (freezeDuration.value === -1) {
    frozenUntil = '2099-12-31 23:59:59'
  } else if (freezeDuration.value === 0) {
    if (!freezeCustomDate.value) return ElMessage.warning('请选择解冻时间')
    frozenUntil = freezeCustomDate.value
  } else {
    const d = new Date(); d.setDate(d.getDate() + freezeDuration.value)
    frozenUntil = d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0') + ' ' + String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0') + ':' + String(d.getSeconds()).padStart(2,'0')
  }
  freezeSubmitting.value = true
  try {
    if (isAdmin) await adminPlayerFreeze(freezeRow.value.id, { frozenUntil })
    else await csPlayerFreeze(freezeRow.value.id)
    ElMessage.success('已冻结'); freezeVisible.value = false; fetchData()
  } finally { freezeSubmitting.value = false }
}
async function handleUnfreeze(id) {
  if (isAdmin) await adminPlayerUnfreeze(id)
  else await adminPlayerUpdateStatus(id, 'ACTIVE')
  ElMessage.success('已解冻'); fetchData()
}
const detailVisible = ref(false), detailData = ref(null), detailLoading = ref(false)
const transactionLoading = ref(false)
const playerTransactions = ref([])
const transactionTotal = ref(0)
const transactionQuery = reactive({ pageNum: 1, pageSize: 5, playerId: null })
const transactionTypeMap = {
  ADMIN_RECHARGE: '管理员充值',
  ADMIN_DEDUCT: '管理员扣款',
  INCOME: '收益入账',
  WITHDRAW: '提现申请',
  WITHDRAW_COMPLETE: '提现完成',
  WITHDRAW_REJECT: '提现驳回',
  CONSUMPTION: '订单扣款',
  REFUND: '退款入账'
}
function transactionTypeLabel(type) {
  return transactionTypeMap[type] || type || '-'
}
function formatTransactionAmount(amount) {
  const value = Number(amount || 0)
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}`
}
function transactionAmountClass(amount) {
  return Number(amount || 0) >= 0 ? 'amount-positive' : 'amount-negative'
}
async function fetchPlayerTransactions() {
  if (!transactionQuery.playerId) return
  transactionLoading.value = true
  try {
    const params = { pageNum: transactionQuery.pageNum, pageSize: transactionQuery.pageSize }
    const fn = isAdmin ? adminPlayerTransactions : csPlayerTransactions
    const res = await fn(transactionQuery.playerId, params)
    playerTransactions.value = res.data?.records || []
    transactionTotal.value = Number(res.data?.total || 0)
  } finally {
    transactionLoading.value = false
  }
}
async function showDetail(id) {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  transactionQuery.playerId = id
  transactionQuery.pageNum = 1
  playerTransactions.value = []
  transactionTotal.value = 0
  try {
    const detailFn = isAdmin ? adminPlayerDetail : csPlayerDetail
    await Promise.all([
      detailFn(id).then(res => { detailData.value = res.data }),
      fetchPlayerTransactions()
    ])
  } finally {
    detailLoading.value = false
  }
}

// 编辑打手
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({ id: null, nickname: '', realName: '', level: null })
function openEditDialog(row) {
  editForm.id = row.id
  editForm.nickname = row.nickname || ''
  editForm.realName = row.realName || ''
  editForm.level = row.level ?? null
  editVisible.value = true
}
async function handleEditSave() {
  if (!editForm.id) return
  editSaving.value = true
  try {
    if (isAdmin) {
      await adminPlayerUpdate(editForm.id, {
        nickname: editForm.nickname,
        realName: editForm.realName,
        level: editForm.level
      })
    } else {
      await csPlayerUpdateNickname(editForm.id, editForm.nickname)
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    fetchData()
    if (detailVisible.value && detailData.value?.id === editForm.id) {
      showDetail(editForm.id)
    }
  } finally {
    editSaving.value = false
  }
}

// 打手订单
const orderStatusMap = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ASSIGNED: '已指派', ACCEPTED: '已接单',
  WAITING_TEAMMATE: '组队中', IN_PROGRESS: '进行中', COMPLETED: '待确认',
  CONFIRMED: '已完成', REVIEWED: '已评价', CANCELLED: '已取消',
  REFUNDING: '退款中', REFUNDED: '已退款', DISPUTED: '争议中', ARBITRATED: '已仲裁'
}
const orderTagTypeMap = {
  PENDING_PAYMENT: 'info', PAID: '', ASSIGNED: '', ACCEPTED: '',
  WAITING_TEAMMATE: '', IN_PROGRESS: 'warning', COMPLETED: 'success',
  CONFIRMED: 'success', REVIEWED: 'success', CANCELLED: 'info',
  REFUNDING: 'danger', REFUNDED: 'info', DISPUTED: 'danger', ARBITRATED: 'info'
}
function orderStatusLabel(s) { return orderStatusMap[s] || s }
function orderTagType(s) { return orderTagTypeMap[s] || '' }

const ordersVisible = ref(false), ordersLoading = ref(false)
const playerOrders = ref([]), ordersTotal = ref(0), ordersPlayerName = ref('')
const ordersQuery = reactive({ pageNum: 1, pageSize: 10, playerId: null })

function showPlayerOrders(row) {
  ordersPlayerName.value = row.nickname || ('ID: ' + row.id)
  ordersQuery.playerId = row.id
  ordersQuery.pageNum = 1
  playerOrders.value = []
  ordersVisible.value = true
  fetchPlayerOrders()
}
async function fetchPlayerOrders() {
  ordersLoading.value = true
  try {
    const params = { pageNum: ordersQuery.pageNum, pageSize: ordersQuery.pageSize }
    if (ordersQuery.playerId) params.playerId = ordersQuery.playerId
    const fn = isAdmin ? adminOrderList : csOrderList
    const res = await fn(params)
    playerOrders.value = res.data?.records || []
    ordersTotal.value = Number(res.data?.total || 0)
  } finally { ordersLoading.value = false }
}

onMounted(fetchData)
</script>
<style scoped>.search-form { margin-bottom: 16px; }</style>

<style scoped>
.player-detail {
  padding: 4px 0;
}
.player-detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
}
.player-detail-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.player-detail-name {
  font-size: 16px;
  font-weight: 600;
}
.player-detail-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
}
.player-detail-id {
  font-size: 12px;
}
.amount-positive { color: #f56c6c; font-weight: 600; }
.amount-negative { color: #67c23a; font-weight: 600; }
</style>
