<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="category-page">
    <view v-for="group in groupedCategories" :key="group.parent?.id || 'root'" class="category-group">
      <view v-if="group.parent" class="group-title">{{ group.parent.name }}</view>
      <view class="group-title">顶级分类</view>
      <view v-for="c in group.children" :key="c.id" class="category-card">
        <view class="card-left">
          <image v-if="c.icon && !c.parentId" class="icon" :src="c.icon" mode="aspectFill" lazy-load />
          <view class="info" :class="{ 'sub-info': c.parentId }">
            <text class="name">{{ c.name }}</text>
            <text class="count">商品数：{{ c.productCount || 0 }}</text>
          </view>
        </view>
        <view class="card-actions">
          <text class="action-btn" @click="editCategory(c)">编辑</text>
          <text class="action-btn danger" @click="doDelete(c.id)">删除</text>
        </view>
      </view>
    </view>
    <EmptyState v-if="categories.length===0" text="暂无分类" image="/pages-cs/static/icons/文件夹空空如也.svg" />
    <view class="add-btn" @click="openAddDialog">+ 添加分类</view>
    <!-- 添加/编辑弹窗 -->
    <view v-if="showAddDialog" class="mask" @click="showAddDialog=false">
      <view class="dialog" @click.stop>
        <text class="dialog-title">{{ editingId ? '编辑分类' : '添加分类' }}</text>
        <view class="form-item">
          <text class="label">父级分类</text>
          <picker :value="parentIndex" :range="parentCategoryNames" @change="onParentChange">
            <text :class="['pick-text', categoryForm.parentId ? '' : 'placeholder']">{{ selectedParentName || '无（顶级分类）' }}</text>
          </picker>
        </view>
        <view class="form-item">
          <text class="label">分类名称</text>
          <input v-model="categoryForm.name" placeholder="请输入分类名称" class="input" />
        </view>
        <view class="form-item">
          <text class="label">排序</text>
          <input type="number" v-model="categoryForm.sort" placeholder="0" class="input" />
        </view>
        <view class="form-item" v-if="!categoryForm.parentId">
          <text class="label">图标</text>
          <ImageUploader v-model="iconImages" :max="1" />
        </view>
        <view class="dialog-btn" @click="doSave">保存</view>
      </view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import { getCsCategoryList, saveCsCategory, deleteCsCategory } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const categories = ref([])
const showAddDialog = ref(false)
const editingId = ref('')
const categoryForm = reactive({ name: '', sort: 0, icon: '', parentId: '' })
const iconImages = ref([])
const parentIndex = ref(-1)

const parentCategories = computed(() => {
  return categories.value.filter((c) => !c.parentId || c.parentId === 0 || c.parentId === '')
})

const parentCategoryNames = computed(() => {
  return ['无（顶级分类）', ...parentCategories.value.map((c) => c.name)]
})

const selectedParentName = computed(() => {
  if (!categoryForm.parentId) return '无（顶级分类）'
  const p = parentCategories.value.find((c) => c.id === categoryForm.parentId)
  return p?.name || '无（顶级分类）'
})

const groupedCategories = computed(() => {
  const parents = parentCategories.value
  const childrenMap = {}
  categories.value.forEach((c) => {
    const pid = c.parentId ?? ''
    const key = pid === 0 || pid === null || pid === undefined ? '' : String(pid)
    if (!childrenMap[key]) childrenMap[key] = []
    childrenMap[key].push(c)
  })
  const result = []
  result.push({ parent: null, children: childrenMap[''] || [] })
  parents.forEach((p) => {
    result.push({ parent: p, children: childrenMap[String(p.id)] || [] })
  })
  return result
})

onShow(() => { loadData() })

function openAddDialog() {
  editingId.value = ''
  categoryForm.name = ''
  categoryForm.sort = 0
  categoryForm.parentId = ''
  iconImages.value = []
  parentIndex.value = 0
  showAddDialog.value = true
}

