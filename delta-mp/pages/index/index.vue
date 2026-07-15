<template>
  <view class="home-page tab-page">
    <canvas type="2d" id="goldDust" class="gold-dust-canvas"></canvas>
    <scroll-view scroll-y class="home-scroll tab-page-scroll" :show-scrollbar="false">
      <!-- 搜索栏（审核期隐藏，避免进入商品列表） -->
      <view v-if="configLoaded && !isUnderReview" class="search-bar" @click="goSearch">
        <view class="search-inner">
          <text class="search-placeholder">搜索</text>
        </view>
      </view>
      <view v-if="configLoaded && !isUnderReview" class="search-bar-placeholder" />

      <!-- 轮播图（审核期同样使用后端配置） -->
      <swiper class="banner" :autoplay="true" :interval="4000" circular indicator-dots>
        <swiper-item v-for="item in banners" :key="item.id" @click="onBannerClick(item)">
          <image :src="item.imageUrl" mode="aspectFill" class="banner-img" />
        </swiper-item>
      </swiper>

      <!-- 公告栏 - 竖向滚动（审核期隐藏） -->
      <view v-if="configLoaded && !isUnderReview && notices.length" class="notice-bar" @click="showNoticeList = true">
        <view class="notice-label">
          <image class="notice-label-icon" src="/static/icons/公告.svg" mode="aspectFit" />
          <text class="notice-label-text">公告</text>
        </view>
        <swiper class="notice-swiper" vertical autoplay :interval="3500" :duration="500" circular :disable-touch="true">
          <swiper-item v-for="item in notices" :key="item.id">
            <view class="notice-swiper-item">
              <text class="notice-title">{{ item.title }}</text>
            </view>
          </swiper-item>
        </swiper>
        <text class="notice-more">{{ notices.length }}条 ›</text>
      </view>

      <!-- 公告列表弹窗（审核期隐藏） -->
      <view class="notice-list-mask" v-if="!isUnderReview && showNoticeList" @click="showNoticeList = false">
        <view class="notice-list-popup" @click.stop>
          <view class="notice-list-header">
            <text class="notice-list-title">平台公告</text>
            <text class="notice-list-close" @click="showNoticeList = false">✕</text>
          </view>
          <scroll-view scroll-y class="notice-list-body">
            <view
              v-for="item in notices"
              :key="item.id"
              class="notice-list-item"
              @click="openNoticeDetail(item); showNoticeList = false"
            >
              <view class="notice-list-dot"></view>
              <view class="notice-list-info">
                <text class="notice-list-name">{{ item.title }}</text>
                <text class="notice-list-time">{{ formatTime(item.createTime) }}</text>
              </view>
              <text class="notice-list-arrow">›</text>
            </view>
          </scroll-view>
        </view>
      </view>

      <!-- 商品分类（审核期隐藏） -->
      <view v-if="configLoaded && !isUnderReview" class="category-grid">
        <view
          v-for="cat in categories"
          :key="cat.id"
          class="category-item"
          @click="goCategory(cat.id)"
        >
          <image class="category-icon" :src="cat.icon || '/static/icons/分类.svg'" mode="aspectFill" lazy-load />
          <text class="category-name">{{ cat.name }}</text>
        </view>
      </view>

      <!-- 热门推荐商品（审核期隐藏） -->
      <view v-if="configLoaded && !isUnderReview" class="section">
        <view class="section-header">
          <scroll-view scroll-x class="recommend-tabs" :show-scrollbar="false" scroll-with-animation>
            <view
              v-for="tab in recommendTabs"
              :key="tab.id"
              class="recommend-tab"
              :class="{ active: currentRecommendCategoryId === tab.id }"
              @click="switchRecommendCategory(tab.id)"
            >
              {{ tab.name }}
            </view>
          </scroll-view>
        </view>
        <view class="product-grid">
          <ProductCard v-for="p in recommendProducts" :key="p.id" :product="p" />
        </view>
      </view>

      <!-- 审核期：资讯精选 -->
      <view v-if="configLoaded && isUnderReview" class="section audit-home-section">
        <view class="section-header audit-section-header">
          <text class="section-title">热门资讯</text>
          <text class="section-more" @click="goAuditCategory">更多 ›</text>
        </view>
        <view
          v-for="article in auditHomeArticles"
          :key="article.id"
          class="audit-home-card"
          @click="openAuditArticle(article)"
        >
          <image
            v-if="article.coverImage"
            class="audit-home-cover"
            :src="article.coverImage"
            mode="aspectFill"
            lazy-load
          />
          <view class="audit-home-info">
            <text class="audit-home-title">{{ article.title }}</text>
            <text class="audit-home-summary">{{ article.summary }}</text>
            <view class="audit-home-meta">
              <text class="audit-home-date">{{ formatAuditDate(article.publishDate) }}</text>
              <text v-if="article.tags?.[0]" class="audit-home-tag">{{ article.tags[0] }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="tab-page-bottom-spacer" />
    </scroll-view>

    <CustomTabBar :current="0" />

    <!-- 悬浮客服按钮 -->
    <view class="cs-float-btn" @click="goCustomerService">
      <image src="/static/icons/客服.svg" mode="aspectFit" class="cs-icon" />
    </view>

    <CsContactModal
      v-model:visible="csModal.visible"
      :title="csModal.title"
      :qrcode-url="csModal.qrcodeUrl"
      :tips="csModal.tips"
      :copy-text="csModal.copyText"
    />

    <!-- 公告弹窗 - 支持多公告叠加（审核期隐藏） -->
    <view class="notice-popup" v-if="!isUnderReview && showNoticePopup" @click="closeNoticePopup">
      <view class="popup-content" @click.stop>
        <view class="popup-header">
          <text class="popup-title">公告（{{ popupCurrentIndex + 1 }}/{{ popupNotices.length }}）</text>
          <text class="popup-close" @click="closeNoticePopup">✕</text>
        </view>
        <scroll-view scroll-y class="popup-body">
          <text class="popup-notice-title">{{ popupNotices[popupCurrentIndex]?.title }}</text>
          <rich-text v-if="popupNotices[popupCurrentIndex]?.content" :nodes="popupNotices[popupCurrentIndex].content" />
        </scroll-view>
        <view class="popup-footer">
          <view class="popup-nav" v-if="popupNotices.length > 1">
            <view
              class="popup-nav-btn"
              :class="{ disabled: popupCurrentIndex === 0 }"
              @click="popupCurrentIndex > 0 && popupCurrentIndex--"
            >上一条</view>
            <view
              class="popup-nav-btn"
              :class="{ disabled: popupCurrentIndex === popupNotices.length - 1 }"
              @click="popupCurrentIndex < popupNotices.length - 1 && popupCurrentIndex++"
            >下一条</view>
          </view>
          <view class="popup-btn" @click="closeNoticePopup">我知道了</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import ProductCard from '@/components/ProductCard.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { getRecommendProducts, getRecommendCategories } from '@/api/product'
import { getActiveBanners } from '@/api/banner'
import { getActiveNotices } from '@/api/notice'
import { getCategoryTree } from '@/api/category'
import { getRemind } from '@/api/message'
import CsContactModal from '@/components/CsContactModal.vue'
import { useWeworkCs } from '@/composables/useWeworkCs'
import { useUserStore } from '@/store/user'
import { useChatStore } from '@/store/chat'
import { useSiteStore } from '@/store/site'
import { useGoldDust } from '@/composables/useGoldDust'
import { useAuditMode } from '@/composables/useAuditMode'
import { openAuditArticleDetail, formatAuditDate } from '@/composables/useAuditArticle'
import { AUDIT_HOME_ARTICLES } from '@/constants/auditContent'

useGoldDust()
const { modalState: csModal, openWeworkCs } = useWeworkCs()
const userStore = useUserStore()
const siteStore = useSiteStore()
const { isUnderReview, configLoaded } = useAuditMode()
const auditHomeArticles = AUDIT_HOME_ARTICLES

// 分享给好友
onShareAppMessage(() => ({
  title: siteStore.siteName + ' - ' + siteStore.subtitle,
  path: '/pages/index/index'
}))
// 分享到朋友圈
onShareTimeline(() => ({
  title: siteStore.siteName + ' - ' + siteStore.subtitle
}))
const banners = ref([])
const categories = ref([])
const notices = ref([])
const recommendProducts = ref([])
/** 热门推荐 Tab：全部 + 接口返回的分类（护航专区推荐、热门推荐等） */
const recommendTabs = ref([])
const currentRecommendCategoryId = ref('')
const showNoticePopup = ref(false)
const popupNotices = ref([])
const popupCurrentIndex = ref(0)
const showNoticeList = ref(false)

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}-${d.getDate()}`
}

async function loadData() {
  if (!siteStore.configLoaded) await siteStore.fetchSiteConfig()
  const _t = Date.now()
  try {
    const opts = { loading: false }
    const baseTasks = [getActiveBanners(opts)]
    if (!isUnderReview.value) {
      baseTasks.push(getActiveNotices(opts), getCategoryTree(opts), getRecommendCategories(opts))
    }
    const results = await Promise.all(baseTasks)
    const bannerRes = results[0]
    const noticeRes = isUnderReview.value ? null : results[1]
    const catRes = isUnderReview.value ? null : results[2]
    const categoriesRes = isUnderReview.value ? null : results[3]

    // 轮播图
    if (bannerRes.data) {
      banners.value = bannerRes.data.records || bannerRes.data || []
    }
    // 公告（审核期不加载）
    if (!isUnderReview.value) {
      if (noticeRes?.data) {
        const list = noticeRes.data.records || noticeRes.data || []
        notices.value = list
        // 筛选出需要弹窗展示的公告
        const popupList = list.filter(n => n.popupDisplay === 1)
        if (popupList.length > 0) {
          const seenIds = uni.getStorageSync('seenNoticeIds') || []
          const unseenList = popupList.filter(n => !seenIds.includes(String(n.id)))
          if (unseenList.length > 0) {
            popupNotices.value = unseenList
            popupCurrentIndex.value = 0
            showNoticePopup.value = true
          }
        }
      }
    } else {
      notices.value = []
      showNoticePopup.value = false
      showNoticeList.value = false
    }
    if (!isUnderReview.value) {
      // 分类（树接口返回父级）
      if (catRes?.data) {
        categories.value = (catRes.data || []).slice(0, 8)
      }
      // 热门推荐分类 Tab
      if (categoriesRes?.data && Array.isArray(categoriesRes.data)) {
        recommendTabs.value = categoriesRes.data.map(c => ({ id: String(c.id), name: c.name }))
        if (recommendTabs.value.length > 0 && !currentRecommendCategoryId.value) {
          currentRecommendCategoryId.value = recommendTabs.value[0].id
        }
      }
      // 推荐商品
      const productParams = currentRecommendCategoryId.value ? { categoryId: currentRecommendCategoryId.value } : {}
      const productRes = await getRecommendProducts(productParams, opts)
      if (productRes.data) {
        recommendProducts.value = productRes.data.records || productRes.data || []
      }
    }
  } catch (e) {
    console.error('load home data error', e)
  }
  console.log(`[HOME] loadData done ${Date.now() - _t}ms`)
}

onShow(() => {
  loadData()
  if (useUserStore().token) {
    getRemind({ loading: false }).then(res => {
      const d = res.data || {}
      useChatStore().setUnreadFromServer(d.messageUnread ?? 0)
      useChatStore().setSystemUnreadFromServer(d.systemUnread ?? 0)
    }).catch(() => {})
  }
})

onPullDownRefresh(async () => {
  await loadData()
  uni.stopPullDownRefresh()
})

function switchRecommendCategory(id) {
  if (currentRecommendCategoryId.value === id) return
  currentRecommendCategoryId.value = id
  const params = id ? { categoryId: id } : {}
  getRecommendProducts(params, { loading: false }).then(res => {
    recommendProducts.value = res.data?.records || res.data || []
  }).catch(() => {})
}

function goSearch() {
  uni.navigateTo({ url: '/pages/product/list?focus=1' })
}

function onBannerClick(item) {
  if (isUnderReview.value) return
  if (item.linkType === 'product' && item.linkValue) {
    uni.navigateTo({ url: `/pages/product/detail?id=${item.linkValue}` })
  } else if (item.linkType === 'url' && item.linkValue) {
    // 外部链接可通过webview打开
  }
}

function openAuditArticle(article) {
  openAuditArticleDetail(article)
}

function goAuditCategory() {
  uni.switchTab({ url: '/pages/category/index' })
}

function goCategory(id) {
  if (id) uni.setStorageSync('selectedCategoryId', id)
  uni.switchTab({ url: '/pages/category/index' })
}

function openNoticeDetail(item) {
  popupNotices.value = [item]
  popupCurrentIndex.value = 0
  showNoticePopup.value = true
}

function closeNoticePopup() {
  showNoticePopup.value = false
  // 记录已看过的弹窗公告ID
  const seenIds = uni.getStorageSync('seenNoticeIds') || []
  popupNotices.value.forEach(n => {
    if (n.id && !seenIds.includes(String(n.id))) {
      seenIds.push(String(n.id))
    }
  })
  uni.setStorageSync('seenNoticeIds', seenIds)
}

function goCustomerService() {
  if (!userStore.checkLogin()) return
  openWeworkCs({ scene: 'general' })
}
</script>

<style lang="scss" scoped>
.home-page {
  background: #f1f5f9;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

/* === 内容层置于背景之上 === */
.search-bar, .banner, .notice-bar, .category-tags, .section, .notice-popup {
  position: relative;
  z-index: 1;
}

.search-bar {
  position: relative;
  z-index: 2;
  padding: 24rpx 24rpx 18rpx;
  background: transparent;

  .search-inner {
    height: 80rpx;
    background: #ffffff;
    border: 1rpx solid #e2e8f0;
    border-radius: 999rpx;
    display: flex;
    align-items: center;
    padding: 0 34rpx;
    box-shadow: 0 8rpx 28rpx rgba(15, 23, 42, 0.12);
  }
  .search-placeholder { font-size: 28rpx; color: #64748b; }
}
.search-bar-placeholder { display: none; }

.banner {
  height: 395rpx;
  margin: 0 24rpx;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.1);

  .banner-img { width: 100%; height: 100%; }
}

.notice-bar {
  display: flex;
  align-items: center;
  margin: 20rpx 24rpx;
  padding: 0 24rpx;
  height: 72rpx;
  background: #ffffff;
  border-radius: 12rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 20rpx rgba(255, 69, 68, 0.08);
}
.notice-label {
  display: flex;
  align-items: center;
  gap: 6rpx;
  flex-shrink: 0;
  margin-right: 16rpx;
  padding-right: 16rpx;
  border-right: 1rpx solid #e2e8f0;
  .notice-label-icon {
    width: 32rpx;
    height: 32rpx;
    filter: brightness(0) saturate(100%) invert(27%) sepia(51%) saturate(2878%) hue-rotate(346deg) brightness(104%) contrast(101%);
  }
  .notice-label-text { font-size: 24rpx; color: #ff4544; font-weight: 600; }
}
.notice-swiper {
  flex: 1;
  height: 72rpx;
}
.notice-swiper-item {
  display: flex;
  align-items: center;
  height: 72rpx;
}
.notice-title {
  font-size: 24rpx;
  color: #ff4544;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.notice-more {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #94a3b8;
  margin-left: 12rpx;
}
/* 公告列表弹窗 */
.notice-list-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 999;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.notice-list-popup {
  width: 100%;
  max-height: 70vh;
  background: #ffffff;
  border-radius: 32rpx 32rpx 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.notice-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 36rpx 24rpx;
  border-bottom: 1rpx solid #e2e8f0;
  .notice-list-title { font-size: 32rpx; font-weight: bold; color: #ff4544; }
  .notice-list-close { font-size: 36rpx; color: #94a3b8; padding: 0 8rpx; }
}
.notice-list-body {
  height: 50vh;
  padding: 12rpx 0;
}
.notice-list-item {
  display: flex;
  align-items: center;
  padding: 28rpx 36rpx;
  border-bottom: 1rpx solid #f1f5f9;
}
.notice-list-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #ff4544;
  flex-shrink: 0;
  margin-right: 20rpx;
}
.notice-list-info {
  flex: 1;
  overflow: hidden;
}
.notice-list-name {
  font-size: 28rpx;
  color: #1e293b;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.notice-list-time {
  font-size: 22rpx;
  color: #94a3b8;
  display: block;
  margin-top: 6rpx;
}
.notice-list-arrow {
  color: #cbd5e1;
  font-size: 26rpx;
  flex-shrink: 0;
  margin-left: 12rpx;
}

/* 商品分类：图标 + 文字网格 */
.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24rpx 16rpx;
  padding: 28rpx 24rpx;
  background: #ffffff;
  margin: 20rpx 24rpx;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(255, 69, 68, 0.08);
}
.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  &:active { opacity: 0.85; }
}
.category-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: #f1f5f9;
}
.category-name {
  font-size: 24rpx;
  color: #475569;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.section {
  padding: 24rpx;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 20rpx;
  .section-title {
    font-size: 32rpx;
    font-weight: bold;
    color: #ff4544;
    flex-shrink: 0;
  }
}
.recommend-tabs {
  flex: 1;
  white-space: nowrap;
  height: 56rpx;
}
.recommend-tab {
  display: inline-block;
  padding: 12rpx 24rpx;
  font-size: 24rpx;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 999rpx;
  margin-right: 16rpx;

  &.active {
    color: #fff;
    background: linear-gradient(135deg, #ff4544, #e63939);
  }
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20rpx;
  overflow: hidden;
}

.notice-popup {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;

  .popup-content {
    width: 600rpx;
    max-height: 70vh;
    background: #ffffff;
    border-radius: 24rpx;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.15);
  }

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 28rpx 32rpx;
    border-bottom: 1rpx solid #e2e8f0;

    .popup-title { font-size: 32rpx; font-weight: bold; color: #ff4544; }
    .popup-close { font-size: 36rpx; color: #94a3b8; padding: 0 8rpx; }
  }

  .popup-body {
    padding: 32rpx;
    max-height: 50vh;

    .popup-notice-title { font-size: 30rpx; font-weight: bold; color: #0f172a; display: block; margin-bottom: 20rpx; }
  }

  .popup-footer {
    padding: 24rpx 32rpx;
    border-top: 1rpx solid #e2e8f0;

    .popup-nav {
      display: flex;
      justify-content: center;
      gap: 32rpx;
      margin-bottom: 20rpx;

      .popup-nav-btn {
        font-size: 26rpx;
        color: #ff4544;
        padding: 8rpx 24rpx;
        border: 1rpx solid rgba(99, 102, 241, 0.15);
        border-radius: 999rpx;

        &.disabled {
          color: #cbd5e1;
          border-color: #e2e8f0;
        }
      }
    }

    .popup-btn {
      height: 76rpx;
      line-height: 76rpx;
      text-align: center;
      background: linear-gradient(135deg, #ff4544, #e63939);
      color: #ffffff;
      font-weight: bold;
      border-radius: 999rpx;
      font-size: 28rpx;
    }
  }
}
.audit-home-section {
  .audit-section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 20rpx;
  }
  .section-title {
    font-size: 32rpx;
    font-weight: bold;
    color: #ff4544;
  }
  .section-more {
    font-size: 24rpx;
    color: #94a3b8;
  }
}
.audit-home-card {
  display: flex;
  gap: 20rpx;
  padding: 20rpx;
  background: #ffffff;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(255, 69, 68, 0.08);
}
.audit-home-cover {
  width: 200rpx;
  height: 140rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  background: #e2e8f0;
}
.audit-home-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.audit-home-title {
  font-size: 28rpx;
  color: #1e293b;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}
.audit-home-summary {
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 8rpx;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  flex: 1;
}
.audit-home-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
}
.audit-home-date {
  font-size: 20rpx;
  color: #cbd5e1;
}
.audit-home-tag {
  font-size: 20rpx;
  color: #ff4544;
  padding: 2rpx 10rpx;
  background: rgba(255, 69, 68, 0.08);
  border-radius: 6rpx;
}

.cs-float-btn {
  position: fixed;
  right: 24rpx;
  bottom: 240rpx;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff4544, #e63939);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(255, 107, 43, 0.4);
  z-index: 100;

  .cs-icon {
    width: 52rpx;
    height: 52rpx;
    filter: brightness(0) saturate(100%) invert(100%);
  }
}
</style>
