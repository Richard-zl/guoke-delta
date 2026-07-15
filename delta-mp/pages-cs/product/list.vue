<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="product-list">
    <view class="search-bar">
      <input v-model="keyword" placeholder="搜索商品名称" class="search-input" @confirm="refresh" />
      <view class="add-btn" @click="goAdd">+ 添加</view>
    </view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="p in products" :key="p.id" class="product-card" @click="goEdit(p.id)">
        <image class="cover" :src="p.coverImage || p.image" mode="aspectFill" lazy-load />
        <view class="info">
          <text class="name">{{ p.name }}</text>
          <text class="price">¥{{ Number(p.price || p.minPrice || 0).toFixed(2) }}</text>
          <view class="meta">
            <text class="sales">销量 {{ p.sales || 0 }}</text>
            <text class="status-text" :class="p.status === 'ON' ? 'on' : 'off'">{{ p.status === 'ON' ? '上架' : '下架' }}</text>
          </view>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && products.length===0" text="暂无商品" image="/static/icons/购物车空空如也.svg" button-text="添加商品" @action="goAdd" />
    </scroll-view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getCsProductList } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const keyword = ref('')
const products = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onShow(() => { refresh() })

function refresh() { pageNum.value = 1; products.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  if (keyword.value) params.keyword = keyword.value
  const res = await getCsProductList(params)
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  products.value = pageNum.value === 1 ? list : [...products.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
function goAdd() { uni.navigateTo({ url: '/pages-cs/product/edit' }) }
function goEdit(id) { uni.navigateTo({ url: '/pages-cs/product/edit?id=' + id }) }
</script>
<style lang="scss" scoped>
.product-list { background: #ffffff; min-height: 100vh; overflow: hidden; }
.search-bar { display: flex; gap: 16rpx; padding: 16rpx 24rpx;
.search-input { flex: 1; background: rgba(0,0,0,0.05); padding: 24rpx 24rpx; border-radius: 999rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); }
  .add-btn { padding: 16rpx 32rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; border-radius: 999rpx; font-size: 26rpx; flex-shrink: 0; }
}
.list { height: calc(100vh - 120rpx); padding: 20rpx 24rpx; box-sizing: border-box; }
.product-card {
  display: flex; gap: 16rpx; padding: 20rpx;
  background: rgba(0,0,0,0.04); border-radius: 12rpx; margin-bottom: 16rpx;
  box-sizing: border-box; overflow: hidden;
  .cover { width: 160rpx; height: 160rpx; border-radius: 8rpx; flex-shrink: 0; }
  .info { flex: 1; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden;
    .name { font-size: 28rpx; font-weight: bold; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; color: rgba(0,0,0,0.85); }
    .price { font-size: 30rpx; color: #ee0a24; font-weight: bold; }
    .meta { display: flex; justify-content: space-between; .sales { font-size: 22rpx; color: rgba(0,0,0,0.3); } .status-text { font-size: 22rpx; &.on { color: #07c160; } &.off { color: #ee0a24; } } }
  }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: rgba(0,0,0,0.3); }
</style>
