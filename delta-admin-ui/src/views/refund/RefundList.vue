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
            <span v-else style="font-size:12px;color:#999">{{ row.operatorRemark || statusText[row.status] }}</span>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>

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
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const statusText = { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已拒绝' }
const statusTagType = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
const snapshotText = { PAID: '待接单', ASSIGNED: '已指派' }

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

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm('确认同意退款？将全额退回用户支付金额。', '确认', { type: 'warning' })
  } catch { return }
  try {
    await csRefundApprove(row.id)
    ElMessage.success('已同意退款')
    fetchData()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
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
</style>
