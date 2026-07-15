<template>
  <view class="category-page tab-page">
    <canvas type="2d" id="goldDust" class="gold-dust-canvas"></canvas>

    <!-- 审核期：资讯浏览 -->
    <view v-if="configLoaded && isUnderReview" class="category-body">
      <scroll-view scroll-y class="side" :show-scrollbar="false">
        <view
          v-for="c in auditCategories"
          :key="c.id"
          class="side-item"
          :class="{ active: selectedAuditId === c.id }"
          @click="selectAuditCategory(c.id)"
        >
          <text class="side-text">{{ c.name }}</text>
        </view>
      </scroll-view>
      <scroll-view scroll-y class="main" :show-scrollbar="false">
        <view class="audit-section">
          <view
            v-for="article in currentAuditArticles"
            :key="article.id"
            class="audit-card"
            @click="openAuditArticle(article)"
          >
            <image
              v-if="article.coverImage"
              class="audit-card-cover"
              :src="article.coverImage"
              mode="aspectFill"
              lazy-load
            />
            <view class="audit-card-info">
              <text class="audit-card-title">{{ article.title }}</text>
              <text class="audit-card-summary">{{ article.summary }}</text>
              <view class="audit-card-meta">
                <text class="audit-card-date">{{ formatAuditDate(article.publishDate) }}</text>
                <text class="audit-card-author">{{ article.author }}</text>
              </view>
            </view>
          </view>
          <view v-if="currentAuditArticles.length === 0" class="empty-tip">暂无内容</view>
        </view>
      </scroll-view>
    </view>

    <!-- 正常模式：商品分类 -->
    <view v-else-if="configLoaded" class="category-body">
      <scroll-view scroll-y class="side" :show-scrollbar="false">
        <view
          v-for="c in parentCategories"
          :key="c.id"
          class="side-item"
          :class="{ active: selectedParentId === c.id }"
          @click="selectParent(c)"
        >
          <text class="side-text">{{ c.name }}</text>
        </view>
      </scroll-view>
      <scroll-view scroll-y class="main" :show-scrollbar="false" @scrolltolower="loadMoreProducts">
        <scroll-view scroll-x class="children-wrap" :show-scrollbar="false">
          <view class="children-inner">
            <view
              v-for="child in currentChildren"
              :key="child.id"
              class="child-tag"
              :class="{ active: selectedChildId === child.id }"
              @click="selectChild(child.id)"
            >
              {{ child.name }}
            </view>
          </view>
        </scroll-view>
        <view class="product-section">
          <view
            v-for="p in products" :key="p.id"
            class="product-row"
            @click="goProductDetail(p.id)"
          >
            <image class="product-cover" :src="p.coverImage" mode="aspectFill" lazy-load />
            <view class="product-info">
              <text class="product-name">{{ p.name }}</text>
              <text v-if="p.subtitle" class="product-subtitle">{{ p.subtitle }}</text>
              <view class="product-bottom">
                <text class="product-price">¥{{ Number(p.price || 0).toFixed(2) }}</text>
              </view>
            </view>
          </view>
          <view v-if="productLoading" class="loading-tip">加载中...</view>
          <view v-if="!productLoading && products.length === 0" class="empty-tip">暂无商品</view>
          <view v-if="productFinished && products.length > 0" class="loading-tip">没有更多了</view>
        </view>
      </scroll-view>
    </view>

    <CustomTabBar />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { getCategoryTree } from '@/api/category'
import { getProductList } from '@/api/product'
import { useGoldDust } from '@/composables/useGoldDust'
import { useAuditMode } from '@/composables/useAuditMode'
import { useSiteStore } from '@/store/site'
import { openAuditArticleDetail, formatAuditDate } from '@/composables/useAuditArticle'
import { AUDIT_CATEGORIES, AUDIT_ARTICLES } from '@/constants/auditContent'

useGoldDust()

const siteStore = useSiteStore()
const { isUnderReview, configLoaded } = useAuditMode()

const auditCategories = AUDIT_CATEGORIES
const selectedAuditId = ref(AUDIT_CATEGORIES[0]?.id || 'guide')

const currentAuditArticles = computed(() => AUDIT_ARTICLES[selectedAuditId.value] || [])

const categoryTree = ref([])
const selectedParentId = ref(0)
const selectedChildId = ref(0)
const loading = ref(false)
const products = ref([])
const productLoading = ref(false)
const productFinished = ref(false)
const productPageNum = ref(1)
const parentCategories = computed(() => categoryTree.value)
const currentChildren = computed(() => {
  const parent = categoryTree.value.find((c) => c.id === selectedParentId.value)
  return parent?.children || []
})
const queryCategoryId = computed(() => (selectedChildId.value ? selectedChildId.value : null))
const queryParentCategoryId = computed(() => {
  if (!selectedChildId.value && selectedParentId.value) return selectedParentId.value
  return null
})
const inited = ref(false)

onShow(async () => {
  if (!siteStore.configLoaded) await siteStore.fetchSiteConfig()
  if (isUnderReview.value) {
    uni.setNavigationBarTitle({ title: '资讯' })
    return
  }
  uni.setNavigationBarTitle({ title: '分类' })

  const storedId = uni.getStorageSync('selectedCategoryId')
  if (storedId) uni.removeStorageSync('selectedCategoryId')

  if (!inited.value) {
    loading.value = true
    try {
      const res = await getCategoryTree()
      const tree = res.data || []
      categoryTree.value = tree
      if (storedId) {
        applyStoredId(tree, Number(storedId))
      } else if (tree.length > 0) {
        selectedParentId.value = tree[0].id
        selectedChildId.value = tree[0].children?.[0]?.id ?? 0
      }
    } catch (e) {
      categoryTree.value = []
    }
    loading.value = false
    inited.value = true
    refreshProducts()
  } else if (storedId) {
    applyStoredId(categoryTree.value, Number(storedId))
    refreshProducts()
  }
})

