<template>
  <view class="product-list">
    <view class="search-bar">
      <input v-model="keyword" placeholder="搜索商品" confirm-type="search" @confirm="doSearch" :focus="autoFocus" />
    </view>
    <!-- 排序栏 -->
    <view class="sort-bar">
      <view v-for="s in sortOptions" :key="s.value" class="sort-item" :class="{active: currentSort===s.value}" @click="switchSort(s.value)">
        {{ s.label }}
        <text v-if="currentSort===s.value" class="sort-arrow">{{ sortAsc ? '↑' : '↓' }}</text>
      </view>
    </view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view class="product-grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && products.length===0" text="暂无商品" image="/static/icons/购物车空空如也.svg" />
    </scroll-view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ProductCard from '@/components/ProductCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getProductList } from '@/api/product'
import { blockIfUnderReview } from '@/composables/useAuditGuard'

const keyword = ref('')
const products = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
const categoryId = ref('')
const parentCategoryId = ref('')
const autoFocus = ref(false)
const currentSort = ref('')
const sortAsc = ref(true)
const sortOptions = [
  { label: '综合', value: '' },
  { label: '价格', value: 'price' },
  { label: '销量', value: 'sales' }
]

onLoad(async (opts) => {
  if (await blockIfUnderReview()) return
  if (opts.categoryId) categoryId.value = opts.categoryId
  if (opts.parentCategoryId) parentCategoryId.value = opts.parentCategoryId
  if (opts.keyword) keyword.value = opts.keyword
  if (opts.focus) autoFocus.value = true
  loadData()
})

onShow(async () => {
  if (await blockIfUnderReview()) return
})

function switchSort(val) {
  if (currentSort.value === val) { sortAsc.value = !sortAsc.value }
  else { currentSort.value = val; sortAsc.value = val === 'price' }
  refresh()
}
function doSearch() { refresh() }
function refresh() { pageNum.value = 1; products.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 10 }
  if (keyword.value) params.keyword = keyword.value
  if (categoryId.value) params.categoryId = categoryId.value
  if (parentCategoryId.value) params.parentCategoryId = parentCategoryId.value
  if (currentSort.value) {
    params.orderBy = currentSort.value
    params.orderDir = sortAsc.value ? 'asc' : 'desc'
  }
  const res = await getProductList(params)
  const list = res.data?.records || res.data?.rows || []
  if (list.length < 10) finished.value = true
  products.value = pageNum.value === 1 ? list : [...products.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
</script>
<style lang="scss" scoped>
.product-list { background: #f1f5f9; min-height: 100vh; }
.search-bar { padding: 20rpx 24rpx; background: #f1f5f9; input { height: 72rpx; background: #ffffff; border-radius: 999rpx; padding: 0 32rpx; font-size: 28rpx; color: #1e293b; box-shadow: 0 2rpx 12rpx rgba(99, 102, 241, 0.06); } }
.sort-bar { display: flex; background: #ffffff; margin: 0 24rpx; border-radius: 12rpx; padding: 0 12rpx; box-shadow: 0 2rpx 12rpx rgba(99, 102, 241, 0.06); margin-bottom: 16rpx; }
.sort-item { flex: 1; text-align: center; padding: 20rpx 0; font-size: 26rpx; color: #94a3b8; &.active { color: #ff4544; font-weight: 500; } .sort-arrow { font-size: 22rpx; margin-left: 4rpx; } }
.list { height: calc(100vh - 240rpx); padding: 20rpx 24rpx; box-sizing: border-box; }
.product-grid {
  display: flex;
  flex-direction: column;
  row-gap: 20rpx;
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
</style>
