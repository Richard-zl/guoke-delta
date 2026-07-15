<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="cs-order-list tab-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <input v-model="keyword" placeholder="搜索订单号/用户" class="search-input" @confirm="refresh" />
    </view>
    <!-- 状态筛选 -->
    <scroll-view scroll-x class="filter-bar">
      <view class="filter-item" :class="{active: !statusFilter}" @click="filterStatus('')">全部</view>
      <view v-for="s in statusOptions" :key="s.value" class="filter-item" :class="{active: statusFilter===s.value}" @click="filterStatus(s.value)">{{ s.label }}</view>
    </scroll-view>
    <scroll-view scroll-y class="list" :show-scrollbar="false" @scrolltolower="loadMore">
      <view v-for="o in orders" :key="o.id" class="order-card" @click="goDetail(o.id)">
        <view class="card-top">
          <text class="order-no">{{ o.orderNo }}</text>
          <StatusTag :status="o.status" />
        </view>
        <view class="card-body">
          <text class="product-name">{{ o.productName }}</text>
          <view class="info-row">
            <text class="user">用户：{{ displayUserName(o) }}</text>
            <text class="amount">￥{{ Number(o.amount).toFixed(2) }}</text>
          </view>
          <text class="time">{{ o.createdAt }}</text>
        </view>
        <view v-if="o.status==='PAID' || o.status==='ASSIGNED'" class="card-actions">
          <view class="btn-assign" @click.stop="goAssign(o.id)">指派接单员</view>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && orders.length===0" text="暂无订单" image="/static/icons/暂无纪录.svg" />
    </scroll-view>
    <CustomTabBar :current="1" />
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { getCsOrderList } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const statusOptions = [
  { value: 'PENDING_PAYMENT', label: '待支付' }, { value: 'PAID', label: '待接单' },
  { value: 'ASSIGNED', label: '已指派' }, { value: 'ACCEPTED', label: '已接单' },
  { value: 'IN_PROGRESS', label: '进行中' }, { value: 'COMPLETED', label: '待确认' },
  { value: 'CONFIRMED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDING', label: '退款中' }, { value: 'REFUNDED', label: '已退款' },
  { value: 'DISPUTED', label: '争议中' }
]
const keyword = ref('')
const statusFilter = ref('')
const orders = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onShow(() => { refresh() })

function filterStatus(s) { statusFilter.value = s; refresh() }
function refresh() { pageNum.value = 1; orders.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  if (keyword.value) params.keyword = keyword.value
  if (statusFilter.value) params.status = statusFilter.value
  const res = await getCsOrderList(params)
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  orders.value = pageNum.value === 1 ? list : [...orders.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
function displayUserName(o) {
  const name = o.userNickname ?? o.user_nickname
  if (name != null && String(name).trim() !== '') return name
  return 'ID: ' + (o.userId ?? '')
}
function goDetail(id) { uni.navigateTo({ url: '/pages-cs/order/detail?id=' + id }) }
function goAssign(id) { uni.navigateTo({ url: '/pages-cs/order/assign?orderId=' + id }) }
</script>
<style lang="scss" scoped>
.cs-order-list { background: #ffffff; width: 100%; overflow: hidden; box-sizing: border-box; position: relative; }
.search-bar { padding: 16rpx 24rpx; background: rgba(0,0,0,0.04); }
.search-input { height: 72rpx; line-height: 72rpx; box-sizing: border-box; background: rgba(0,0,0,0.05); padding: 0 24rpx; border-radius: 999rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); }
.filter-bar { white-space: nowrap; padding: 12rpx 24rpx; background: rgba(0,0,0,0.04); border-bottom: 1rpx solid rgba(0,0,0,0.04); }
.filter-item { display: inline-block; padding: 10rpx 20rpx; margin-right: 12rpx; font-size: 24rpx; color: rgba(0,0,0,0.55); background: rgba(0,0,0,0.05); border-radius: 999rpx; &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; } }
.list { height: calc(100vh - 300rpx - env(safe-area-inset-bottom)); padding: 20rpx 24rpx; box-sizing: border-box; overflow-x: hidden; }
.order-card { background: rgba(0,0,0,0.04); border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx;
  .card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; .order-no { font-size: 24rpx; color: rgba(0,0,0,0.3); } }
  .card-body { .product-name { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 8rpx; color: rgba(0,0,0,0.85); } .info-row { display: flex; justify-content: space-between; .user { font-size: 24rpx; color: rgba(0,0,0,0.55); } .amount { font-size: 28rpx; color: #ff4544; font-weight: bold; } } .time { font-size: 22rpx; color: rgba(0,0,0,0.3); display: block; margin-top: 4rpx; } }
  .card-actions { display: flex; justify-content: flex-end; padding-top: 16rpx; margin-top: 12rpx; border-top: 1rpx solid rgba(0,0,0,0.04); }
  .btn-assign { padding: 10rpx 28rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; font-size: 24rpx; border-radius: 999rpx; }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: rgba(0,0,0,0.3); }
</style>
