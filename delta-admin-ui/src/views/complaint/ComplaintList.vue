<template>
  <div class="page-container">
    <el-card>
      <template #header><div class="card-header"><span>投诉处理</span><el-button type="primary" @click="openCreateDialog">创建投诉工单</el-button></div></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
            <el-option label="待处理" value="PENDING" /><el-option label="处理中" value="PROCESSING" />
            <el-option label="已解决" value="RESOLVED" /><el-option label="申诉中" value="APPEALING" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="orderId" label="订单ID" width="100" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="type" label="投诉类型" width="120" />
        <el-table-column prop="content" label="投诉内容" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG_TYPE[row.status] || 'info'" size="small">{{ STATUS_TEXT[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click="handleProcess(row)">受理</el-button>
            <el-button v-if="row.status === 'PROCESSING' || row.status === 'APPEALING'" link type="success" @click="openResolveDialog(row)">仲裁</el-button>
            <el-button link type="info" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="投诉详情" width="720px">
      <div v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="投诉ID">{{ detailData.complaint.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="STATUS_TAG_TYPE[detailData.complaint.status] || 'info'" size="small">
              {{ STATUS_TEXT[detailData.complaint.status] || detailData.complaint.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="用户">
            <el-avatar :src="detailData.userAvatar" :size="28" style="margin-right:8px">
              {{ (detailData.userNickname || '')[0] || '用' }}
            </el-avatar>
            {{ detailData.userNickname || ('ID: ' + detailData.complaint.userId) }}
          </el-descriptions-item>
          <el-descriptions-item label="打手">
            <template v-if="detailData.order?.playerId">
              <el-avatar :src="detailData.playerAvatar" :size="28" style="margin-right:8px">
                {{ (detailData.playerNickname || '')[0] || '打' }}
              </el-avatar>
              {{ detailData.playerNickname || ('ID: ' + detailData.order.playerId) }}
            </template>
            <span v-else>未指派</span>
          </el-descriptions-item>
          <el-descriptions-item label="订单号" :span="2">
            {{ detailData.order?.orderNo }} （ID: {{ detailData.order?.id }}）
          </el-descriptions-item>
          <el-descriptions-item label="商品">{{ detailData.order?.productName }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            {{ ORDER_STATUS[detailData.order?.status] || detailData.order?.status || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="投诉类型">{{ detailData.complaint.type }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData.complaint.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="投诉内容" :span="2">
            {{ detailData.complaint.content }}
          </el-descriptions-item>
          <el-descriptions-item label="期望结果" :span="2">
            {{ detailData.complaint.expectedResult || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="证据图片" v-if="detailData.complaint.images" :span="2">
            <el-image
              v-for="(img, i) in detailData.complaint.images.split(',')"
              :key="i"
              :src="img"
              style="width:80px;height:80px;margin-right:8px"
              fit="cover"
              :preview-src-list="detailData.complaint.images.split(',')"
            />
          </el-descriptions-item>
          <el-descriptions-item label="仲裁结果">
            {{ RESULT_TEXT[detailData.complaint.result] || detailData.complaint.result || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="退款金额" v-if="detailData.complaint.refundAmount">
            ¥{{ Number(detailData.complaint.refundAmount).toFixed(2) }}
          </el-descriptions-item>
          <el-descriptions-item label="打手处罚" v-if="detailData.complaint.playerPenalty && detailData.complaint.playerPenalty !== 'NONE'">
            {{ PENALTY_TEXT[detailData.complaint.playerPenalty] || detailData.complaint.playerPenalty }}
          </el-descriptions-item>
          <el-descriptions-item label="处理说明" :span="2">
            {{ detailData.complaint.resultReason || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div style="margin-top:16px">
          <div style="font-weight:600;margin-bottom:8px">订单进度</div>
          <el-timeline v-if="detailData.progress && detailData.progress.length">
            <el-timeline-item
              v-for="p in detailData.progress"
              :key="p.id"
              :timestamp="p.createdAt"
            >
              <div class="progress-content">{{ p.content }}</div>
              <div v-if="p.remark" class="progress-remark">{{ p.remark }}</div>
            </el-timeline-item>
          </el-timeline>
          <div v-else style="font-size:12px;color:#999">暂无进度记录</div>
        </div>
      </div>
    </el-dialog>
    <!-- 仲裁弹窗 -->
    <el-dialog v-model="resolveVisible" title="仲裁处理" width="550px">
      <el-form label-width="90px">
        <el-form-item label="仲裁结果">
          <el-radio-group v-model="resolveForm.result">
            <el-radio value="FULL_REFUND">全额退款</el-radio>
            <el-radio value="PARTIAL_REFUND">部分退款</el-radio>
            <el-radio value="REDO">重新服务</el-radio>
            <el-radio value="REJECT">驳回投诉</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="退款金额" v-if="resolveForm.result === 'PARTIAL_REFUND'">
          <el-input-number v-model="resolveForm.refundAmount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="打手处罚">
          <el-radio-group v-model="resolveForm.playerPenalty">
            <el-radio value="NONE">无</el-radio>
            <el-radio value="WARNING">警告</el-radio>
            <el-radio value="FREEZE">冻结账号</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明"><el-input v-model="resolveForm.resultReason" type="textarea" :rows="3" placeholder="请输入处理说明" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="resolveVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleResolve">确认仲裁</el-button></template>
    </el-dialog>
    <!-- 创建投诉工单弹窗 -->
    <el-dialog v-model="createVisible" title="创建投诉工单" width="600px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="选择用户" prop="userId">
          <el-select
            v-model="createForm.userId"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            placeholder="搜索用户昵称..."
            style="width:100%"
            @change="onUserSelected"
            clearable
          >
            <el-option v-for="u in userOptions" :key="u.id" :value="u.id" :label="u.nickname || ('ID: ' + u.id)">
              <div style="display:flex;align-items:center;gap:8px">
                <el-avatar :src="u.avatar" :size="24">{{ (u.nickname || '')[0] || '用' }}</el-avatar>
                <span>{{ u.nickname || '-' }}</span>
                <span style="font-size:12px;color:#999;margin-left:auto">ID: {{ u.id }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="关联订单" prop="orderId">
          <el-select
            v-model="createForm.orderId"
            filterable
            :loading="orderSearchLoading"
            :disabled="!createForm.userId"
            placeholder="请先选择用户"
            style="width:100%"
          >
            <el-option v-for="o in orderOptions" :key="o.id" :value="o.id" :label="o.orderNo + ' - ' + o.productName">
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span>{{ o.orderNo }} - {{ o.productName }}</span>
                <span style="font-size:12px;color:#999">¥{{ o.amount }} · {{ ORDER_STATUS[o.status] || o.status }}</span>
              </div>
            </el-option>
          </el-select>
          <div v-if="createForm.userId && orderOptions.length === 0 && !orderSearchLoading" style="font-size:12px;color:#f56c6c;margin-top:4px">该用户没有可投诉的订单</div>
        </el-form-item>
        <el-form-item label="投诉类型" prop="type">
          <el-select v-model="createForm.type" placeholder="选择类型">
            <el-option label="服务态度" value="服务态度" /><el-option label="代练质量" value="代练质量" />
            <el-option label="账号安全" value="账号安全" /><el-option label="违规操作" value="违规操作" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="投诉内容" prop="content"><el-input v-model="createForm.content" type="textarea" :rows="4" placeholder="描述投诉内容" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { csComplaintList, csComplaintProcess, csComplaintResolve, csComplaintCreate, csUserList, csOrderList, csComplaintDetail } from '@/api/business'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const STATUS_TEXT = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', APPEALING: '申诉中' }
const STATUS_TAG_TYPE = { PENDING: 'danger', PROCESSING: 'warning', RESOLVED: 'success', APPEALING: 'danger' }
const RESULT_TEXT = { FULL_REFUND: '全额退款', PARTIAL_REFUND: '部分退款', REJECT: '驳回投诉', REDO: '重新服务' }
const PENALTY_TEXT = { NONE: '无', WARNING: '警告', FREEZE: '冻结账号' }
const ORDER_STATUS = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ASSIGNED: '已指派', ACCEPTED: '已接单',
  WAITING_TEAMMATE: '组队中', IN_PROGRESS: '进行中', COMPLETED: '待确认',
  CONFIRMED: '已完成', REVIEWED: '已评价', CANCELLED: '已取消',
  REFUNDING: '退款中', REFUNDED: '已退款', DISPUTED: '争议中', ARBITRATED: '已仲裁'
}

const loading = ref(false), list = ref([]), total = ref(0), submitting = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })

async function fetchData() { loading.value = true; try { const res = await csComplaintList(query); list.value = res.data.records; total.value = Number(res.data.total) } finally { loading.value = false } }
async function handleProcess(row) { await csComplaintProcess(row.id, ''); ElMessage.success('已受理'); fetchData() }

const detailVisible = ref(false), detailData = ref(null)
async function showDetail(row) {
  const res = await csComplaintDetail(row.id)
  detailData.value = res.data
  detailVisible.value = true
}

// 仲裁
const resolveVisible = ref(false), resolveId = ref(null)
const resolveForm = reactive({ result: 'FULL_REFUND', resultReason: '', refundAmount: 0, playerPenalty: 'NONE' })
function openResolveDialog(row) { resolveId.value = row.id; Object.assign(resolveForm, { result: 'FULL_REFUND', resultReason: '', refundAmount: 0, playerPenalty: 'NONE' }); resolveVisible.value = true }
async function handleResolve() {
  if (!resolveForm.resultReason) return ElMessage.warning('请输入处理说明')
  submitting.value = true
  try {
    await csComplaintResolve(resolveId.value, {
      result: resolveForm.result,
      resultReason: resolveForm.resultReason,
      refundAmount: resolveForm.result === 'PARTIAL_REFUND' ? resolveForm.refundAmount : null,
      playerPenalty: resolveForm.playerPenalty
    })
    ElMessage.success('仲裁完成'); resolveVisible.value = false; fetchData()
  } finally { submitting.value = false }
}

// 创建投诉工单
const createVisible = ref(false), creating = ref(false), createFormRef = ref(null)
const createForm = reactive({ userId: null, orderId: null, type: '', content: '' })
const createRules = {
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }],
  orderId: [{ required: true, message: '请选择关联订单', trigger: 'change' }],
  type: [{ required: true, message: '请选择投诉类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入投诉内容', trigger: 'blur' }]
}

// 用户搜索
const userSearchLoading = ref(false)
const userOptions = ref([])
let userSearchTimer = null
function searchUsers(keyword) {
  if (!keyword || keyword.length < 1) { userOptions.value = []; return }
  clearTimeout(userSearchTimer)
  userSearchTimer = setTimeout(async () => {
    userSearchLoading.value = true
    try {
      const res = await csUserList({ keyword, pageNum: 1, pageSize: 20 })
      userOptions.value = res.data?.records || []
    } catch { userOptions.value = [] }
    finally { userSearchLoading.value = false }
  }, 300)
}

// 用户选择后加载订单
const orderSearchLoading = ref(false)
const orderOptions = ref([])
async function onUserSelected(userId) {
  createForm.orderId = null
  orderOptions.value = []
  if (!userId) return
  orderSearchLoading.value = true
  try {
    const res = await csOrderList({ userId, pageNum: 1, pageSize: 50 })
    orderOptions.value = (res.data?.records || []).filter(o =>
      ['IN_PROGRESS', 'COMPLETED', 'CONFIRMED', 'DISPUTED'].includes(o.status)
    )
  } catch { orderOptions.value = [] }
  finally { orderSearchLoading.value = false }
}

function openCreateDialog() {
  Object.assign(createForm, { userId: null, orderId: null, type: '', content: '' })
  userOptions.value = []
  orderOptions.value = []
  createVisible.value = true
}
async function handleCreate() {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await csComplaintCreate(createForm)
    ElMessage.success('投诉工单已创建'); createVisible.value = false; fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.msg || '创建失败')
  } finally { creating.value = false }
}

onMounted(fetchData)
</script>
<style scoped>.search-form { margin-bottom: 16px; } .card-header { display: flex; justify-content: space-between; align-items: center; }</style>