async function loadData() {
  try { const res = await getCsCategoryList({ pageNum: 1, pageSize: 100 }); categories.value = res.data?.records || res.data || [] } catch (e) {}
}

function onParentChange(e) {
  const idx = e.detail.value
  parentIndex.value = idx
  if (idx === 0) {
    categoryForm.parentId = ''
  } else {
    categoryForm.parentId = parentCategories.value[idx - 1]?.id || ''
  }
}

function editCategory(c) {
  editingId.value = c.id
  categoryForm.name = c.name
  categoryForm.sort = c.sort || 0
  categoryForm.icon = c.icon || ''
  categoryForm.parentId = c.parentId || ''
  iconImages.value = c.icon ? [c.icon] : []
  const pid = categoryForm.parentId
  parentIndex.value = pid ? parentCategories.value.findIndex((p) => p.id === pid) + 1 : 0
  showAddDialog.value = true
}
async function doSave() {
  if (!categoryForm.name) return uni.showToast({ title: '请输入名称', icon: 'none' })
  categoryForm.icon = categoryForm.parentId ? '' : (iconImages.value[0] || '')
  const data = { ...categoryForm, sort: Number(categoryForm.sort) || 0 }
  if (!data.parentId) delete data.parentId
  if (editingId.value) data.id = editingId.value
  try {
    await saveCsCategory(data)
    uni.showToast({ title: '保存成功' })
    showAddDialog.value = false
    editingId.value = ''
    categoryForm.name = ''; categoryForm.sort = 0; categoryForm.parentId = ''; iconImages.value = []; parentIndex.value = -1
    loadData()
  } catch (e) {}
}
function doDelete(id) {
  uni.showModal({ title: '提示', content: '确定删除该分类？', success: async (r) => {
    if (r.confirm) {
      try { await deleteCsCategory(id); uni.showToast({ title: '已删除' }); loadData() } catch (e) {}
    }
  }})
}
</script>
<style lang="scss" scoped>
.category-page { background: #ffffff; min-height: 100vh; padding: 24rpx; }
.group-title { font-size: 26rpx; color: #ff4544; font-weight: 600; margin: 24rpx 0 12rpx; padding-left: 8rpx; }
.group-title:first-child { margin-top: 0; }
.category-card { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx; margin-bottom: 16rpx;
  .card-left { display: flex; align-items: center; gap: 16rpx; }
  .icon { width: 64rpx; height: 64rpx; border-radius: 8rpx; }
  .info { .name { font-size: 28rpx; font-weight: bold; display: block; color: rgba(0,0,0,0.85); } .count { font-size: 22rpx; color: rgba(0,0,0,0.3); display: block; margin-top: 4rpx; } &.sub-info { padding-left: 16rpx; border-left: 4rpx solid rgba(255,69,68,0.25); } }
  .card-actions { display: flex; gap: 20rpx; .action-btn { font-size: 26rpx; color: #ff4544; &.danger { color: #ee0a24; } } }
}
.add-btn { text-align: center; padding: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx; font-size: 28rpx; color: #ff4544; }
.mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 999; }
.dialog { width: 80%; background: #ffffff; border-radius: 16rpx; padding: 32rpx; border: 1rpx solid rgba(0,0,0,0.06);
  .dialog-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 24rpx; text-align: center; color: rgba(0,0,0,0.85); }
  .form-item { margin-bottom: 20rpx; .label { font-size: 26rpx; color: rgba(0,0,0,0.85); display: block; margin-bottom: 8rpx; } .input { background: rgba(0,0,0,0.05); padding: 16rpx; border-radius: 8rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); } .pick-text { display: block; padding: 16rpx; background: rgba(0,0,0,0.05); border-radius: 8rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); &.placeholder { color: rgba(0,0,0,0.35); } } }
  .dialog-btn { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; text-align: center; padding: 20rpx; border-radius: 999rpx; font-size: 28rpx; margin-top: 16rpx; }
}
</style>
