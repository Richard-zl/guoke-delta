<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="qr-page">
    <!-- 列表 -->
    <scroll-view scroll-y class="qr-list">
      <view v-for="item in list" :key="item.id" class="qr-item">
        <view class="qr-content" @click="openEdit(item)">
          <text class="qr-text">{{ item.content }}</text>
          <view class="qr-meta">
            <text v-if="item.category" class="qr-category">{{ item.category }}</text>
            <el-tag v-if="item.status === 0" class="qr-disabled">已禁用</el-tag>
          </view>
        </view>
        <view class="qr-actions">
          <text class="action-btn edit" @click="openEdit(item)">编辑</text>
          <text class="action-btn delete" @click="confirmDelete(item)">删除</text>
        </view>
      </view>
      <view v-if="list.length === 0 && !loading" class="empty-tip">暂无快捷发言，点击下方按钮添加</view>
    </scroll-view>

    <!-- 底部添加按钮 -->
    <view class="bottom-bar">
      <view class="add-btn" @click="openAdd">＋ 新增快捷发言</view>
    </view>

    <!-- 新增/编辑弹窗 -->
    <view v-if="showModal" class="modal-mask" @click="showModal = false">
      <view class="modal-body" @click.stop>
        <text class="modal-title">{{ editingId ? '编辑快捷发言' : '新增快捷发言' }}</text>
        <view class="form-item">
          <text class="form-label">内容</text>
          <textarea v-model="form.content" placeholder="输入快捷发言内容" :maxlength="200" class="form-textarea" />
        </view>
        <view class="form-item">
          <text class="form-label">分类（可选）</text>
          <input v-model="form.category" placeholder="如：问候、售后" class="form-input" />
        </view>
        <view class="form-item row">
          <text class="form-label">排序</text>
          <input v-model="form.sortOrder" type="number" placeholder="0" class="form-input short" />
        </view>
        <view class="form-item row">
          <text class="form-label">启用</text>
          <switch :checked="form.status === 1" @change="form.status = $event.detail.value ? 1 : 0" color="#ff4544" />
        </view>
        <view class="modal-btns">
          <view class="modal-btn cancel" @click="showModal = false">取消</view>
          <view class="modal-btn confirm" @click="handleSubmit">{{ editingId ? '保存' : '添加' }}</view>
        </view>
      </view>
    </view>

    <!-- 删除确认弹窗 -->
    <view v-if="showDeleteConfirm" class="modal-mask" @click="showDeleteConfirm = false">
      <view class="modal-body small" @click.stop>
        <text class="modal-title">确认删除</text>
        <text class="delete-msg">确定要删除这条快捷发言吗？</text>
        <view class="delete-preview">{{ deletingItem?.content }}</view>
        <view class="modal-btns">
          <view class="modal-btn cancel" @click="showDeleteConfirm = false">取消</view>
          <view class="modal-btn danger" @click="handleDelete">删除</view>
        </view>
      </view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getQuickReplyList, addQuickReply, updateQuickReply, deleteQuickReply } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const list = ref([])
const loading = ref(false)
const showModal = ref(false)
const showDeleteConfirm = ref(false)
const editingId = ref(null)
const deletingItem = ref(null)
const form = reactive({ content: '', category: '', sortOrder: 0, status: 1 })