function selectAuditCategory(id) {
  selectedAuditId.value = id
}

function openAuditArticle(article) {
  openAuditArticleDetail(article)
}

function applyStoredId(tree, id) {
  const isParent = tree.some(c => c.id === id)
  if (isParent) {
    selectedParentId.value = id
    selectedChildId.value = 0
  } else {
    for (const p of tree) {
      const child = (p.children || []).find(c => c.id === id)
      if (child) {
        selectedParentId.value = p.id
        selectedChildId.value = child.id
        break
      }
    }
  }
}

function selectParent(c) {
  selectedParentId.value = c.id
  selectedChildId.value = c.children?.[0]?.id ?? 0
  refreshProducts()
}

function selectChild(childId) {
  selectedChildId.value = childId
  refreshProducts()
}

function refreshProducts() {
  productPageNum.value = 1
  products.value = []
  productFinished.value = false
  loadProducts()
}

async function loadProducts() {
  if (productLoading.value) return
  productLoading.value = true
  try {
    const params = { pageNum: productPageNum.value, pageSize: 10 }
    if (queryCategoryId.value) {
      params.categoryId = queryCategoryId.value
    } else if (queryParentCategoryId.value) {
      params.parentCategoryId = queryParentCategoryId.value
    }
    const res = await getProductList(params)
    const list = res.data?.records || res.data?.rows || []
    if (list.length < 10) productFinished.value = true
    products.value = productPageNum.value === 1 ? list : [...products.value, ...list]
  } catch (e) {
    products.value = productPageNum.value === 1 ? [] : products.value
  }
  productLoading.value = false
}

function goProductDetail(id) {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}

function loadMoreProducts() {
  if (!productLoading.value && !productFinished.value) {
    productPageNum.value++
    loadProducts()
  }
}
</script>

<style lang="scss" scoped>
.category-page {
  height: 100vh;
  background: #f1f5f9;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding-bottom: 0;
}

.category-body {
  position: absolute;
  top: 0;
  right: 0;
  bottom: calc(110rpx + env(safe-area-inset-bottom));
  left: 0;
  display: flex;
  overflow: hidden;
  z-index: 1;
}

.side {
  width: 200rpx;
  height: 100%;
  background: #ffffff;
  flex-shrink: 0;
  box-sizing: border-box;
  overflow: hidden;
}

.side-item {
  padding: 28rpx 16rpx;
  font-size: 26rpx;
  color: #64748b;
  text-align: center;
  position: relative;
  box-sizing: border-box;

  .side-text {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &.active {
    background: #f1f5f9;
    color: #ff4544;
    font-weight: 600;
    border-left: 6rpx solid #ff4544;
  }
}

.main {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 20rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.children-wrap {
  white-space: nowrap;
  margin-bottom: 24rpx;
  height: 72rpx;
}

.children-inner {
  display: inline-flex;
  gap: 16rpx;
  padding-right: 20rpx;
}

.child-tag {
  display: inline-block;
  padding: 14rpx 28rpx;
  font-size: 24rpx;
  color: #64748b;
  background: #ffffff;
  border-radius: 999rpx;
  box-shadow: 0 2rpx 10rpx rgba(99, 102, 241, 0.06);
  white-space: nowrap;
  flex-shrink: 0;

  &.active {
    background: rgba(99, 102, 241, 0.1);
    color: #ff4544;
    font-weight: 600;
  }
}

.audit-section {
  min-height: 200rpx;
}

.audit-card {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(99, 102, 241, 0.06);
}

.audit-card-cover {
  width: 100%;
  height: 240rpx;
  display: block;
  background: #e2e8f0;
}

.audit-card-info {
  padding: 24rpx;
}

.audit-card-title {
  font-size: 28rpx;
  color: #1e293b;
  font-weight: 600;
  display: block;
  line-height: 1.4;
}

.audit-card-summary {
  font-size: 24rpx;
  color: #94a3b8;
  margin-top: 10rpx;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.audit-card-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: #cbd5e1;
}

.audit-card-date {
  color: #94a3b8;
}

.audit-card-author {
  color: #cbd5e1;
}

.product-section {
  min-height: 200rpx;
  overflow: hidden;
}

.product-row {
  display: flex;
  gap: 20rpx;
  padding: 20rpx;
  background: #ffffff;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(99, 102, 241, 0.06);
  overflow: hidden;
}

.product-cover {
  width: 180rpx;
  height: 180rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}

.product-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

.product-name {
  font-size: 26rpx;
  color: #1e293b;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-subtitle {
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 6rpx;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 8rpx;
}

.product-price {
  font-size: 32rpx;
  font-weight: bold;
  color: #ff4544;
}

.loading-tip,
.empty-tip {
  text-align: center;
  padding: 32rpx;
  font-size: 24rpx;
  color: #94a3b8;
}

.empty-tip {
  padding: 60rpx;
  font-size: 26rpx;
}
</style>
