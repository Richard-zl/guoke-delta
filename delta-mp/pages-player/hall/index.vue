<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="hall-page tab-page">
    <!-- 分类筛选 -->
    <scroll-view scroll-x class="filter-bar">
      <view class="filter-item" :class="{active: !categoryId}" @click="filterCategory('')">全部</view>
      <view v-for="c in categories" :key="c.id" class="filter-item" :class="{active: categoryId===c.id}" @click="filterCategory(c.id)">{{ c.name }}</view>
    </scroll-view>
    <scroll-view scroll-y class="list" :show-scrollbar="false" @scrolltolower="loadMore">
      <view v-for="o in orders" :key="o.id" class="order-card-wrap">
        <OrderCard :order="o" detail-path="/pages-player/order/detail" embed />
        <view class="accept-bar">
          <view class="amount-block">
            <text class="income-line">到手金额：¥{{ incomeAmount(o).toFixed(2) }}</text>
            <text class="commission-hint">抽佣比例 {{ commissionPercent(o) }}%</text>
          </view>
          <view class="btn-accept" @click.stop="doAccept(o.id)">立即接单</view>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && orders.length===0" text="暂无可接订单" image="/static/icons/暂无项目.svg" />
    </scroll-view>
<CustomTabBar :current="0" />
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import OrderCard from '@/components/OrderCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getOrderHall, acceptOrder } from '@/api/player'
import { getAllCategories } from '@/api/category'
import { useRemindStore } from '@/store/remind'

const orders = ref([])
const remindStore = useRemindStore()
/** 仅主分类，用于顶部 tab 展示 */
const categories = ref([])
/** 全量分类（含子分类），用于计算主分类下的子分类 id 列表 */
const allCategories = ref([])
const categoryId = ref('')
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

/** 获取某主分类及其所有子分类的 id 列表（含多级） */
function getCategoryIdsUnderMain(mainId) {
  const flat = allCategories.value
  const ids = [mainId]
  function collect(pid) {
    flat.filter(c => c.parentId == pid).forEach(c => {
      ids.push(c.id)
      collect(c.id)
    })
  }
  collect(mainId)
  return ids
}

onShow(async () => {
  try { uni.hideHomeButton() } catch (_) {}
  remindStore.fetchPlayerRemind()
  if (!allCategories.value.length) {
    try {
      const res = await getAllCategories()
      const all = res.data || []
      allCategories.value = all
      categories.value = all.filter(c => !c.parentId || c.parentId === 0 || c.parentId === '')
    } catch (e) {}
  }
  refresh()
})

onPullDownRefresh(async () => {
  await refresh()
  uni.stopPullDownRefresh()
})

/** 订单抽佣比例（0~1），仅用订单字段，无则按 0 */
function commissionRateForOrder(order) {
  if (order != null && (order.commissionRate === 0 || (order.commissionRate != null && order.commissionRate !== ''))) {
    return Number(order.commissionRate)
  }
  return 0
}
function commissionPercent(order) {
  return Math.round(commissionRateForOrder(order) * 100)
}
function incomeAmount(order) {
  const n = Number(order?.amount) || 0
  return n * (1 - commissionRateForOrder(order))
}
function filterCategory(id) { categoryId.value = id; refresh() }
async function refresh() { pageNum.value = 1; orders.value = []; finished.value = false; await loadData() }
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 10 }
  if (categoryId.value) {
    const ids = getCategoryIdsUnderMain(categoryId.value)
    params.categoryIds = ids.join(',')
  }
  const res = await getOrderHall(params)
  const list = res.data?.records || []
  if (list.length < 10) finished.value = true
  orders.value = pageNum.value === 1 ? list : [...orders.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
async function doAccept(id) {
  uni.showModal({ title: '提示', content: '确定接受该订单？', success: async (r) => {
    if (r.confirm) {
      try {
        await acceptOrder(id)
        uni.showToast({ title: '接单成功' })
        refresh()
      } catch (e) { /* API error handled by request */ }
    }
  }})
}
</script>
<style lang="scss" scoped>
.hall-page { background: #ffffff; position: relative; }
.filter-bar { white-space: nowrap; padding: 16rpx 24rpx; background: #f1f5f9; border-bottom: 1rpx solid #f1f5f9; }
.filter-item { display: inline-block; padding: 12rpx 24rpx; margin-right: 16rpx; font-size: 26rpx; color: #64748b; background: rgba(0,0,0,0.05); border-radius: 999rpx; &.active { background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; } }
.list { height: calc(100vh - 190rpx - env(safe-area-inset-bottom)); padding: 20rpx 24rpx; overflow-x: hidden; box-sizing: border-box; }
.order-card-wrap { margin-bottom: 20rpx; overflow: hidden; border-radius: 16rpx; background: #e2e8f0; border: 1rpx solid #cbd5e1; }
.accept-bar { display: flex; justify-content: space-between; align-items: center; padding: 16rpx 24rpx; background: #e2e8f0; border-radius: 0 0 16rpx 16rpx; width: 100%; box-sizing: border-box; }
.amount-block { display: flex; flex-direction: column; gap: 4rpx; }
.order-amount { font-size: 28rpx; color: #1e293b; font-weight: 600; display: block; }
.income-line { font-size: 28rpx; color: #ee0a24; font-weight: bold; display: block; }
.commission-hint { font-size: 22rpx; color: #64748b; display: block; }
.btn-accept { padding: 12rpx 32rpx; background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; font-size: 26rpx; border-radius: 999rpx; flex-shrink: 0; }
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
</style>
