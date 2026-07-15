<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="cs-dashboard tab-page">
    <scroll-view scroll-y class="dashboard-scroll tab-page-scroll" :show-scrollbar="false">
      <!-- 待办提醒 -->
      <view class="section">
        <view class="stat-cards">
          <view class="card alert" @click="go('/pages-cs/order/list')">
            <text class="num">{{data.pendingAssign||0}}</text>
            <text class="label">待指派订单</text>
          </view>
          <view class="card alert" @click="go('/pages-cs/complaint/list')">
            <text class="num">{{data.pendingComplaints||0}}</text>
            <text class="label">待处理投诉</text>
          </view>
        </view>
        <view class="stat-cards">
          <view class="card">
            <text class="num">{{data.inProgress||0}}</text>
            <text class="label">进行中订单</text>
          </view>
          <view class="card">
            <text class="num">{{data.processingComplaints||0}}</text>
            <text class="label">仲裁处理中</text>
          </view>
          <view class="card">
            <text class="num">{{data.pendingChatSessions||0}}</text>
            <text class="label">待回复会话</text>
          </view>
        </view>
      </view>

      <!-- 今日数据 -->
      <view class="section">
        <text class="section-title">今日数据</text>
        <view class="stat-cards">
          <view class="card">
            <text class="num">{{data.todayOrders||0}}</text>
            <text class="label">新增订单</text>
          </view>
          <view class="card">
            <text class="num">{{data.todayCompleted||0}}</text>
            <text class="label">完成订单</text>
          </view>
          <view class="card">
            <text class="num gold">¥{{formatAmount(data.todayAmount)}}</text>
            <text class="label">成交金额</text>
          </view>
        </view>
      </view>

      <!-- 平台概况 -->
      <view class="section">
        <text class="section-title">平台概况</text>
        <view class="stat-cards">
          <view class="card">
            <text class="num">{{data.totalUsers||0}}</text>
            <text class="label">总用户数</text>
          </view>
          <view class="card">
            <text class="num">{{data.totalPlayers||0}}</text>
            <text class="label">总接单员数</text>
          </view>
          <view class="card">
            <text class="num">{{data.activePlayers||0}}</text>
            <text class="label">在线接单员</text>
          </view>
        </view>
      </view>

      <!-- 待指派订单预览 -->
      <view class="section" v-if="pendingOrders.length > 0">
        <view class="section-header">
          <text class="section-title">待指派订单</text>
          <text class="section-more" @click="go('/pages-cs/order/list')">查看全部 ›</text>
        </view>
        <view class="order-list">
          <view class="order-item" v-for="o in pendingOrders" :key="o.id" @click="go('/pages-cs/order/detail?id=' + o.id)">
            <view class="order-top">
              <text class="order-name">{{o.productName}}</text>
              <text class="order-amount">¥{{o.amount}}</text>
            </view>
            <view class="order-bottom">
              <text class="order-no">{{o.orderNo}}</text>
              <text class="order-time">{{formatTime(o.createdAt)}}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 快捷入口 -->
      <view class="section">
        <text class="section-title">快捷入口</text>
        <view class="quick-actions">
          <view class="action" @click="go('/pages-cs/order/list')">
            <image class="action-icon" src="/static/icons/订单.svg" mode="aspectFit" />
            <text>订单管理</text>
          </view>
          <view class="action" @click="go('/pages-cs/complaint/list')">
            <image class="action-icon" src="/pages-cs/static/icons/警示.svg" mode="aspectFit" />
            <text>投诉管理</text>
          </view>
          <view class="action" @click="go('/pages-cs/player/list')">
            <image class="action-icon" src="/pages-cs/static/icons/会员.svg" mode="aspectFit" />
            <text>接单员管理</text>
          </view>
          <view class="action" @click="go('/pages-cs/relay/list')">
            <image class="action-icon" src="/pages-cs/static/icons/会员.svg" mode="aspectFit" />
            <text>接力申请</text>
            <view v-if="remindStore.relayUnread > 0" class="action-badge">
              {{ remindStore.relayUnread > 99 ? '99+' : remindStore.relayUnread }}
            </view>
          </view>
          <view class="action" @click="go('/pages-cs/replace/list')">
            <image class="action-icon" src="/pages-cs/static/icons/会员.svg" mode="aspectFit" />
            <text>换人审核</text>
            <view v-if="remindStore.replaceUnread > 0" class="action-badge">
              {{ remindStore.replaceUnread > 99 ? '99+' : remindStore.replaceUnread }}
            </view>
          </view>
        </view>
      </view>

      <view class="tab-page-bottom-spacer" />
    </scroll-view>
    <CustomTabBar :current="0" />
  </view>

