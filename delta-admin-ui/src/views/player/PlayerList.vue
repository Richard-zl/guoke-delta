<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>打手管理</span>
          <div class="refresh-area">
            <span v-if="lastUpdatedAt" class="updated-time">{{ formatUpdateTime(lastUpdatedAt) }} 更新</span>
            <el-button link type="primary" @click="fetchData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item><el-input v-model="query.keyword" placeholder="搜索打手" clearable @keyup.enter="fetchData" /></el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable @change="onStatusFilter">
            <el-option label="待审核" value="PENDING" /><el-option label="正常" value="ACTIVE" />
            <el-option label="已驳回" value="REJECTED" /><el-option label="已冻结" value="FROZEN" />
            <el-option label="已离职" value="RESIGNED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.workStatus" placeholder="工作状态">
            <el-option v-for="item in WORK_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe :row-class-name="playerRowClass">
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
        <el-table-column label="工作状态" width="120">
          <template #default="{ row }">
            <el-tag :type="workStatusMeta(row.workStatus).type" size="small">{{ workStatusMeta(row.workStatus).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订单占用" min-width="170">
          <template #default="{ row }">{{ formatActiveOrders(row) }}</template>
        </el-table-column>
        <el-table-column label="在线状态" min-width="165">
          <template #default="{ row }">
            <el-tag :type="row.isOnline === 1 ? 'success' : 'info'" size="small">{{ row.isOnline === 1 ? '在线' : '离线' }}</el-tag>
            <div class="last-online">最后在线：{{ row.lastOnlineAt || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="150">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            <div v-if="row.status === 'FROZEN' && row.frozenUntil" style="font-size:12px;color:#999;margin-top:2px">解冻时间：{{ row.frozenUntil }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" />
        <el-table-column label="操作" width="520" fixed="right">
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
            <template v-if="isAdmin">
              <el-button v-if="row.status === 'ACTIVE' || row.status === 'FROZEN'" link type="primary" @click="openReferral(row)">转介绍</el-button>
              <el-button link type="warning" @click="openFundDialog(row)">资金调整</el-button>
              <el-button v-if="row.status === 'ACTIVE' || row.status === 'FROZEN'" link type="danger" @click="handleResign(row)">离职</el-button>
              <el-button v-if="row.status === 'RESIGNED'" link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
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
          <el-descriptions-item label="可提现余额">¥{{ formatMoney(detailData.balance) }}</el-descriptions-item>
          <el-descriptions-item label="待入账">¥{{ formatMoney(detailData.pendingBalance) }}</el-descriptions-item>
          <el-descriptions-item label="提现冻结">¥{{ formatMoney(detailData.frozenAmount) }}</el-descriptions-item>
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
    <!-- 资金调整 -->
    <el-dialog v-model="fundVisible" title="打手资金调整" width="640px" destroy-on-close>
      <p class="fund-subtitle">对打手可提现余额补款或扣款。操作会写入资金流水和操作日志。</p>
      <div class="fund-modes">
        <button type="button" class="fund-mode" :class="{ active: fundForm.direction === 'ADD' }" @click="fundForm.direction = 'ADD'">
          <b>补款</b>
          <span>增加可提现余额</span>
        </button>
        <button type="button" class="fund-mode deduct" :class="{ active: fundForm.direction === 'DEDUCT' }" @click="fundForm.direction = 'DEDUCT'">
          <b>扣款</b>
          <span>扣除可提现余额</span>
        </button>
      </div>
      <el-form label-width="96px">
        <el-form-item label="目标打手" required>
          <el-select
            v-model="fundForm.playerId"
            filterable
            remote
            :remote-method="searchFundPlayers"
            :loading="fundPlayerLoading"
            placeholder="搜索昵称 / 手机号 / 打手ID"
            style="width:100%"
            @change="loadFundWallet"
          >
            <el-option v-for="p in fundPlayerOptions" :key="p.id" :value="p.id" :label="fundPlayerLabel(p)" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整金额" required>
          <el-input-number v-model="fundForm.amount" :min="0.01" :precision="2" :step="1" style="width:220px" />
        </el-form-item>
        <div class="fund-snapshot">
          当前余额 ¥{{ formatMoney(fundWallet.balance) }}
          · 待入账 ¥{{ formatMoney(fundWallet.pendingBalance) }}
          · 冻结 ¥{{ formatMoney(fundWallet.frozenAmount) }}
          · 押金 {{ fundWallet.depositPaymentNo ? '已缴纳' : '未缴纳' }}
        </div>
        <el-form-item label="关联订单">
          <el-input v-model="fundForm.orderNo" placeholder="选填，输入本店订单号后核对" clearable @blur="lookupFundOrder" />
          <div v-if="fundOrderHint" class="fund-hint" :class="{ error: !fundOrderOk }">{{ fundOrderHint }}</div>
        </el-form-item>
        <el-form-item label="原因" required>
          <div class="fund-tags">
            <el-button v-for="tag in fundTags" :key="tag" size="small" @click="appendFundTag(tag)">{{ tag }}</el-button>
          </div>
          <el-input v-model="fundForm.remark" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请输入本次调整的原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="fund-foot-note">操作记录保存在资金流水和操作日志，可随时追溯</span>
        <el-button @click="fundVisible = false">取消</el-button>
        <el-button :type="fundForm.direction === 'DEDUCT' ? 'danger' : 'primary'" :loading="fundSaving" @click="submitFund">
          {{ fundForm.direction === 'DEDUCT' ? '确认扣款' : '确认补款' }}
        </el-button>
      </template>
    </el-dialog>
    <!-- 转介绍客户 -->
    <el-dialog v-model="referralVisible" :title="(referralPlayer?.nickname || '') + ' 的转介绍客户'" width="760px" destroy-on-close>
      <p class="fund-subtitle">绑定已注册客户。一个客户同时只能属于一个介绍打手。比例只影响之后确认的订单。</p>
      <div class="referral-search">
        <el-input v-model="referralKeyword" placeholder="用户ID / 手机号 / 昵称" clearable @keyup.enter="searchReferralUsers" />
        <el-button :loading="referralSearching" @click="searchReferralUsers">查找</el-button>
      </div>
      <el-table v-if="referralCandidates.length" :data="referralCandidates" size="small" highlight-current-row class="referral-candidates" @current-change="onPickReferralUser">
        <el-table-column width="48">
          <template #default="{ row }">
            <span>{{ referralPick?.id === row.id ? '✓' : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="用户ID" width="100" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="phone" label="手机号" min-width="140" />
      </el-table>
      <div v-if="referralPick" class="fund-hint">已选择：{{ referralPick.nickname || '未命名' }}（ID:{{ referralPick.id }}，{{ referralPick.phone || '无手机号' }}）</div>
      <div class="referral-add">
        <span>介绍比例</span>
        <el-input-number v-model="referralRate" :min="0.01" :max="100" :precision="2" :step="0.5" />
        <span>%</span>
        <el-button type="primary" :loading="referralSaving" @click="submitReferral">新增</el-button>
      </div>
      <el-table :data="referralList" v-loading="referralLoading" size="small">
        <el-table-column prop="nickname" label="客户" min-width="120" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="比例" width="90">
          <template #default="{ row }">{{ row.ratePercent }}%</template>
        </el-table-column>
        <el-table-column prop="boundAt" label="绑定时间" min-width="170" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="editReferralRate(row)">改比例</el-button>
            <el-button link type="danger" @click="unbindReferral(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
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
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { adminPlayerList, adminPlayerDetail, adminPlayerTransactions, adminPlayerApprove, adminPlayerReject, adminPlayerUpdateStatus, adminPlayerFreeze, adminPlayerUnfreeze, adminPlayerUpdate, adminPlayerAdjustBalance, adminPlayerResign, adminPlayerDelete, adminReferralSearch, adminPlayerReferrals, adminPlayerBindReferral, adminPlayerUpdateReferral, adminPlayerUnbindReferral, csPlayerList, csPlayerDetail, csPlayerTransactions, csPlayerAudit, csPlayerFreeze, csPlayerUpdateNickname, adminOrderList, csOrderList } from '@/api/business'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import { WORK_STATUS_OPTIONS, workStatusMeta, formatActiveOrders, isPlayerRowDimmed } from '@/utils/playerWorkStatus'
const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '', workStatus: 'AVAILABLE' })
const lastUpdatedAt = ref(null)
const POLL_INTERVAL_MS = 30_000
let pollTimer = null
const statusMap = { PENDING: '待审核', ACTIVE: '正常', REJECTED: '已驳回', FROZEN: '已冻结', RESIGNED: '已离职' }
const statusTagMap = { PENDING: 'warning', ACTIVE: 'success', REJECTED: 'danger', FROZEN: 'info', RESIGNED: 'info' }
function formatMoney(amount) {
  const value = Number(amount || 0)
  return value.toFixed(2)
}
function onStatusFilter(status) {
  if (status === 'RESIGNED') query.workStatus = ''
}
function statusText(s) { return statusMap[s] || s }
function statusTagType(s) { return statusTagMap[s] || '' }
async function fetchData({ silent = false } = {}) {
  if (!silent) loading.value = true
  try {
    const fn = isAdmin ? adminPlayerList : csPlayerList
    const res = await fn(query)
    list.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
    lastUpdatedAt.value = new Date()
  } catch {
    // 请求层已统一提示错误，保留上一次成功数据。
  } finally {
    if (!silent) loading.value = false
  }
}
function resetQuery() {
  Object.assign(query, { pageNum: 1, pageSize: 10, keyword: '', status: '', workStatus: 'AVAILABLE' })
  fetchData()
}
function formatUpdateTime(date) {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
function playerRowClass({ row }) {
  return isPlayerRowDimmed(row) ? 'dimmed-row' : ''
}
function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => fetchData({ silent: true }), POLL_INTERVAL_MS)
}
function stopPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
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
  ADMIN_RECHARGE: '管理员补款',
  ADMIN_DEDUCT: '管理员扣款',
  INCOME: '收益入账',
  REFERRAL: '转介绍分成',
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

const fundVisible = ref(false)
const fundSaving = ref(false)
const fundPlayerLoading = ref(false)
const fundPlayerOptions = ref([])
const fundTags = ['订单补款', '奖励补款', '违规扣款', '售后扣款']
const fundForm = reactive({ playerId: null, direction: 'ADD', amount: null, orderNo: '', remark: '' })
const fundWallet = reactive({ balance: 0, pendingBalance: 0, frozenAmount: 0, depositPaymentNo: '' })
const fundOrderHint = ref('')
const fundOrderOk = ref(true)
let fundSearchTimer = null

function fundPlayerLabel(player) {
  return `${player.nickname || '未命名'} / ${player.phone || '-'} / ID:${player.id}`
}
function openFundDialog(row) {
  fundForm.playerId = row.id
  fundForm.direction = 'ADD'
  fundForm.amount = null
  fundForm.orderNo = ''
  fundForm.remark = ''
  fundOrderHint.value = ''
  fundOrderOk.value = true
  fundPlayerOptions.value = [row]
  fundVisible.value = true
  loadFundWallet(row.id)
}
function searchFundPlayers(keyword) {
  if (!keyword) return
  clearTimeout(fundSearchTimer)
  fundSearchTimer = setTimeout(async () => {
    fundPlayerLoading.value = true
    try {
      const res = await adminPlayerList({ keyword, pageNum: 1, pageSize: 20, workStatus: '' })
      const records = res.data?.records || []
      const current = fundPlayerOptions.value.find(item => item.id === fundForm.playerId)
      fundPlayerOptions.value = current && !records.some(item => item.id === current.id)
        ? [current, ...records]
        : records
    } finally {
      fundPlayerLoading.value = false
    }
  }, 300)
}
async function loadFundWallet(playerId) {
  if (!playerId) return
  const res = await adminPlayerDetail(playerId)
  const data = res.data || {}
  fundWallet.balance = data.balance || 0
  fundWallet.pendingBalance = data.pendingBalance || 0
  fundWallet.frozenAmount = data.frozenAmount || 0
  fundWallet.depositPaymentNo = data.depositPaymentNo || ''
}
function appendFundTag(tag) {
  if (fundForm.remark.includes(tag)) return
  fundForm.remark = fundForm.remark ? `${fundForm.remark} ${tag}` : tag
}
async function lookupFundOrder() {
  const orderNo = fundForm.orderNo.trim()
  if (!orderNo) {
    fundOrderHint.value = ''
    fundOrderOk.value = true
    return
  }
  const res = await adminOrderList({ orderNo, pageNum: 1, pageSize: 5 })
  const exact = (res.data?.records || []).find(item => item.orderNo === orderNo)
  fundOrderOk.value = !!exact
  fundOrderHint.value = exact
    ? `${exact.productName || '订单'} · ¥${exact.amount}`
    : '未找到该订单'
}
async function submitFund() {
  if (!fundForm.playerId) return ElMessage.warning('请选择打手')
  if (!fundForm.amount || fundForm.amount <= 0) return ElMessage.warning('请填写调整金额')
  if (!fundForm.remark.trim()) return ElMessage.warning('请填写调整原因')
  if (fundForm.orderNo.trim() && !fundOrderOk.value) return ElMessage.warning('关联订单不存在')
  const action = fundForm.direction === 'DEDUCT' ? '扣款' : '补款'
  try {
    await ElMessageBox.confirm(`确认${action} ¥${Number(fundForm.amount).toFixed(2)}？`, '资金调整', { type: 'warning' })
  } catch {
    return
  }
  fundSaving.value = true
  try {
    await adminPlayerAdjustBalance(fundForm.playerId, {
      direction: fundForm.direction,
      amount: fundForm.amount,
      remark: fundForm.remark.trim(),
      orderNo: fundForm.orderNo.trim() || undefined
    })
    ElMessage.success(`${action}成功`)
    fundVisible.value = false
    fetchData()
    if (detailVisible.value && detailData.value?.id === fundForm.playerId) showDetail(fundForm.playerId)
  } finally {
    fundSaving.value = false
  }
}

const referralVisible = ref(false)
const referralPlayer = ref(null)
const referralKeyword = ref('')
const referralCandidates = ref([])
const referralPick = ref(null)
const referralRate = ref(5)
const referralList = ref([])
const referralLoading = ref(false)
const referralSearching = ref(false)
const referralSaving = ref(false)

function openReferral(row) {
  referralPlayer.value = row
  referralKeyword.value = ''
  referralCandidates.value = []
  referralPick.value = null
  referralRate.value = 5
  referralVisible.value = true
  loadReferrals()
}
async function loadReferrals() {
  if (!referralPlayer.value) return
  referralLoading.value = true
  try {
    const res = await adminPlayerReferrals(referralPlayer.value.id)
    referralList.value = res.data || []
  } finally {
    referralLoading.value = false
  }
}
async function searchReferralUsers() {
  const keyword = referralKeyword.value.trim()
  if (!keyword) return ElMessage.warning('请输入用户ID、手机号或昵称')
  referralSearching.value = true
  try {
    const res = await adminReferralSearch(keyword)
    referralCandidates.value = res.data || []
    referralPick.value = referralCandidates.value.length === 1 ? referralCandidates.value[0] : null
    if (!referralCandidates.value.length) ElMessage.warning('未找到客户')
  } finally {
    referralSearching.value = false
  }
}
function onPickReferralUser(row) {
  referralPick.value = row
}
async function submitReferral() {
  if (!referralPick.value) return ElMessage.warning('请先查找并选择客户')
  if (!referralRate.value || referralRate.value <= 0) return ElMessage.warning('请填写介绍比例')
  referralSaving.value = true
  try {
    await adminPlayerBindReferral(referralPlayer.value.id, {
      userId: referralPick.value.id,
      ratePercent: referralRate.value
    })
    ElMessage.success('已绑定')
    referralPick.value = null
    referralCandidates.value = []
    referralKeyword.value = ''
    loadReferrals()
  } finally {
    referralSaving.value = false
  }
}
async function editReferralRate(row) {
  try {
    const { value } = await ElMessageBox.prompt('介绍比例（%）', '修改比例', {
      inputValue: String(row.ratePercent),
      inputPattern: /^(100(\.0{1,2})?|([1-9]\d?|0)(\.\d{1,2})?)$/,
      inputErrorMessage: '请输入大于0且不超过100的比例，最多两位小数'
    })
    const rate = Number(value)
    if (!(rate > 0)) return ElMessage.warning('介绍比例需大于0')
    await adminPlayerUpdateReferral(referralPlayer.value.id, row.id, { ratePercent: rate })
    ElMessage.success('比例已更新')
    loadReferrals()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
  }
}
async function unbindReferral(row) {
  try {
    await ElMessageBox.confirm(`确认解绑 ${row.nickname || '该客户'}？已确认订单的介绍费不变。`, '解绑转介绍', { type: 'warning' })
  } catch {
    return
  }
  await adminPlayerUnbindReferral(referralPlayer.value.id, row.id)
  ElMessage.success('已解绑')
  loadReferrals()
}
async function handleResign(row) {
  try {
    await ElMessageBox.confirm(`确认让「${row.nickname || row.id}」离职？离职后不能接单、被指派和提现。`, '打手离职', { type: 'warning' })
  } catch {
    return
  }
  await adminPlayerResign(row.id)
  ElMessage.success('已离职。筛选「已离职」后可以删除')
  fetchData()
}
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.nickname || row.id}」？仅已离职且订单、余额、提现都结清的打手可以删除。历史订单和资金流水会保留。`, '删除打手', { type: 'warning' })
  } catch {
    return
  }
  await adminPlayerDelete(row.id)
  ElMessage.success('已删除')
  fetchData()
}

onMounted(() => {
  fetchData()
  startPolling()
})
onBeforeUnmount(stopPolling)
</script>
<style scoped>
.search-form { margin-bottom: 16px; }
.card-header, .refresh-area { display: flex; align-items: center; justify-content: space-between; }
.refresh-area { gap: 10px; }
.updated-time, .last-online { color: #909399; font-size: 12px; }
.last-online { margin-top: 4px; }
:deep(.dimmed-row) { opacity: 0.5; }
</style>

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
.fund-subtitle { margin: 0 0 16px; color: #909399; font-size: 13px; }
.fund-modes { display: flex; gap: 12px; margin-bottom: 18px; }
.fund-mode {
  flex: 1; text-align: left; border: 1px solid #dcdfe6; background: #fff; border-radius: 8px; padding: 12px 14px; cursor: pointer;
}
.fund-mode b { display: block; font-size: 15px; color: #303133; }
.fund-mode span { display: block; margin-top: 4px; color: #909399; font-size: 12px; }
.fund-mode.active { border-color: #409eff; background: #ecf5ff; }
.fund-mode.deduct.active { border-color: #f56c6c; background: #fef0f0; }
.fund-snapshot { margin: 0 0 16px 96px; padding: 10px 12px; background: #f4f8ff; border-radius: 8px; color: #606266; font-size: 13px; }
.fund-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.fund-hint { margin-top: 6px; font-size: 12px; color: #67c23a; }
.fund-hint.error { color: #f56c6c; }
.fund-foot-note { float: left; color: #909399; font-size: 12px; line-height: 32px; }
.referral-search, .referral-add { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.referral-search .el-input { width: 280px; }
.referral-candidates { margin-bottom: 8px; }
</style>
