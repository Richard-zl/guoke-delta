<template>
  <view class="article-page">
    <scroll-view scroll-y class="article-scroll" :show-scrollbar="false">
      <!-- 封面 -->
      <view class="cover-wrap">
        <image
          v-if="article?.coverImage"
          class="cover-image"
          :src="article.coverImage"
          mode="aspectFill"
        />
        <view class="cover-mask" />
        <text v-if="categoryName" class="cover-tag">{{ categoryName }}</text>
      </view>

      <view v-if="article" class="article-body">
        <text class="article-title">{{ article.title }}</text>
        <view class="meta-row">
          <text class="meta-author">{{ article.author }}</text>
          <text class="meta-dot">·</text>
          <text class="meta-date">{{ article.publishDate }}</text>
        </view>
        <view v-if="article.tags?.length" class="tag-row">
          <text v-for="tag in article.tags" :key="tag" class="tag">{{ tag }}</text>
        </view>

        <!-- 正文块 -->
        <view class="content-blocks">
          <template v-for="(block, idx) in article.blocks" :key="idx">
            <text v-if="block.type === 'heading'" class="block-heading">{{ block.content }}</text>
            <text v-else-if="block.type === 'text'" class="block-text">{{ block.content }}</text>
            <view v-else-if="block.type === 'tip'" class="block-tip">
              <text class="tip-label">提示</text>
              <text class="tip-text">{{ block.content }}</text>
            </view>
            <view v-else-if="block.type === 'image'" class="block-image-wrap">
              <image
                v-if="block.url"
                class="block-image"
                :src="block.url"
                mode="widthFix"
                lazy-load
              />
              <text v-if="block.caption" class="image-caption">{{ block.caption }}</text>
            </view>
          </template>
        </view>

        <!-- 相关推荐 -->
        <view v-if="relatedList.length" class="related-section">
          <text class="related-title">相关推荐</text>
          <view
            v-for="item in relatedList"
            :key="item.id"
            class="related-card"
            @click="goArticle(item.id)"
          >
            <image
              v-if="item.coverImage"
              class="related-cover"
              :src="item.coverImage"
              mode="aspectFill"
              lazy-load
            />
            <view class="related-info">
              <text class="related-name">{{ item.title }}</text>
              <text class="related-summary">{{ item.summary }}</text>
            </view>
          </view>
        </view>
      </view>

      <view v-else class="empty-wrap">
        <text class="empty-text">资讯不存在或已下架</text>
      </view>

      <view class="page-bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import {
  getAuditArticleById,
  getRelatedAuditArticles,
  AUDIT_CATEGORY_MAP
} from '@/constants/auditContent'

const articleId = ref('')
const article = ref(null)

const categoryName = computed(() => {
  const id = article.value?.categoryId
  return id ? AUDIT_CATEGORY_MAP[id] || '' : ''
})

const relatedList = computed(() =>
  article.value ? getRelatedAuditArticles(article.value) : []
)

onLoad((query) => {
  articleId.value = query?.id || ''
  loadArticle(articleId.value)
})

onShareAppMessage(() => {
  const a = article.value
  return {
    title: a?.title || '游戏资讯',
    path: `/pages/audit/article-detail?id=${articleId.value}`
  }
})

function loadArticle(id) {
  const data = getAuditArticleById(id)
  article.value = data
  if (data?.title) {
    uni.setNavigationBarTitle({ title: '资讯详情' })
  }
}

function goArticle(id) {
  uni.redirectTo({ url: `/pages/audit/article-detail?id=${id}` })
}
</script>

<style lang="scss" scoped>
.article-page {
  min-height: 100vh;
  background: #f1f5f9;
}

.article-scroll {
  height: 100vh;
}

.cover-wrap {
  position: relative;
  width: 100%;
  height: 360rpx;
  background: #e2e8f0;
}

.cover-image {
  width: 100%;
  height: 100%;
}

.cover-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, transparent 40%, rgba(15, 23, 42, 0.35) 100%);
}

.cover-tag {
  position: absolute;
  left: 32rpx;
  bottom: 32rpx;
  padding: 8rpx 20rpx;
  font-size: 22rpx;
  color: #fff;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 999rpx;
  backdrop-filter: blur(8rpx);
}

.article-body {
  margin-top: -24rpx;
  padding: 32rpx 32rpx 0;
  background: #f1f5f9;
  border-radius: 24rpx 24rpx 0 0;
  position: relative;
  z-index: 1;
}

.article-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.45;
}

.meta-row {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  font-size: 24rpx;
  color: #94a3b8;
}

.meta-dot {
  margin: 0 12rpx;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 20rpx;
}

.tag {
  padding: 6rpx 16rpx;
  font-size: 22rpx;
  color: #ff4544;
  background: rgba(255, 69, 68, 0.08);
  border-radius: 8rpx;
}

.content-blocks {
  margin-top: 32rpx;
}

.block-heading {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #1e293b;
  margin: 36rpx 0 16rpx;
  line-height: 1.4;
}

.block-text {
  display: block;
  font-size: 28rpx;
  color: #475569;
  line-height: 1.85;
  margin-bottom: 20rpx;
  text-align: justify;
}

.block-tip {
  display: flex;
  gap: 16rpx;
  padding: 24rpx;
  margin: 24rpx 0;
  background: #fffbeb;
  border-left: 6rpx solid #f59e0b;
  border-radius: 12rpx;
}

.tip-label {
  flex-shrink: 0;
  font-size: 22rpx;
  font-weight: 600;
  color: #d97706;
}

.tip-text {
  flex: 1;
  font-size: 26rpx;
  color: #92400e;
  line-height: 1.7;
}

.block-image-wrap {
  margin: 28rpx 0;
}

.block-image {
  width: 100%;
  border-radius: 16rpx;
  display: block;
  background: #e2e8f0;
}

.image-caption {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #94a3b8;
  text-align: center;
}

.related-section {
  margin-top: 48rpx;
  padding-top: 32rpx;
  border-top: 1rpx solid #e2e8f0;
}

.related-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 20rpx;
}

.related-card {
  display: flex;
  gap: 20rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(99, 102, 241, 0.06);
}

.related-cover {
  width: 160rpx;
  height: 120rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  background: #e2e8f0;
}

.related-info {
  flex: 1;
  min-width: 0;
}

.related-name {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-summary {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.empty-wrap {
  padding: 120rpx 32rpx;
  text-align: center;
}

.empty-text {
  font-size: 28rpx;
  color: #94a3b8;
}

.page-bottom-spacer {
  height: 48rpx;
}
</style>
