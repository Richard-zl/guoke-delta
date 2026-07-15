<template>
  <view class="order-list-page tab-page">
    <canvas type="2d" id="goldDust" class="gold-dust-canvas"></canvas>
    <scroll-view scroll-y class="list tab-page-scroll" :show-scrollbar="false" @scrolltolower="loadMore">
      <OrderCard v-for="o in orders" :key="o.id" :order="o" />
      <view v-if="loading" class="loading-tip">加载中...</view>
      <EmptyState v-if="!loading && orders.length===0" text="暂无订单" image="/static/icons/暂无纪录.svg" />
      <view class="order-list-bottom-space"></view>
    </scroll-view>
    <CustomTabBar :current="2" />
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import OrderCard from '@/components/OrderCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { getOrderList } from '@/api/order'
import { useGoldDust } from '@/composables/useGoldDust'
import { blockIfUnderReview } from '@/composables/useAuditGuard'

useGoldDust()
const orders = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onShow(async () => {
  if (await blockIfUnderReview()) return
  refresh()
})

function refresh() { pageNum.value = 1; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const res = await getOrderList({ pageNum: pageNum.value, pageSize: 10 }, { loading: false })
  const list = res.data?.records || []
  if (list.length < 10) finished.value = true
  orders.value = pageNum.value === 1 ? list : [...orders.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
</script>
<style lang="scss" scoped>
.order-list-page {
  background: #f1f5f9;
  height: 100vh;
  position: relative;
}
.list {
  padding: 20rpx 24rpx;
  position: relative;
  z-index: 1;
  box-sizing: border-box;
}
.list :deep(.order-card) {
  background: #ffffff;
  border: 1rpx solid #e2e8f0;
}
.loading-tip {
  text-align: center;
  padding: 32rpx;
  font-size: 24rpx;
  color: #94a3b8;
}
.order-list-bottom-space {
  height: calc(140rpx + env(safe-area-inset-bottom));
}
</style>
