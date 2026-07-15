<template>
  <div class="page-container">
    <el-card>
      <template #header><span>换人申请管理</span></template>
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
          <template #default="{ row }">{{ row.playerNickname || (row.oldPlayerId ? 'ID: ' + row.oldPlayerId : '-') }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="换人原因" min-width="180" show-overflow-tooltip />
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
              <el-button link type="success" @click="openApproveDialog(row)">同意</el-button>
              <el-button link type="danger" @click="openRejectDialog(row)">拒绝</el-button>
            </template>
            <span v-else style="font-size:12px;color:#999">{{ statusText[row.status] }}</span>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>

    <!-- 同意换人弹窗：选择处理方式 -->
    <el-dialog v-model="approveVisible" title="同意换人" width="560px" @closed="resetApproveState">
      <el-alert type="warning" :closable="false" style="margin-bottom:16px">
        <template #title>被换打手本单不参与结算，无任何收益</template>
      </el-alert>

      <!-- 选择方式 -->
      <div v-if="!showPlayerPicker">
        <p style="margin:0 0 12px;color:#606266;font-size:14px">请选择处理方式：</p>
        <div class="mode-card" @click="doApproveHall">
          <div class="mode-title">🏛️ 放到接单大厅</div>
          <div class="mode-desc">订单重新进入接单大厅，等待打手自主接单</div>
        </div>
        <div class="mode-card" @click="enterPlayerPicker">
          <div class="mode-title">👤 指定打手</div>
          <div class="mode-desc">从打手列表中手动选择一名打手直接接手</div>
        </div>
      </div>

      <!-- 选择打手 -->
      <div v-if="showPlayerPicker">
        <el-form :inline="true" class="player-search">
          <el-form-item>
            <el-input v-model="playerKeyword" placeholder="搜索打手昵称/手机号" clearable @keyup.enter="searchPlayers" />
          </el-form-item>
          <el-form-item><el-button type="primary" @click="searchPlayers">搜索</el-button></el-form-item>
        </el-form>
        <el-table :data="playerList" v-loading="playerLoading" stripe size="small" max-height="300">
          <el-table-column prop="nickname" label="昵称" min-width="100" />
          <el-table-column prop="phone" label="手机" width="120" />
          <el-table-column label="进行中" width="80" align="center">
            <template #default="{ row }">{{ row.activeOrders ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="doApproveAssign(row)">选择</el-button>
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
      </div>

      <template #footer>
        <el-button v-if="showPlayerPicker" @click="showPlayerPicker = false">返回</el-button>
        <el-button @click="approveVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 拒绝弹窗 -->
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
import { csReplaceList, csReplaceApprove, csReplaceReject, playerAssignList, csOrderAssign } from '@/api/business'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const statusText = { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已拒绝' }
const statusTagType = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }

const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })

async function fetchData() {
  loading.value = true
  try {
    const res = await csReplaceList(query)
    list.value = res.data?.records ?? []
    total.value = Number(res.data?.total ?? 0)
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally { loading.value = false }
}

// ===== 同意换人 =====
const approveVisible = ref(false)
const approveRow = ref(null)
const showPlayerPicker = ref(false)
const playerList = ref([])
const playerLoading = ref(false)
const playerKeyword = ref('')
const playerPageNum = ref(1)
const playerTotal = ref(0)
const playerPageSize = 20

function openApproveDialog(row) {
  approveRow.value = row
  showPlayerPicker.value = false
  playerKeyword.value = ''
  playerList.value = []
  playerPageNum.value = 1
  playerTotal.value = 0
  approveVisible.value = true
}

function resetApproveState() {
  showPlayerPicker.value = false
  playerList.value = []
  playerPageNum.value = 1
  playerTotal.value = 0
  approveRow.value = null
}

function enterPlayerPicker() {
  showPlayerPicker.value = true
  playerPageNum.value = 1
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
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally { playerLoading.value = false }
}

async function doApproveHall() {
  try {
    await ElMessageBox.confirm('确认同意换人？订单将重新放入接单大厅，被换打手本单无收益。', '确认', { type: 'warning' })
  } catch { return }
  try {
    await csReplaceApprove(approveRow.value.id)
    ElMessage.success('已同意，订单已放入接单大厅')
    approveVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function doApproveAssign(player) {
  try {
    await ElMessageBox.confirm(`确认同意换人并将订单指派给「${player.nickname}」？被换打手本单无收益。`, '确认', { type: 'warning' })
  } catch { return }
  try {
    await csReplaceApprove(approveRow.value.id)
    try {
      await csOrderAssign(approveRow.value.orderId, player.id)
      ElMessage.success(`已同意，订单已指派给 ${player.nickname}`)
    } catch (e) {
      ElMessage.warning('换人成功，但指派失败：' + (e?.message || '请手动指派'))
    }
    approveVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ===== 拒绝 =====
const rejectVisible = ref(false), rejectId = ref(null), rejectRemark = ref('')
function openRejectDialog(row) { rejectId.value = row.id; rejectRemark.value = ''; rejectVisible.value = true }
async function handleReject() {
  try {
    await csReplaceReject(rejectId.value, rejectRemark.value)
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
.player-search { margin-bottom: 8px; }
.mode-card {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.mode-card:hover { border-color: #409eff; background: #f0f7ff; }
.mode-title { font-size: 15px; font-weight: bold; color: #303133; margin-bottom: 4px; }
.mode-desc { font-size: 13px; color: #909399; }
</style>