async function fetchList() {
  loading.value = true
  try {
    const res = await getQuickReplyList({ pageNum: 1, pageSize: 100 })
    list.value = res.data?.records || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editingId.value = null
  Object.assign(form, { content: '', category: '', sortOrder: 0, status: 1 })
  showModal.value = true
}

function openEdit(item) {
  editingId.value = item.id
  Object.assign(form, {
    content: item.content || '',
    category: item.category || '',
    sortOrder: item.sortOrder ?? 0,
    status: item.status ?? 1
  })
  showModal.value = true
}

async function handleSubmit() {
  if (!form.content.trim()) {
    return uni.showToast({ title: '请输入内容', icon: 'none' })
  }
  try {
    const data = {
      content: form.content.trim(),
      category: form.category.trim() || null,
      sortOrder: Number(form.sortOrder) || 0,
      status: form.status
    }
    if (editingId.value) {
      await updateQuickReply({ ...data, id: editingId.value })
      uni.showToast({ title: '已更新' })
    } else {
      await addQuickReply(data)
      uni.showToast({ title: '已添加' })
    }
    showModal.value = false
    fetchList()
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function confirmDelete(item) {
  deletingItem.value = item
  showDeleteConfirm.value = true
}

async function handleDelete() {
  if (!deletingItem.value) return
  try {
    await deleteQuickReply(deletingItem.value.id)
    uni.showToast({ title: '已删除' })
    showDeleteConfirm.value = false
    fetchList()
  } catch (e) {
    uni.showToast({ title: '删除失败', icon: 'none' })
  }
}

onShow(() => { fetchList() })
</script>
<style lang="scss" scoped>
.qr-page { background: #ffffff; min-height: 100vh; display: flex; flex-direction: column; overflow: hidden; }

.qr-list { flex: 1; padding: 20rpx 24rpx; padding-bottom: 140rpx; box-sizing: border-box; }
.qr-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24rpx; margin-bottom: 16rpx;
  background: rgba(0,0,0,0.04); border: 1rpx solid rgba(0,0,0,0.05);
  border-radius: 12rpx; box-sizing: border-box; overflow: hidden;
}
.qr-content { flex: 1; overflow: hidden; margin-right: 16rpx; }
.qr-text { font-size: 28rpx; color: rgba(0,0,0,0.85); display: block; line-height: 1.5; word-break: break-all; }
.qr-meta { display: flex; align-items: center; gap: 12rpx; margin-top: 8rpx; }
.qr-category { font-size: 22rpx; color: #ff4544; background: rgba(255,69,68,0.1); padding: 2rpx 12rpx; border-radius: 4rpx; }
.qr-disabled { font-size: 22rpx; color: rgba(0,0,0,0.3); }

.qr-actions { display: flex; gap: 16rpx; flex-shrink: 0; }
.action-btn {
  font-size: 24rpx; padding: 8rpx 20rpx; border-radius: 8rpx;
  &.edit { color: #ff4544; background: rgba(255,69,68,0.1); border: 1rpx solid rgba(255,69,68,0.15); }
  &.delete { color: #ee0a24; background: rgba(238,10,36,0.08); border: 1rpx solid rgba(238,10,36,0.15); }
}

.empty-tip { text-align: center; padding: 120rpx 0; color: rgba(0,0,0,0.3); font-size: 28rpx; }

.bottom-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 20rpx 24rpx; padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: rgba(255,255,255,0.95); border-top: 1rpx solid rgba(0,0,0,0.06);
}
.add-btn {
  height: 88rpx; line-height: 88rpx; text-align: center;
  background: linear-gradient(135deg, #ff4544, #e63939);
  color: #ffffff; font-weight: bold; font-size: 30rpx; border-radius: 999rpx;
}

/* 弹窗 */
.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; z-index: 999; }
.modal-body {
  width: 85%; background: #ffffff; border: 1rpx solid rgba(255,69,68,0.15);
  border-radius: 16rpx; padding: 40rpx;
  &.small { width: 75%; }
}
.modal-title { font-size: 32rpx; font-weight: bold; text-align: center; display: block; color: #ff4544; margin-bottom: 32rpx; }

.form-item {
  margin-bottom: 24rpx;
  &.row { display: flex; align-items: center; justify-content: space-between; }
}
.form-label { font-size: 26rpx; color: rgba(0,0,0,0.55); display: block; margin-bottom: 8rpx; }
.form-item.row .form-label { margin-bottom: 0; }
.form-textarea {
  width: 100%; height: 180rpx; background: rgba(0,0,0,0.05);
  border: 1rpx solid rgba(0,0,0,0.06); border-radius: 8rpx;
  padding: 20rpx; font-size: 28rpx; color: rgba(0,0,0,0.85);
}
.form-input {
  width: 100%; height: 72rpx; background: rgba(0,0,0,0.05);
  border: 1rpx solid rgba(0,0,0,0.06); border-radius: 8rpx;
  padding: 0 20rpx; font-size: 28rpx; color: rgba(0,0,0,0.85);
  &.short { width: 160rpx; text-align: center; }
}

.modal-btns { display: flex; gap: 24rpx; margin-top: 32rpx; }
.modal-btn {
  flex: 1; height: 76rpx; line-height: 76rpx; text-align: center; border-radius: 999rpx; font-size: 28rpx;
  &.cancel { background: rgba(0,0,0,0.05); color: rgba(0,0,0,0.55); }
  &.confirm { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; }
  &.danger { background: #ee0a24; color: #fff; font-weight: bold; }
}

.delete-msg { font-size: 28rpx; color: rgba(0,0,0,0.7); text-align: center; display: block; }
.delete-preview {
  margin: 20rpx 0; padding: 16rpx; background: rgba(0,0,0,0.04);
  border-radius: 8rpx; font-size: 26rpx; color: rgba(0,0,0,0.45);
  line-height: 1.5; word-break: break-all;
}
</style>
