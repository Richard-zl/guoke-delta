<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="cs-mine tab-page">
    <scroll-view scroll-y class="mine-scroll tab-page-scroll" :show-scrollbar="false">
      <!-- 顶部信息卡片 - 可点击编辑资料 -->
      <view class="user-header" @click="goProfileEdit">
        <image class="avatar" :src="csInfo.avatar || '/static/images/default-avatar.png'" mode="aspectFill" />
        <view class="info">
          <text class="name">{{ csInfo.nickname || '客服工作台' }}</text>
          <text class="desc">管理订单 · 处理投诉 · 服务用户</text>
        </view>
        <text class="arrow">›</text>
      </view>

      <!-- 订单服务 -->
      <view class="menu-list">
        <view class="menu-item" @click="go('/pages-cs/order/list')">
          <image class="menu-icon" src="/static/icons/订单.svg" mode="aspectFit" />
          <text>订单管理</text><text class="arrow">›</text>
        </view>
        <view class="menu-item" @click="go('/pages-cs/complaint/list')">
          <image class="menu-icon" src="/static/icons/提示.svg" mode="aspectFit" />
          <text>投诉管理</text><text class="arrow">›</text>
        </view>
        <view class="menu-item" @click="go('/pages-cs/quick-reply/index')">
          <image class="menu-icon" src="/static/icons/提示.svg" mode="aspectFit" />
          <text>快捷发言</text><text class="arrow">›</text>
        </view>
        <view class="menu-item" @click="go('/pages/message/index')">
          <image class="menu-icon" src="/static/icons/提示.svg" mode="aspectFit" />
          <text>系统通知</text>
          <view v-if="remindStore.systemUnread > 0" class="badge">{{ remindStore.systemUnread > 99 ? '99+' : remindStore.systemUnread }}</view>
          <text class="arrow">›</text>
        </view>
      </view>

      <!-- 用户管理 -->
      <view class="menu-list">
        <view class="menu-item" @click="go('/pages-cs/user/list')">
          <image class="menu-icon" src="/static/icons/会员.svg" mode="aspectFit" />
          <text>用户管理</text><text class="arrow">›</text>
        </view>
        <view class="menu-item" @click="go('/pages-cs/player/list')">
          <image class="menu-icon" src="/static/icons/会员.svg" mode="aspectFit" />
          <text>接单员管理</text><text class="arrow">›</text>
        </view>
      </view>

      <view class="logout" @click="doLogout">退出登录</view>
      <view class="tab-page-bottom-spacer" />
    </scroll-view>
    <CustomTabBar :current="4" />
  </view>

</template>

<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useAppStore } from '@/store/app'
import { useChatStore } from '@/store/chat'
import { useRemindStore } from '@/store/remind'
import { getCsInfo, removeCsToken } from '@/utils/auth'
const { pageBlocked } = useAuditPageGuard()

const appStore = useAppStore()
const remindStore = useRemindStore()
const csInfo = computed(() => getCsInfo() || {})

onShow(() => { remindStore.fetchCsRemind() })

function go(url) { uni.navigateTo({ url }) }
function goProfileEdit() { uni.navigateTo({ url: '/pages-cs/profile/edit' }) }

function doLogout() {
  uni.showModal({
    title: '确认退出',
    content: '确定退出客服登录吗？',
    success(r) {
      if (r.confirm) {
        useChatStore().disconnect()
        removeCsToken()
        appStore.switchToUser()
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.cs-mine {
  background: #f1f5f9;
  height: 100vh;
  position: relative;
  overflow: hidden;
}
.mine-scroll {
  height: calc(100vh - 110rpx - env(safe-area-inset-bottom));
  position: relative;
  z-index: 1;
}
.user-header {
  display: flex;
  align-items: center;
  padding: 48rpx 32rpx;
  margin: 20rpx 24rpx 0;
  background: #ffffff;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(255, 69, 68, 0.08);
  position: relative;
  z-index: 1;
  .avatar { width: 120rpx; height: 120rpx; border-radius: 50%; margin-right: 24rpx; border: 3rpx solid rgba(255, 69, 68, 0.15); flex-shrink: 0; }
  .info { flex: 1; overflow: hidden; }
  .name { font-size: 34rpx; font-weight: bold; color: #0f172a; display: block; }
  .desc { font-size: 24rpx; color: #94a3b8; display: block; margin-top: 8rpx; }
  > .arrow { color: #cbd5e1; font-size: 28rpx; flex-shrink: 0; margin-left: 12rpx; }
}
.menu-list {
  margin: 20rpx 24rpx 0;
  background: #ffffff;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(255, 69, 68, 0.08);
  overflow: hidden;
  position: relative;
  z-index: 1;
}
.menu-item {
  display: flex;
  align-items: center;
  padding: 32rpx 24rpx;
  border-bottom: 1rpx solid #f1f5f9;
  font-size: 28rpx;
  color: #334155;
  .menu-icon { width: 40rpx; height: 40rpx; margin-right: 16rpx; flex-shrink: 0; }
  text { flex: 1; }
  .badge {
    min-width: 32rpx; height: 32rpx; line-height: 32rpx; padding: 0 8rpx;
    font-size: 20rpx; color: #fff; background: #ee0a24; border-radius: 32rpx;
    text-align: center; margin-right: 8rpx; flex-shrink: 0;
  }
  .arrow { color: #cbd5e1; flex: none; }
  &:last-child { border-bottom: none; }
}
.logout {
  margin: 40rpx 24rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: #ffffff;
  color: #ee5555;
  font-size: 30rpx;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(238, 10, 36, 0.08);
  position: relative;
  z-index: 1;
}
</style>
