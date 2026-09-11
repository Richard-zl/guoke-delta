<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>打手风采</span>
          <el-button type="primary" @click="openDialog()">新增</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="打手" min-width="160">
          <template #default="{ row }">
            <div class="player-cell">
              <el-avatar :src="row.coverUrl || row.avatar" :size="32">{{ (row.nickname || '')[0] }}</el-avatar>
              <span>{{ row.nickname || row.playerId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="上架" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="语音" width="80">
          <template #default="{ row }">{{ row.hasVoice ? '有' : '无' }}</template>
        </el-table-column>
        <el-table-column label="封面" width="100">
          <template #default="{ row }">
            <el-image v-if="row.coverUrl || row.avatar" :src="row.coverUrl || row.avatar" style="width:64px;height:36px" fit="cover" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="toggleStatus(row)">{{ row.status === 1 ? '下架' : '上架' }}</el-button>
            <el-button link @click="openReviews(row)">评价</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑风采' : '新增风采'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="打手" prop="playerId">
          <el-select
            v-model="form.playerId"
            filterable
            remote
            reserve-keyword
            placeholder="输入昵称或手机号搜索"
            :disabled="!!editingId"
            :remote-method="searchPlayers"
            :loading="playerLoading"
            style="width:100%"
            @change="onPlayerChange"
          >
            <el-option v-for="p in playerOptions" :key="p.id" :label="`${p.nickname} (#${p.id})`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面"><ImageUpload v-model="form.coverUrl" /></el-form-item>
        <el-form-item label="一句话标签" prop="tagline"><el-input v-model="form.tagline" maxlength="8" show-word-limit /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.bio" type="textarea" :rows="3" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="高光勾选">
          <el-checkbox-group v-if="galleryUrls.length" v-model="selectedList" class="gallery-group">
            <el-checkbox v-for="url in galleryUrls" :key="url" :value="url" class="gallery-check">
              <el-image :src="url" class="gallery-thumb" fit="cover" />
            </el-checkbox>
          </el-checkbox-group>
          <span v-else class="muted">该打手暂无高光图</span>
        </el-form-item>
        <el-divider content-position="left">风采展示数据</el-divider>
        <el-alert
          title="留空使用真实数据，仅影响风采首页、列表和详情"
          type="info"
          :closable="false"
          class="metric-alert"
        />
        <el-form-item label="展示评分">
          <div class="metric-row">
            <el-input-number
              v-model="form.displayRating"
              :min="1"
              :max="5"
              :precision="2"
              :step="0.1"
              placeholder="使用真实值"
            />
            <span class="metric-real">真实：{{ realMetric('rating') }}</span>
            <el-button link type="primary" @click="form.displayRating = null">使用真实值</el-button>
          </div>
        </el-form-item>
        <el-form-item label="展示完成单">
          <div class="metric-row">
            <el-input-number
              v-model="form.displayCompletedOrders"
              :min="0"
              :precision="0"
              placeholder="使用真实值"
            />
            <span class="metric-real">真实：{{ realMetric('completed') }}</span>
            <el-button link type="primary" @click="form.displayCompletedOrders = null">使用真实值</el-button>
          </div>
        </el-form-item>
        <el-form-item label="展示完成率">
          <div class="metric-row">
            <el-input-number
              v-model="form.displayCompleteRate"
              :min="0"
              :max="100"
              :precision="2"
              :step="0.1"
              placeholder="使用真实值"
            />
            <span class="metric-real">真实：{{ realRateText() }}</span>
            <el-button link type="primary" @click="form.displayCompleteRate = null">使用真实值</el-button>
          </div>
        </el-form-item>
        <el-form-item label="只读">
          <span class="muted">状态 {{ currentPlayer?.status || '-' }} · 语音 {{ currentPlayer?.hasVoice || formHasVoice ? '有' : '无' }} · 在线 {{ currentPlayer?.isOnline === 1 ? '是' : '否' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="风采评价" width="720px">
      <el-table :data="reviewList" size="small">
        <el-table-column prop="rating" label="星" width="60" />
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
        <el-table-column label="风采隐藏" width="120">
          <template #default="{ row }">
            <el-switch :model-value="row.hideInShowcase === 1" @change="(v) => hideReview(row, v)" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import {
  adminPlayerList,
  adminShowcaseList,
  adminShowcaseCreate,
  adminShowcaseUpdate,
  adminShowcaseStatus,
  adminShowcasePlayerMetrics,
  adminShowcaseReviews,
  adminShowcaseHideReview
} from '@/api/business'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })
const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  playerId: null,
  coverUrl: '',
  tagline: '',
  bio: '',
  sortOrder: 0,
  selectedImages: '',
  status: 0,
  displayRating: null,
  displayCompletedOrders: null,
  displayCompleteRate: null
})
const selectedList = ref([])
const playerOptions = ref([])
const playerLoading = ref(false)
const currentPlayer = ref(null)
const galleryUrls = computed(() => splitCsv(currentPlayer.value?.highlightImages || form.highlightImages || ''))
const formHasVoice = computed(() => !!(currentPlayer.value?.hasVoice || form.hasVoice))
const rules = { playerId: [{ required: true, message: '请选择打手', trigger: 'change' }] }
const reviewVisible = ref(false)
const reviewList = ref([])

