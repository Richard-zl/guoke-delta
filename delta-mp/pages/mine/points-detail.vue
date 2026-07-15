<template>
  <view class="points-detail-page">
    <!-- 积分总览卡片 -->
    <view class="points-header">
      <text class="points-label">我的积分</text>
      <text class="points-value">{{ totalPoints }}</text>
      <text class="points-tip">消费1元=1积分</text>
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
    totalPoints.value = res.data?.totalPoints || res.data?.points || 0
    levelName.value = res.data?.levelName || '青铜伴星'
    
    // 计算下一等级
    const levels = [
      { min: 0, max: 999, name: '青铜伴星', desc: '0-999分' },
      { min: 1000, max: 4999, name: '白银伴星', desc: '每月2张9折券' },
      { min: 5000, max: 14999, name: '黄金伴星', desc: '每月4张9折券，优先派单权' },
      { min: 15000, max: 49999, name: '钻石伴星', desc: '每月6张9折券，每月1张8折券，优先派单权，专属VIP群' },
      { min: 50000, max: 999999, name: '王者伴星', desc: '专属客服，专属VIP群，每月8张9折券，每月2张8折券，新品内测资格，年度定制礼品' }
    ]
    
    let currentIdx = 0
    for (let i = 0; i < levels.length; i++) {
      if (totalPoints.value >= levels[i].min && totalPoints.value <= levels[i].max) {
        levelName.value = levels[i].name
        levelDesc.value = levels[i].desc
        currentIdx = i
        break
      }
    }
    
    if (currentIdx < levels.length - 1) {
      const next = levels[currentIdx + 1]
      nextLevelName.value = next.name
      nextLevelPoints.value = next.min - totalPoints.value
      const totalNeeded = next.min - levels[currentIdx].min
      const currentProgress = totalPoints.value - levels[currentIdx].min
      progressPercent.value = (currentProgress / totalNeeded) * 100
    } else {
      nextLevelPoints.value = 0
      progressPercent.value = 100
    }
  } catch (e) {
    console.error('获取积分汇总失败', e)
  }
}

async function refreshRecords() {
  pageNum.value = 1
  records.value = []
  finished.value = false
  await loadRecords()
}

async function loadRecords() {
  if (loading.value || finished.value) return
  loading.value = true
  try {
    const res = await getPointsDetail({
      pageNum: pageNum.value,
      pageSize: 20
    })
    const list = res.data?.records || []
    if (list.length < 20) finished.value = true
    records.value = pageNum.value === 1 ? list : [...records.value, ...list]
  } catch (e) {
    console.error('获取积分明细失败', e)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  if (finished.value || loading.value) return
  loadingMore.value = true
  pageNum.value++
  loadRecords()
}

function getTypeName(type) {
  const typeMap = {
    'ORDER': '消费获得',
    'ORDER_CONSUME': '消费获得',
    'EARN': '活动获得',
    'ADMIN': '管理员调整',
    'ADMIN_ADD': '管理员增加',
    'ADMIN_DEDUCT': '管理员扣减',
    'DEDUCT': '积分扣除'
  }
  return typeMap[type] || type || '积分变动'
}
</script>

<style lang="scss" scoped>
.points-detail-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.points-header {
  background: linear-gradient(135deg, #ff4544, #e63939);
  padding: 48rpx 32rpx;
  text-align: center;
  
  .points-label {
    font-size: 28rpx;
    color: rgba(255,255,255,0.8);
    display: block;
  }
  
  .points-value {
    font-size: 80rpx;
    font-weight: bold;
    color: #fff;
    display: block;
    margin: 16rpx 0;
  }
  
  .points-tip {
    font-size: 24rpx;
    color: rgba(255,255,255,0.7);
    display: block;
  }
}

.level-card {
  background: #fff;
  margin: 24rpx;
  padding: 32rpx;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.04);
  
  .level-info {
    margin-bottom: 24rpx;
    
    .level-name {
      font-size: 36rpx;
      font-weight: bold;
      color: #ff4544;
      display: block;
    }
    
    .level-desc {
      font-size: 24rpx;
      color: #94a3b8;
      margin-top: 8rpx;
      display: block;
    }
  }
  
  .level-progress {
    .progress-bar {
      height: 12rpx;
      background: #f1f5f9;
      border-radius: 6rpx;
      overflow: hidden;
      
      .progress-fill {
        height: 100%;
        background: linear-gradient(90deg, #ff4544, #f97316);
        border-radius: 6rpx;
      }
    }
    
    .progress-text {
      font-size: 22rpx;
      color: #94a3b8;
      margin-top: 12rpx;
      display: block;
    }
  }
}

.records-section {
  margin: 24rpx;
  
  .section-title {
    font-size: 28rpx;
    font-weight: bold;
    color: #1e293b;
    margin-bottom: 16rpx;
  }
  
  .records-scroll {
    max-height: 600rpx;
  }
}

.record-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 12rpx;
  
  .record-info {
    flex: 1;
    
    .record-title {
      font-size: 28rpx;
      color: #1e293b;
      display: block;
    }
    
    .record-time {
      font-size: 22rpx;
      color: #94a3b8;
      margin-top: 8rpx;
      display: block;
    }
  }
  
  .record-points {
    font-size: 32rpx;
    font-weight: bold;
    
    &.plus {
      color: #ff4544;
    }
    
    &.minus {
      color: #94a3b8;
    }
  }
}

.loading-more, .no-more {
  text-align: center;
  padding: 24rpx;
  font-size: 24rpx;
  color: #94a3b8;
}
</style>