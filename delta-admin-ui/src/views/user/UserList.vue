<template>
  <div class="page-container">
    <el-card>
      <template #header><span>用户管理</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item><el-input v-model="query.keyword" placeholder="用户名/手机号" clearable @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="正常" :value="1" />
            <el-option label="封禁" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.levelCode" placeholder="会员等级" clearable style="min-width: 140px">
            <el-option v-for="item in levelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="注册开始"
            end-placeholder="注册结束"
            value-format="YYYY-MM-DD"
            @change="onDateRangeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-input v-model="query.userId" placeholder="用户ID" clearable style="width: 120px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="avatar" label="头像" width="70"><template #default="{ row }"><el-avatar :src="row.avatar" :size="36" /></template></el-table-column>
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="balance" label="余额" width="100"><template #default="{ row }"><span class="balance-text">¥{{ row.balance || 0 }}</span></template></el-table-column>
        <el-table-column prop="points" label="当前积分" width="100"><template #default="{ row }"><span class="points-text">{{ formatPoints(row.points) }}</span></template></el-table-column>
        <el-table-column prop="totalPoints" label="总积分" width="100"><template #default="{ row }">{{ formatPoints(row.totalPoints) }}</template></el-table-column>
        <el-table-column prop="levelName" label="等级" width="110"><template #default="{ row }"><el-tag size="small" effect="plain">{{ row.levelName || '青铜伴星' }}</el-tag></template></el-table-column>
        <el-table-column label="优惠券" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.availableCouponCount > 0" type="danger" size="small" effect="plain" class="coupon-tag">
              {{ row.availableCouponCount }} 张可用
            </el-tag>
            <span v-else class="coupon-empty">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '正常' : '封禁' }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" />
        <el-table-column label="操作" :width="isAdmin ? 430 : 300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button link type="info" @click="showUserOrders(row)">订单</el-button>
            <el-button link type="warning" @click="openCouponDialog(row)">优惠券</el-button>
            <el-button v-if="isAdmin" link type="warning" @click="openBalanceDialog(row)">余额</el-button>
            <el-button v-if="isAdmin" link type="success" @click="openPointsDialog(row)">积分</el-button>
            <el-popconfirm :title="row.status === 1 ? '确认封禁该用户？' : '确认解封该用户？'" @confirm="handleToggleStatus(row)">
              <template #reference><el-button link :type="row.status === 1 ? 'danger' : 'success'">{{ row.status === 1 ? '封禁' : '解封' }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="550px">
      <el-descriptions :column="2" border v-if="detailData" v-loading="detailLoading">
        <el-descriptions-item label="ID">{{ detailData.user?.id }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detailData.user?.nickname }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailData.user?.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="detailData.user?.status === 1 ? 'success' : 'danger'" size="small">{{ detailData.user?.status === 1 ? '正常' : '封禁' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="余额">¥{{ detailData.wallet?.balance || 0 }}</el-descriptions-item>
        <el-descriptions-item label="当前积分">{{ formatPoints(detailData.user?.points) }}</el-descriptions-item>
        <el-descriptions-item label="总积分">{{ formatPoints(detailData.user?.totalPoints) }}</el-descriptions-item>
        <el-descriptions-item label="会员等级">{{ detailData.user?.levelName || '青铜伴星' }}</el-descriptions-item>
        <el-descriptions-item label="可用优惠券">{{ detailCouponCount }} 张</el-descriptions-item>
        <el-descriptions-item label="等级权益" :span="2">{{ memberBenefitText(detailData.user) }}</el-descriptions-item>
        <el-descriptions-item label="订单数">{{ detailData.orderCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="注册时间" :span="2">{{ detailData.user?.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 用户订单弹窗 -->
    <el-dialog v-model="ordersVisible" :title="ordersUserName ? ordersUserName + ' 的订单' : '用户订单'" width="900px" destroy-on-close>
      <el-table :data="userOrders" v-loading="ordersLoading" stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
        <el-table-column prop="playerName" label="打手" width="120">
          <template #default="{ row }">{{ row.playerName || (row.playerId ? 'ID: ' + row.playerId : '未指派') }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="orderTagType(row.status)" size="small">{{ orderStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" />
      </el-table>
      <Pagination :total="ordersTotal" v-model:page="ordersQuery.pageNum" v-model:limit="ordersQuery.pageSize" @pagination="fetchUserOrders" />
    </el-dialog>

    <!-- 余额调整弹窗 -->
    <el-dialog v-model="balanceVisible" title="余额调整" width="450px" destroy-on-close>
      <div class="balance-info">
        <span>用户：<strong>{{ balanceUser?.nickname }}</strong></span>
        <span>当前余额：<strong class="balance-text">¥{{ balanceUser?.balance || 0 }}</strong></span>
      </div>
      <el-form ref="balanceFormRef" :model="balanceForm" :rules="balanceRules" label-width="80px" style="margin-top: 20px">
        <el-form-item label="操作类型">
          <el-radio-group v-model="balanceForm.type">
            <el-radio-button value="add">充值（增加）</el-radio-button>
            <el-radio-button value="sub">扣款（减少）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="balanceForm.amount" :min="0.01" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="balanceForm.remark" placeholder="操作原因（选填）" />
        </el-form-item>
        <el-alert
          v-if="balanceForm.type === 'add'"
          title="充值成功后将按规则自动赠送积分（金额越高倍率越高），当前积分与总积分同步增加。"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 8px"
        />
      </el-form>
      <template #footer>
        <el-button @click="balanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="balanceSubmitting" @click="handleBalanceSubmit">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 积分调整弹窗：当前积分 / 总积分分账户调整 -->
    <el-dialog v-model="pointsVisible" title="积分管理" width="520px" destroy-on-close>
      <div class="point-info">
        <span>用户：<strong>{{ pointsUser?.nickname }}</strong></span>
        <span>当前积分：<strong class="points-text">{{ formatPoints(pointsUser?.points) }}</strong></span>
        <span>总积分：<strong>{{ formatPoints(pointsUser?.totalPoints) }}</strong></span>
        <span>当前等级：<strong>{{ pointsUser?.levelName || '青铜伴星' }}</strong></span>
      </div>
      <div v-if="pointsUser" class="benefit-tip">当前权益：{{ memberBenefitText(pointsUser) }}</div>
      <el-alert
        title="当前积分可增减（如线下兑换）；总积分用于定级，调整后会重算会员等级。储值充值会自动按规则赠送双账户积分。"
        type="warning"
        :closable="false"
        show-icon
        class="points-alert"
      />
      <el-form ref="pointsFormRef" :model="pointsForm" :rules="pointsRules" label-width="100px" style="margin-top: 20px">
        <el-form-item label="调整账户">
          <el-radio-group v-model="pointsForm.account">
            <el-radio-button value="current">当前积分</el-radio-button>
            <el-radio-button value="total">总积分</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-radio-group v-model="pointsForm.type">
            <el-radio-button value="add">增加</el-radio-button>
            <el-radio-button value="sub">减少</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="积分数" prop="points">
          <!-- 任意正整数均可（如 315），按钮步进仅作快捷加减 -->
          <el-input-number v-model="pointsForm.points" :min="1" :precision="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="pointsForm.remark" placeholder="操作原因（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pointsVisible = false">取消</el-button>
        <el-button type="primary" :loading="pointsSubmitting" @click="handlePointsSubmit">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 优惠券管理弹窗 -->
    <el-dialog
      v-model="couponVisible"
      :title="couponUser ? couponUser.nickname + ' 的优惠券' : '优惠券管理'"
      width="900px"
      destroy-on-close
    >
      <div class="coupon-header" v-if="couponUser">
        <span>用户：<strong>{{ couponUser.nickname }}</strong>（ID: {{ couponUser.id }}）</span>
        <span>可用：<strong class="balance-text">{{ couponUser.availableCouponCount || 0 }} 张</strong></span>
      </div>
      <div class="coupon-toolbar">
        <el-radio-group v-model="couponStatusFilter" size="small" @change="handleCouponFilterChange">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="UNUSED">可用</el-radio-button>
          <el-radio-button value="USED">已使用</el-radio-button>
          <el-radio-button value="EXPIRED">已过期</el-radio-button>
        </el-radio-group>
        <el-button v-if="isAdmin" type="primary" size="small" @click="openGrantDialog">+ 发放优惠券</el-button>
      </div>
      <el-table :data="couponList" v-loading="couponLoading" stripe size="small">
        <el-table-column prop="couponName" label="券名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="面额" width="90">
          <template #default="{ row }">{{ formatCouponAmount(row) }}</template>
        </el-table-column>
        <el-table-column label="门槛" width="90">
          <template #default="{ row }">{{ row.minAmount > 0 ? '满' + row.minAmount + '元' : '无门槛' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="couponStatusTag(row.effectiveStatus)" size="small">{{ couponStatusLabel(row.effectiveStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发放时间" width="160" />
        <el-table-column prop="expireTime" label="过期时间" width="160" />
        <el-table-column v-if="isAdmin" label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              v-if="row.effectiveStatus === 'UNUSED'"
              title="确认失效该优惠券？"
              @confirm="handleRevokeCoupon(row)"
            >
              <template #reference>
                <el-button link type="danger" size="small">失效</el-button>
              </template>
            </el-popconfirm>
            <span v-else class="coupon-empty">-</span>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="couponTotal" v-model:page="couponQuery.pageNum" v-model:limit="couponQuery.pageSize" @pagination="fetchUserCoupons" />
    </el-dialog>

    <!-- 发放优惠券子弹窗 -->
    <el-dialog v-model="grantVisible" title="发放优惠券" width="450px" append-to-body destroy-on-close>
      <el-form ref="grantFormRef" :model="grantForm" :rules="grantRules" label-width="90px">
        <el-form-item label="优惠券" prop="couponId">
          <el-select v-model="grantForm.couponId" placeholder="请选择优惠券" style="width: 100%" @change="onCouponSelect">
            <el-option
              v-for="c in couponTemplates"
              :key="c.id"
              :label="c.name"
              :value="c.id"
              :disabled="c.status !== 1"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedTemplate" label="说明">
          <span class="grant-desc">{{ formatTemplateDesc(selectedTemplate) }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="grantForm.remark" placeholder="发放原因（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantVisible = false">取消</el-button>
        <el-button type="primary" :loading="grantSubmitting" @click="handleGrantSubmit">确认发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import {
  adminUserList, adminUserDetail, adminUserUpdateStatus, adminUserAdjustBalance,
  adminUserAdjustCurrentPoints, adminUserAdjustTotalPoints,
  adminOrderList, adminCouponList, adminUserCouponList, adminUserGrantCoupon, adminUserRevokeCoupon,
  csUserList, csUserDetail, csUserUpdateStatus, csOrderList, csUserCouponList
} from '@/api/business'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'
const loading = ref(false), list = ref([]), total = ref(0)
const dateRange = ref(null)
const levelOptions = [
  { value: 'BRONZE', label: '青铜伴星' },
  { value: 'SILVER', label: '白银伴星' },
  { value: 'GOLD', label: '黄金伴星' },
  { value: 'PLATINUM', label: '铂金伴星' },
  { value: 'DIAMOND', label: '钻石伴星' },
  { value: 'KING', label: '王者伴星' },
]
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: '',
  levelCode: '',
  userId: '',
  createdAtStart: '',
  createdAtEnd: '',
})
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
const memberBenefitMap = {
  BRONZE: '无会员折扣',
  SILVER: '永久9.8折（任务及定制单除外）',
  GOLD: '永久9.6折（任务及定制单除外）',
  PLATINUM: '永久9.4折（任务及定制单除外）',
  DIAMOND: '永久9.2折（任务及定制单除外）',
  KING: '永久9.0折（任务及定制单除外）'
}
function orderStatusLabel(status) { return orderStatusMap[status] || status }
function orderTagType(status) { return orderTagTypeMap[status] || '' }
function formatPoints(points) { return Number(points || 0).toLocaleString() }
function memberBenefitText(user) {
  return memberBenefitMap[user?.levelCode] || memberBenefitMap.BRONZE
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

function buildUserParams() {
  const params = { pageNum: query.pageNum, pageSize: query.pageSize }
  if (query.keyword) params.keyword = query.keyword
  if (query.status !== '' && query.status !== null && query.status !== undefined) params.status = query.status
  if (query.levelCode) params.levelCode = query.levelCode
  if (query.userId) {
    const id = Number(query.userId)
    if (!Number.isNaN(id) && id > 0) params.userId = id
  }
  if (query.createdAtStart) params.createdAtStart = query.createdAtStart
  if (query.createdAtEnd) params.createdAtEnd = query.createdAtEnd
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
    keyword: '',
    status: '',
    levelCode: '',
    userId: '',
    createdAtStart: '',
    createdAtEnd: '',
  })
  dateRange.value = null
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    const fn = isAdmin ? adminUserList : csUserList
    const res = await fn(buildUserParams())
    list.value = res.data.records; total.value = Number(res.data.total)
  } finally { loading.value = false }
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const fn = isAdmin ? adminUserUpdateStatus : csUserUpdateStatus
  await fn(row.id, newStatus)
  ElMessage.success('操作成功'); fetchData()
}

const detailVisible = ref(false), detailData = ref(null), detailLoading = ref(false)
const detailCouponCount = computed(() => {
  const userId = detailData.value?.user?.id
  if (!userId) return 0
  const row = list.value.find(u => u.id === userId)
  return row?.availableCouponCount ?? 0
})

async function showDetail(id) {
  detailVisible.value = true; detailLoading.value = true; detailData.value = null
  try {
    const res = isAdmin ? await adminUserDetail(id) : await csUserDetail(id)
    detailData.value = res.data
  } finally { detailLoading.value = false }
}

const ordersVisible = ref(false), ordersLoading = ref(false)
const userOrders = ref([]), ordersTotal = ref(0), ordersUserName = ref('')
const ordersQuery = reactive({ pageNum: 1, pageSize: 10, userId: null })
function showUserOrders(row) {
  ordersUserName.value = row.nickname || ('ID: ' + row.id)
  ordersQuery.userId = row.id
  ordersQuery.pageNum = 1
  userOrders.value = []
  ordersTotal.value = 0
  ordersVisible.value = true
  fetchUserOrders()
}
async function fetchUserOrders() {
  if (!ordersQuery.userId) return
  ordersLoading.value = true
  try {
    const fn = isAdmin ? adminOrderList : csOrderList
    const res = await fn({
      pageNum: ordersQuery.pageNum,
      pageSize: ordersQuery.pageSize,
      userId: ordersQuery.userId
    })
    userOrders.value = res.data?.records || []
    ordersTotal.value = Number(res.data?.total || 0)
  } finally { ordersLoading.value = false }
}

const balanceVisible = ref(false)
const balanceUser = ref(null)
const balanceSubmitting = ref(false)
const balanceFormRef = ref(null)
const balanceForm = reactive({ type: 'add', amount: null, remark: '' })
const balanceRules = {
  amount: [{ required: true, message: '请输入金额', trigger: 'change' }]
}

function openBalanceDialog(row) {
  balanceUser.value = row
  balanceForm.type = 'add'
  balanceForm.amount = null
  balanceForm.remark = ''
  balanceVisible.value = true
}

async function handleBalanceSubmit() {
  const valid = await balanceFormRef.value.validate().catch(() => false)
  if (!valid) return

  const amount = balanceForm.type === 'add' ? balanceForm.amount : -balanceForm.amount
  const action = balanceForm.type === 'add' ? '充值' : '扣款'

  try {
    await ElMessageBox.confirm(
      `确认对用户 "${balanceUser.value.nickname}" ${action} ¥${balanceForm.amount}？`,
      '确认操作', { type: 'warning' }
    )
  } catch { return }

  balanceSubmitting.value = true
  try {
    await adminUserAdjustBalance(balanceUser.value.id, { amount, remark: balanceForm.remark })
    ElMessage.success(`${action}成功`)
    balanceVisible.value = false
    fetchData()
  } catch (e) {
    const msg = e?.data?.msg || e?.msg || '操作失败'
    ElMessage.error(msg)
  } finally {
    balanceSubmitting.value = false
  }
}

const pointsVisible = ref(false)
const pointsUser = ref(null)
const pointsSubmitting = ref(false)
const pointsFormRef = ref(null)
const pointsForm = reactive({ account: 'current', type: 'add', points: null, remark: '' })
const pointsRules = {
  points: [{ required: true, message: '请输入积分数', trigger: 'change' }]
}

function openPointsDialog(row) {
  pointsUser.value = row
  pointsForm.account = 'current'
  pointsForm.type = 'add'
  pointsForm.points = null
  pointsForm.remark = ''
  pointsVisible.value = true
}

async function handlePointsSubmit() {
  const valid = await pointsFormRef.value.validate().catch(() => false)
  if (!valid) return

  const points = pointsForm.type === 'add' ? pointsForm.points : -pointsForm.points
  const action = pointsForm.type === 'add' ? '增加' : '减少'
  const accountLabel = pointsForm.account === 'total' ? '总积分' : '当前积分'
  const levelTip = pointsForm.account === 'total' ? '会员等级将同步重算。' : '不影响总积分与等级。'

  try {
    await ElMessageBox.confirm(
      `确认对用户 "${pointsUser.value.nickname}" ${action} ${pointsForm.points} ${accountLabel}？${levelTip}`,
      '确认调整积分', { type: 'warning' }
    )
  } catch { return }

  pointsSubmitting.value = true
  try {
    const fn = pointsForm.account === 'total' ? adminUserAdjustTotalPoints : adminUserAdjustCurrentPoints
    await fn(pointsUser.value.id, { points, remark: pointsForm.remark })
    ElMessage.success(`${action}${accountLabel}成功`)
    pointsVisible.value = false
    fetchData()
  } catch (e) {
    const msg = e?.data?.msg || e?.msg || '积分调整失败'
    ElMessage.error(msg)
  } finally {
    pointsSubmitting.value = false
  }
}

// ========== 优惠券管理 ==========
const couponVisible = ref(false)
const couponUser = ref(null)
const couponLoading = ref(false)
const couponList = ref([])
const couponTotal = ref(0)
const couponStatusFilter = ref('')
const couponQuery = reactive({ pageNum: 1, pageSize: 10, userId: null })

function isCashCoupon(row) {
  return row.couponType?.startsWith('CASH')
}

function formatCouponAmount(row) {
  if (isCashCoupon(row)) return `${Number(row.cashAmount || 0)}元`
  if (row.discountRate) return `${parseFloat((row.discountRate * 10).toFixed(1))}折`
  return '-'
}

function couponStatusLabel(status) {
  return { UNUSED: '可用', USED: '已使用', EXPIRED: '已过期' }[status] || status
}

function couponStatusTag(status) {
  return { UNUSED: 'success', USED: 'info', EXPIRED: 'danger' }[status] || ''
}

function openCouponDialog(row) {
  couponUser.value = row
  couponQuery.userId = row.id
  couponQuery.pageNum = 1
  couponStatusFilter.value = ''
  couponList.value = []
  couponTotal.value = 0
  couponVisible.value = true
  fetchUserCoupons()
}

function handleCouponFilterChange() {
  couponQuery.pageNum = 1
  fetchUserCoupons()
}

async function fetchUserCoupons() {
  if (!couponQuery.userId) return
  couponLoading.value = true
  try {
    const fn = isAdmin ? adminUserCouponList : csUserCouponList
    const params = {
      pageNum: couponQuery.pageNum,
      pageSize: couponQuery.pageSize
    }
    if (couponStatusFilter.value) params.status = couponStatusFilter.value
    const res = await fn(couponQuery.userId, params)
    couponList.value = res.data?.records || []
    couponTotal.value = Number(res.data?.total || 0)
  } finally { couponLoading.value = false }
}

async function handleRevokeCoupon(row) {
  try {
    await adminUserRevokeCoupon(couponUser.value.id, row.id)
    ElMessage.success('已失效')
    fetchUserCoupons()
    fetchData()
    couponUser.value = { ...couponUser.value, availableCouponCount: (couponUser.value.availableCouponCount || 1) - 1 }
  } catch (e) {
    ElMessage.error(e?.data?.msg || e?.msg || '操作失败')
  }
}

// 发放优惠券
const grantVisible = ref(false)
const grantSubmitting = ref(false)
const grantFormRef = ref(null)
const grantForm = reactive({ couponId: null, remark: '' })
const grantRules = {
  couponId: [{ required: true, message: '请选择优惠券', trigger: 'change' }]
}
const couponTemplates = ref([])
const selectedTemplate = computed(() => couponTemplates.value.find(c => c.id === grantForm.couponId))

function formatTemplateDesc(tpl) {
  const amount = tpl.type?.startsWith('CASH')
    ? `${Number(tpl.cashAmount || 0)}元代金券`
    : `${parseFloat((tpl.discountRate * 10).toFixed(1))}折优惠券`
  const threshold = tpl.minAmount > 0 ? `，满${tpl.minAmount}元可用` : '，无门槛'
  return amount + threshold + '，当月有效'
}

async function openGrantDialog() {
  grantForm.couponId = null
  grantForm.remark = ''
  grantVisible.value = true
  if (couponTemplates.value.length === 0) {
    try {
      const res = await adminCouponList()
      couponTemplates.value = (res.data || []).filter(c => c.status === 1)
    } catch {
      ElMessage.error('加载优惠券模板失败')
    }
  }
}

function onCouponSelect() { /* 触发 selectedTemplate 更新 */ }

async function handleGrantSubmit() {
  const valid = await grantFormRef.value.validate().catch(() => false)
  if (!valid) return

  grantSubmitting.value = true
  try {
    await adminUserGrantCoupon(couponUser.value.id, {
      couponId: grantForm.couponId,
      remark: grantForm.remark
    })
    ElMessage.success('发放成功')
    grantVisible.value = false
    fetchUserCoupons()
    fetchData()
    couponUser.value = {
      ...couponUser.value,
      availableCouponCount: (couponUser.value.availableCouponCount || 0) + 1
    }
  } catch (e) {
    ElMessage.error(e?.data?.msg || e?.msg || '发放失败')
  } finally {
    grantSubmitting.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.search-form { margin-bottom: 16px; }
.balance-text { color: #ff6b2b; font-weight: 600; }
.points-text { color: #67c23a; font-weight: 600; }
.coupon-tag { font-weight: 600; }
.coupon-empty { color: #c0c4cc; font-size: 13px; }
.balance-info, .point-info, .coupon-header {
  display: flex;
  justify-content: space-between;
  padding: 16px 20px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 14px;
  color: #606266;
}
.point-info {
  flex-wrap: wrap;
  gap: 10px 20px;
}
.benefit-tip {
  margin-top: 10px;
  color: #909399;
  font-size: 13px;
}
.points-alert { margin-top: 14px; }
.coupon-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0;
}
.grant-desc { color: #909399; font-size: 13px; }
</style>