function splitCsv(s) { return String(s || '').split(/[,，]/).map(x => x.trim()).filter(Boolean) }

function realMetric(type) {
  const p = currentPlayer.value
  if (!p) return '-'
  if (type === 'rating') return p.realAvgRating ?? p.avgRating ?? '-'
  if (type === 'completed') return p.realCompletedOrders ?? p.completedOrders ?? 0
  return p.realCompleteRate ?? p.completeRate ?? '-'
}

function realRateText() {
  const value = realMetric('rate')
  return value === '-' ? value : `${value}%`
}

async function fetchData() {
  loading.value = true
  try {
    const res = await adminShowcaseList(query)
    list.value = res.data.records
    total.value = Number(res.data.total)
  } finally { loading.value = false }
}

function playerLabel(p) {
  return p ? { ...p, highlightImages: p.highlightImages, hasVoice: !!(p.introVoiceUrl) } : null
}

function upsertPlayerOption(p) {
  if (!p?.id) return
  const rest = playerOptions.value.filter(x => x.id !== p.id)
  playerOptions.value = [p, ...rest]
}

async function searchPlayers(keyword) {
  playerLoading.value = true
  try {
    const res = await adminPlayerList({
      status: 'ACTIVE',
      keyword: (keyword || '').trim() || undefined,
      pageNum: 1,
      pageSize: 50
    })
    const records = res.data.records || []
    playerOptions.value = records
    if (currentPlayer.value?.id && !playerOptions.value.some(x => x.id === currentPlayer.value.id)) {
      upsertPlayerOption(currentPlayer.value)
    }
  } finally {
    playerLoading.value = false
  }
}

async function onPlayerChange(id) {
  const p = playerOptions.value.find(x => x.id === id)
  currentPlayer.value = playerLabel(p)
  selectedList.value = []
  if (!id) return
  const selectedId = id
  const res = await adminShowcasePlayerMetrics(id)
  if (form.playerId !== selectedId) return
  currentPlayer.value = { ...currentPlayer.value, ...(res.data || {}) }
}

function openDialog(row) {
  editingId.value = row?.id || null
  Object.assign(form, {
    playerId: row?.playerId || null,
    coverUrl: row?.coverUrl || '',
    tagline: row?.tagline || '',
    bio: row?.bio || '',
    sortOrder: row?.sortOrder || 0,
    selectedImages: row?.selectedImages || '',
    status: row?.status ?? 0,
    highlightImages: row?.highlightImages || '',
    hasVoice: row?.hasVoice,
    displayRating: row?.displayRating ?? null,
    displayCompletedOrders: row?.displayCompletedOrders ?? null,
    displayCompleteRate: row?.displayCompleteRate ?? null
  })
  selectedList.value = splitCsv(row?.selectedImages)
  currentPlayer.value = row ? { ...row, status: row.playerStatus || row.status } : null
  if (row?.playerId) {
    playerOptions.value = [{
      id: row.playerId,
      nickname: row.nickname,
      introVoiceUrl: row.introVoiceUrl,
      highlightImages: row.highlightImages,
      isOnline: row.isOnline,
      status: row.playerStatus
    }]
  } else {
    playerOptions.value = []
    searchPlayers('')
  }
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      playerId: form.playerId,
      coverUrl: form.coverUrl || '',
      tagline: form.tagline || '',
      bio: form.bio || '',
      sortOrder: form.sortOrder || 0,
      selectedImages: selectedList.value.join(','),
      status: form.status || 0,
      displayRating: form.displayRating,
      displayCompletedOrders: form.displayCompletedOrders,
      displayCompleteRate: form.displayCompleteRate
    }
    if (editingId.value) await adminShowcaseUpdate(editingId.value, payload)
    else await adminShowcaseCreate(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } finally { submitting.value = false }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  await adminShowcaseStatus(row.id, next)
  ElMessage.success('已更新')
  fetchData()
}

async function openReviews(row) {
  const res = await adminShowcaseReviews(row.playerId, { pageNum: 1, pageSize: 50 })
  reviewList.value = res.data.records || []
  reviewVisible.value = true
}

async function hideReview(row, hide) {
  await adminShowcaseHideReview(row.id, !!hide)
  row.hideInShowcase = hide ? 1 : 0
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.player-cell { display: flex; align-items: center; gap: 8px; }
.muted { color: #94a3b8; font-size: 13px; }
.metric-alert { margin-bottom: 18px; }
.metric-row { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.metric-real { min-width: 90px; color: #64748b; font-size: 13px; }

/* 图片放在 el-checkbox 的 label 里会被默认行高裁切，需重置高度与对齐 */
.gallery-group { display: flex; flex-wrap: wrap; gap: 12px; }
.gallery-check {
  display: flex;
  align-items: center;
  height: auto;
  margin: 0;
  padding: 4px 8px 4px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.gallery-check.is-checked { border-color: var(--el-color-primary); }
.gallery-check :deep(.el-checkbox__label) {
  display: flex;
  padding-left: 8px;
  line-height: 1;
}
.gallery-thumb {
  width: 72px;
  height: 72px;
  border-radius: 4px;
  display: block;
}
</style>
