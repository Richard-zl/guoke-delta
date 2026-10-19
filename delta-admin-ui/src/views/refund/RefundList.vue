<template>
  <div class="page-container">
    <el-card>
      <template #header><span>退款审核</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="待处理" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
        <el-table-column prop="userNickname" label="用户" width="120">
          <template #default="{ row }">{{ row.userNickname || ('ID: ' + row.userId) }}</template>
        </el-table-column>
        <el-table-column prop="playerNickname" label="当前打手" width="120">
          <template #default="{ row }">{{ row.playerNickname || (row.playerId ? 'ID: ' + row.playerId : '-') }}</template>
        </el-table-column>
        <el-table-column prop="orderStatusSnapshot" label="申请时状态" width="120">
          <template #default="{ row }">{{ snapshotText[row.orderStatusSnapshot] || row.orderStatusSnapshot }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="退款原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType[row.status]" size="small">{{ statusText[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorRemark" label="处理原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operatorRemark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="170" />
        <el-table-column prop="processedAt" label="处理时间" width="170">
          <template #default="{ row }">{{ row.processedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" @click="handleApprove(row)">同意</el-button>
              <el-button link type="danger" @click="openRejectDialog(row)">拒绝</el-button>
            </template>
            <span v-else style="font-size:12px;color:#999">{{ statusText[row.status] }}</span>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>

    <el-dialog v-model="approveVisible" title="同意退款" width="480px" @closed="resetApprove">
      <p class="approve-tip">确认后将全额退回用户支付金额，请选择退款原因。</p>
      <div class="reason-list">
        <el-radio-group v-model="approveReason">
          <el-radio v-for="item in approveReasons" :key="item" :value="item">{{ item }}</el-radio>
          <el-radio value="其他">其他</el-radio>
        </el-radio-group>
      </div>
      <el-input
        v-if="approveReason === '其他'"
        v-model="approveCustom"
        class="custom-reason"
        maxlength="100"
        show-word-limit
        placeholder="请输入退款原因"
      />
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approveSubmitting" @click="submitApprove">确认同意</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="拒绝原因" width="400px">
      <el-input v-model="rejectRemark" type="textarea" :rows="3" placeholder="请输入拒绝原因" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="primary" @click="handleReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { csRefundList, csRefundApprove, csRefundReject } from '@/api/business'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const statusText = { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已拒绝' }
const statusTagType = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
const snapshotText = { PAID: '待接单', ASSIGNED: '已指派' }
const approveReasons = ['无人接单', '下错订单', '更换订单', '客人原因', '投诉退款', '重复下单', '打手无法服务', '协商一致']

const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })

async function fetchData() {
  loading.value = true
  try {
    const res = await csRefundList(query)
    list.value = res.data?.records ?? []
    total.value = Number(res.data?.total ?? 0)
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally { loading.value = false }
}

const approveVisible = ref(false)
const approveId = ref(null)
const approveReason = ref('')
const approveCustom = ref('')
const approveSubmitting = ref(false)

function handleApprove(row) {
  approveId.value = row.id
  approveReason.value = ''
  approveCustom.value = ''
  approveVisible.value = true
}

function resetApprove() {
  approveId.value = null
  approveReason.value = ''
  approveCustom.value = ''
}

function buildApproveRemark() {
  if (!approveReason.value) return ''
  if (approveReason.value === '其他') {
    const custom = approveCustom.value.trim()
    return custom ? `其他：${custom}` : ''
  }
  return approveReason.value
}

async function submitApprove() {
  const remark = buildApproveRemark()
  if (!approveReason.value) {
    ElMessage.warning('请选择退款原因')
    return
  }
  if (approveReason.value === '其他' && !approveCustom.value.trim()) {
    ElMessage.warning('请填写其他原因')
    return
  }
  approveSubmitting.value = true
  try {
    await csRefundApprove(approveId.value, remark)
    ElMessage.success('已同意退款')
    approveVisible.value = false
    fetchData()
  } catch {
    // 拦截器已提示错误
  } finally {
    approveSubmitting.value = false
  }
}

const rejectVisible = ref(false), rejectId = ref(null), rejectRemark = ref('')
function openRejectDialog(row) { rejectId.value = row.id; rejectRemark.value = ''; rejectVisible.value = true }
async function handleReject() {
  if (!rejectRemark.value.trim()) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  try {
    await csRefundReject(rejectId.value, rejectRemark.value.trim())
    ElMessage.success('已拒绝')
    rejectVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(fetchData)
</script>

<style scoped>
.search-form { margin-bottom: 16px; }
.approve-tip { margin: 0 0 16px; color: #606266; font-size: 14px; }
.reason-list :deep(.el-radio-group) { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; }
.reason-list :deep(.el-radio) { margin-right: 0; height: 24px; }
.custom-reason { margin-top: 12px; }
</style>