</template>

<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { getDashboard } from '@/pages-cs/api/cs'
import { useRemindStore } from '@/store/remind'
const { pageBlocked } = useAuditPageGuard()

const data = ref({})
const remindStore = useRemindStore()
const pendingOrders = computed(() => {
  const list = data.value.pendingOrders
  return Array.isArray(list) ? list : []
})

onShow(async () => {
  try {
    const res = await getDashboard()
    data.value = res.data || {}
  } catch (e) { /* ignore */ }
  remindStore.fetchCsRemind()
})

function go(url) { uni.navigateTo({ url }) }

function formatAmount(v) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(5, 16)
}
</script>

<style lang="scss" scoped>
.cs-dashboard {
  background: #ffffff;
  position: relative;
}

.dashboard-scroll {
  padding-top: 20rpx;
}

.section {
  padding: 0 24rpx;
  margin-bottom: 24rpx;
}

.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: bold;
  color: rgba(0, 0, 0, 0.7);
  margin-bottom: 16rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.section-more {
  font-size: 24rpx;
  color: #ff4544;
}

.stat-cards {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;

  .card {
    flex: 1;
    background: rgba(0, 0, 0, 0.04);
    padding: 20rpx 12rpx;
    border-radius: 12rpx;
    text-align: center;

    &.alert .num {
      color: #ee6723;
    }

    .num {
      font-size: 40rpx;
      font-weight: bold;
      color: rgba(0, 0, 0, 0.85);
      display: block;
      line-height: 1.2;

      &.gold {
        color: #ff4544;
        font-size: 32rpx;
      }
    }

    .label {
      display: block;
      font-size: 22rpx;
      color: rgba(0, 0, 0, 0.3);
      margin-top: 8rpx;
    }
  }
}

.order-list {
  .order-item {
    background: rgba(0, 0, 0, 0.04);
    border-radius: 12rpx;
    padding: 20rpx 24rpx;
    margin-bottom: 12rpx;
  }

  .order-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8rpx;
  }

  .order-name {
    font-size: 28rpx;
    color: rgba(0, 0, 0, 0.85);
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .order-amount {
    font-size: 28rpx;
    font-weight: bold;
    color: #ff4544;
    margin-left: 16rpx;
  }

  .order-bottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .order-no {
    font-size: 22rpx;
    color: rgba(0, 0, 0, 0.3);
  }

  .order-time {
    font-size: 22rpx;
    color: rgba(0, 0, 0, 0.3);
  }
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;

  .action {
    background: rgba(0, 0, 0, 0.04);
    padding: 24rpx 0;
    text-align: center;
    border-radius: 12rpx;
    font-size: 24rpx;
    color: rgba(0, 0, 0, 0.85);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;
    position: relative;

    .action-icon {
      width: 48rpx;
      height: 48rpx;
    }

    .action-badge {
      position: absolute;
      top: 10rpx;
      right: 10rpx;
      min-width: 32rpx;
      height: 32rpx;
      line-height: 32rpx;
      padding: 0 8rpx;
      font-size: 20rpx;
      color: #ffffff;
      background: #ee0a24;
      border-radius: 32rpx;
      text-align: center;
    }
  }
}
</style>
