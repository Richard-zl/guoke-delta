<template>
  <div class="page-container">
    <el-card>
      <template #header><span>接力申请审核</span></template>
      <p class="tip">打手在服务中因故无法继续时可申请接力，审核通过后需指定一名打手接手完成订单。</p>
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
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="170" />
        <el-table-column prop="productName" label="商品" min-width="120" show-overflow-tooltip />
        <el-table-column prop="orderAmount" label="订单金额" width="100">
          <template #default="{ row }">¥{{ row.orderAmount ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="originalPlayerName" label="申请打手" width="110">
          <template #default="{ row }">{{ row.originalPlayerName || ('ID:' + row.originalPlayerId) }}</template>
        </el-table-column>
        <el-table-column label="分成方式" width="120">
          <template #default="{ row }">{{ splitDisplay(row) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType[row.status]" size="small">{{ statusText[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="newPlayerName" label="接力打手" width="110">
          <template #default="{ row }">{{ row.newPlayerName || (row.newPlayerId ? 'ID:' + row.newPlayerId : '-') }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="165" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" @click="openApproveDialog(row)">通过</el-button>
              <el-button link type="danger" @click="openRejectDialog(row)">拒绝</el-button>
            </template>
            <span v-else class="processed">{{ row.reviewedAt ? '已处理' : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>

    <!-- 通过：选择接力打手 -->
    <el-dialog v-model="approveVisible" title="通过接力 — 指定接力打手" width="620px">
      <p class="dialog-tip">选择一名打手接手该订单，原打手将按申请的分成方式结算已完成部分。</p>
      <el-form :inline="true" class="player-search">
        <el-form-item><el-input v-model="playerKeyword" placeholder="搜索打手昵称/手机号" clearable @keyup.enter="searchPlayers" /></el-form-item>
        <el-form-item><el-button type="primary" @click="searchPlayers">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="playerList" v-loading="playerLoading" stripe size="small" max-height="320">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="nickname" label="昵称" min-width="100" />
        <el-table-column prop="phone" label="手机" width="120" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="{ ACTIVE: 'success', PENDING: 'warning', FROZEN: 'info' }[row.status] || 'info'" size="small">
              {{ { ACTIVE: '正常', PENDING: '待审核', FROZEN: '已冻结' }[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.id === approveRow?.originalPlayerId" @click="confirmApprove(row.id)">
              {{ row.id === approveRow?.originalPlayerId ? '本人' : '选择' }}
            </el-button>
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
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 拒绝 -->
    <el-dialog v-model="rejectVisible" title="拒绝接力申请" width="420px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入拒绝原因（可选，将通知申请打手）" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="primary" @click="handleReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { csRelayList, csRelayApprove, csRelayReject, playerAssignList } from '@/api/business'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const statusText = { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已拒绝' }
const statusTagType = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }

function splitDisplay(row) {
  const t = { FIFTY_FIFTY: '五五开', FORTY_SIXTY: '四六开', THIRTY_SEVENTY: '三七开', CUSTOM: '自定义' }[row.splitType] || row.splitType
  if (row.splitType === 'CUSTOM' && row.splitAmount != null) return t + ' ¥' + row.splitAmount
  return t
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })

async function fetchData() {
  loading.value = true
  try {
    const res = await csRelayList(query)
    list.value = res.data?.records ?? []
    total.value = Number(res.data?.total ?? 0)
  } finally {
    loading.value = false
  }
}

// 通过：选择接力打手
const approveVisible = ref(false)
const approveRow = ref(null)
const playerList = ref([])
const playerLoading = ref(false)
const playerKeyword = ref('')
const playerPageNum = ref(1)
const playerTotal = ref(0)
const playerPageSize = 20

function openApproveDialog(row) {
  approveRow.value = row
  playerList.value = []
  playerKeyword.value = ''
  playerPageNum.value = 1
  playerTotal.value = 0
  approveVisible.value = true
  fetchPlayers()
}

function searchPlayers() {
  playerPageNum.value = 1
  fetchPlayers()
}

async function fetchPlayers() {
  playerLoading.value = true
  try {
    const res = await playerAssignList({
      pageNum: playerPageNum.value,
      pageSize: playerPageSize,
      keyword: playerKeyword.value || undefined,
    })
    const page = res.data?.players || {}
    playerList.value = page.records ?? []
    playerTotal.value = Number(page.total || 0)
  } finally {
    playerLoading.value = false
  }
}

async function confirmApprove(newPlayerId) {
  if (!approveRow.value) return
  try {
    await csRelayApprove(approveRow.value.id, newPlayerId)
    ElMessage.success('已通过，已指定接力打手')
    approveVisible.value = false
    approveRow.value = null
    fetchData()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// 拒绝
const rejectVisible = ref(false)
const rejectId = ref(null)
const rejectReason = ref('')

function openRejectDialog(row) {
  rejectId.value = row.id
  rejectReason.value = ''
  rejectVisible.value = true
}

async function handleReject() {
  try {
    await csRelayReject(rejectId.value, rejectReason.value)
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
.tip { color: #666; font-size: 13px; margin: -8px 0 12px 0; }
.search-form { margin-bottom: 16px; }
.processed { font-size: 12px; color: #999; }
.dialog-tip { color: #666; font-size: 13px; margin-bottom: 12px; }
.player-search { margin-bottom: 8px; }
</style>
