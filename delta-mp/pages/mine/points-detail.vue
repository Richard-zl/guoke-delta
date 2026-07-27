<template>
  <view class="points-detail-page">
    <!-- 积分总览 -->
    <view class="points-header">
      <text class="points-label">当前积分</text>
      <text class="points-value">{{ currentPoints }}</text>
      <text class="points-sub">总积分 {{ totalPoints }}（用于会员等级）</text>
    </view>

    <!-- 等级卡片 -->
    <view class="level-card">
      <view class="level-info">
        <text class="level-name">{{ levelName }}</text>
        <text class="level-desc">{{ levelDesc }}</text>
      </view>
      <view class="level-progress" v-if="nextLevelPoints > 0">
        <view class="progress-bar">
          <view class="progress-fill" :style="{ width: progressPercent + '%' }"></view>
        </view>
        <text class="progress-text">距{{ nextLevelName }}还差{{ nextLevelPoints }}积分</text>
      </view>
    </view>

    <!-- 积分明细列表 -->
    <view class="records-section">
      <view class="section-title">积分明细</view>
      <scroll-view scroll-y class="records-scroll" @scrolltolower="loadMore">
        <view v-for="record in records" :key="record.id" class="record-item">
          <view class="record-info">
            <text class="record-title">{{ record.remark || getTypeName(record.type) }}</text>
            <text class="record-time">{{ record.createdAt }}</text>
          </view>
          <text class="record-points" :class="{ plus: record.points > 0, minus: record.points < 0 }">
            {{ record.points > 0 ? '+' : '' }}{{ record.points }}
          </text>
        </view>

        <EmptyState v-if="!loading && records.length === 0" text="暂无积分记录" />

        <view class="loading-more" v-if="loadingMore">加载中...</view>
        <view class="no-more" v-if="finished && records.length > 0">没有更多了</view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getPointsDetail, getUserInfo } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import { resolveMemberLevel, getMemberProgress } from '@/utils/memberLevel'

const currentPoints = ref(0)
const totalPoints = ref(0)
const levelName = ref('青铜伴星')
const levelDesc = ref('')
const nextLevelName = ref('')
const nextLevelPoints = ref(0)
const progressPercent = ref(0)
const records = ref([])
const pageNum = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)

onShow(() => {
  loadPointsSummary()
  refreshRecords()
})

async function loadPointsSummary() {
  try {
    const res = await getUserInfo()
    currentPoints.value = res.data?.points || 0
    totalPoints.value = res.data?.totalPoints || 0
    const level = resolveMemberLevel(totalPoints.value, res.data?.levelCode)
    levelName.value = level.name
    levelDesc.value = level.desc
    const progress = getMemberProgress(totalPoints.value)
    progressPercent.value = progress.percent
    nextLevelName.value = progress.nextName
    nextLevelPoints.value = progress.remainPoints
  } catch (e) {
    console.error(e)
  }
}

function getTypeName(type) {
  const map = {
    ORDER_CONSUME: '订单消费',
    RECHARGE: '储值赠送',
    ADMIN_ADD: '系统增加',
    ADMIN_DEDUCT: '系统扣减',
    TOTAL_ADMIN_ADD: '总积分增加',
    TOTAL_ADMIN_DEDUCT: '总积分扣减'
  }
  return map[type] || type || '积分变动'
}

async function refreshRecords() {
  pageNum.value = 1
  finished.value = false
  records.value = []
  await loadRecords()
}

async function loadRecords() {
  if (loading.value || loadingMore.value || finished.value) return
  const isFirst = pageNum.value === 1
  if (isFirst) loading.value = true
  else loadingMore.value = true
  try {
    const res = await getPointsDetail({ pageNum: pageNum.value, pageSize: 20 })
    const list = res.data?.records || []
    records.value = isFirst ? list : records.value.concat(list)
    if (list.length < 20) finished.value = true
    else pageNum.value += 1
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  loadRecords()
}
</script>

<style lang="scss" scoped>
.points-detail-page {
  min-height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}
.points-header {
  background: linear-gradient(135deg, #2c2c2c, #1a1a1a);
  padding: 48rpx 40rpx 56rpx;
  .points-label {
    color: rgba(255, 255, 255, 0.7);
    font-size: 28rpx;
  }
  .points-value {
    display: block;
    color: #fff;
    font-size: 72rpx;
    font-weight: 700;
    margin-top: 12rpx;
  }
  .points-sub {
    display: block;
    color: rgba(255, 255, 255, 0.55);
    font-size: 24rpx;
    margin-top: 12rpx;
  }
}
.level-card {
  margin: -24rpx 24rpx 0;
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  .level-name {
    font-size: 32rpx;
    font-weight: 600;
    color: #222;
  }
  .level-desc {
    display: block;
    font-size: 24rpx;
    color: #888;
    margin-top: 8rpx;
  }
  .level-progress {
    margin-top: 24rpx;
    .progress-bar {
      height: 12rpx;
      background: #eee;
      border-radius: 6rpx;
      overflow: hidden;
      .progress-fill {
        height: 100%;
        background: #ff4544;
        border-radius: 6rpx;
      }
    }
    .progress-text {
      display: block;
      font-size: 22rpx;
      color: #999;
      margin-top: 12rpx;
    }
  }
}
.records-section {
  flex: 1;
  margin-top: 24rpx;
  background: #fff;
  padding: 0 24rpx;
  .section-title {
    font-size: 30rpx;
    font-weight: 600;
    padding: 28rpx 8rpx 16rpx;
  }
  .records-scroll {
    height: calc(100vh - 480rpx);
  }
  .record-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 28rpx 8rpx;
    border-bottom: 1rpx solid #f0f0f0;
    .record-title {
      font-size: 28rpx;
      color: #333;
    }
    .record-time {
      display: block;
      font-size: 22rpx;
      color: #999;
      margin-top: 8rpx;
    }
    .record-points {
      font-size: 32rpx;
      font-weight: 600;
      &.plus { color: #ff4544; }
      &.minus { color: #333; }
    }
  }
  .loading-more, .no-more {
    text-align: center;
    color: #999;
    font-size: 24rpx;
    padding: 24rpx;
  }
}
</style>
