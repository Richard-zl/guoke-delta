<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="user-list">
    <view class="search-bar">
      <input v-model="keyword" placeholder="搜索用户昵称/手机号" class="search-input" @confirm="refresh" />
    </view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="u in users" :key="u.id" class="user-card">
        <image class="avatar" :src="u.avatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
        <view class="info">
          <text class="name">{{ u.nickname || u.phone }}</text>
          <text class="meta">注册：{{ u.createdAt }} | 订单：{{ u.orderCount || 0 }}</text>
        </view>
        <view class="status-badge" :class="u.status === 'DISABLED' ? 'disabled' : 'active'">{{ u.status === 'DISABLED' ? '已禁用' : '正常' }}</view>
        <view class="action-btns">
          <text v-if="u.status!=='DISABLED'" class="action-btn danger" @click="toggleStatus(u.id,'DISABLED')">禁用</text>
          <text class="action-btn" @click="toggleStatus(u.id,'ACTIVE')">启用</text>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && users.length===0" text="暂无用户" image="/static/icons/暂无项目.svg" />
    </scroll-view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getCsUserList, updateCsUserStatus } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const keyword = ref('')
const users = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onShow(() => { refresh() })

function refresh() { pageNum.value = 1; users.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  if (keyword.value) params.keyword = keyword.value
  const res = await getCsUserList(params)
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  users.value = pageNum.value === 1 ? list : [...users.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
async function toggleStatus(id, status) {
  const action = status === 'DISABLED' ? '禁用' : '启用'
  uni.showModal({ title: '提示', content: `确定${action}该用户？`, success: async (r) => {
    if (r.confirm) {
      try { await updateCsUserStatus(id, { status }); uni.showToast({ title: `已${action}` }); refresh() } catch (e) {}
    }
  }})
}
</script>
<style lang="scss" scoped>
.user-list { background: #ffffff; min-height: 100vh; overflow: hidden; }
.search-bar { padding: 16rpx 24rpx; }
.search-input { height: 72rpx; line-height: 72rpx; box-sizing: border-box; background: rgba(0,0,0,0.05); padding: 0 24rpx; border-radius: 999rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); }
.list { height: calc(100vh - 100rpx); padding: 20rpx 24rpx; box-sizing: border-box; }
.user-card {
  display: flex; align-items: center; gap: 16rpx; padding: 24rpx;
  background: rgba(0,0,0,0.04); border-radius: 12rpx; margin-bottom: 16rpx;
  box-sizing: border-box; overflow: hidden;
  .avatar { width: 72rpx; height: 72rpx; border-radius: 50%; flex-shrink: 0; }
  .info { flex: 1; overflow: hidden; .name { font-size: 28rpx; font-weight: bold; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: rgba(0,0,0,0.85); } .meta { font-size: 22rpx; color: rgba(0,0,0,0.3); display: block; margin-top: 4rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
  .status-badge { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 4rpx; flex-shrink: 0; &.active { color: #07c160; background: rgba(7,193,96,0.12); } &.disabled { color: #ee0a24; background: rgba(238,10,36,0.12); } }
  .action-btns { flex-shrink: 0; .action-btn { font-size: 24rpx; color: #ff4544; &.danger { color: #ee0a24; } } }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: rgba(0,0,0,0.3); }
</style>
