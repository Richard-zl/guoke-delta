<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="player-mine tab-page">
    <scroll-view scroll-y class="mine-scroll tab-page-scroll" :show-scrollbar="false">
      <!-- 用户信息 -->
      <view class="user-header">
        <image class="avatar" :src="playerStore.playerInfo?.avatar||'/static/images/default-avatar.png'" mode="aspectFill" />
        <text class="name">{{playerStore.playerInfo?.nickname||'接单员'}}</text>
      </view>
      <!-- 在线状态 -->
      <view class="online-bar">
        <text class="online-label">接单状态</text>
        <view class="online-right">
          <text :class="['online-status-text', isOnline ? 'on' : 'off']">{{ isOnline ? '在线接单中' : '已离线' }}</text>
          <switch :checked="isOnline" color="#22c55e" @change="onToggleOnline" />
        </view>
      </view>
      <!-- 统计卡片 -->
      <view class="stat-cards">
        <view class="card"><text class="num">{{ homeData.todayCompleted || 0 }}</text><text class="label">今日订单</text></view>
        <view class="card"><text class="num">￥{{ homeData.todayIncome || '0.00' }}</text><text class="label">今日收入</text></view>
        <view class="card"><text class="num">￥{{ homeData.wallet?.totalIncome || '0.00' }}</text><text class="label">累计收入</text></view>
      </view>
      <!-- 快捷入口 -->
      <view class="quick-actions">
        <view class="action" @click="go('/pages-player/hall/index')">
          <image class="action-icon" src="/pages-player/static/icons/店铺.svg" mode="aspectFit" />
          <text>接单大厅</text>
        </view>
        <view class="action" @click="go('/pages-player/order/list')"><image class="action-icon" src="/static/icons/订单.svg" mode="aspectFit" /><text>我的订单</text></view>
        <view class="action" @click="go('/pages-player/earnings/index')"><image class="action-icon" src="/static/icons/钞票.svg" mode="aspectFit" /><text>我的收益</text></view>
        <view class="action" @click="go('/pages-player/withdraw/index')"><image class="action-icon" src="/static/icons/理财.svg" mode="aspectFit" /><text>提现</text></view>
      </view>
      <!-- 菜单 -->
      <view class="menu-list">
        <view class="menu-item" @click="go('/pages-player/withdraw/list')"><image class="menu-icon" src="/static/icons/暂无纪录.svg" mode="aspectFit" /><text>提现记录</text><text class="arrow">›</text></view>
        <view class="menu-item" @click="go('/pages-player/account/list')"><image class="menu-icon" src="/static/icons/理财.svg" mode="aspectFit" /><text>提现账户</text><text class="arrow">›</text></view>
        <view class="menu-item" @click="go('/pages-player/invite/list')">
          <image class="menu-icon" src="/pages-player/static/icons/分享.svg" mode="aspectFit" />
          <text>邀请列表</text>
          <view v-if="remindStore.inviteCount > 0" class="badge">{{ remindStore.inviteCount > 99 ? '99+' : remindStore.inviteCount }}</view>
          <text class="arrow">›</text>
        </view>
        <view class="menu-item" @click="go('/pages/message/index')">
          <image class="menu-icon" src="/static/icons/提示.svg" mode="aspectFit" />
          <text>系统通知</text>
          <view v-if="remindStore.systemUnread > 0" class="badge">{{ remindStore.systemUnread > 99 ? '99+' : remindStore.systemUnread }}</view>
          <text class="arrow">›</text>
        </view>
      </view>
      <!-- 待处理订单预览 -->
      <view class="section" v-if="recentOrders.length">
        <view class="section-header">
          <text class="section-title">待处理订单</text>
          <text class="more" @click="go('/pages-player/order/list')">查看全部 ›</text>
        </view>
        <OrderCard v-for="o in recentOrders" :key="o.id" :order="o" detail-path="/pages-player/order/detail" />
      </view>
      <view class="switch-btn" @click="backToUser">返回用户端</view>
      <view class="tab-page-bottom-spacer" />
    </scroll-view>
    <CustomTabBar :current="3" />
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import OrderCard from '@/components/OrderCard.vue'
import { usePlayerStore } from '@/store/player'
import { useAppStore } from '@/store/app'
import { useChatStore } from '@/store/chat'
import { useRemindStore } from '@/store/remind'
import { getPlayerHome, getMyOrders, toggleOnlineStatus } from '@/api/player'

const playerStore = usePlayerStore()
const appStore = useAppStore()
const remindStore = useRemindStore()
const homeData = ref({})
const recentOrders = ref([])

const isOnline = computed(() => playerStore.playerInfo?.isOnline === 1)

onShow(async () => {
  try { uni.hideHomeButton() } catch (_) {}
  playerStore.fetchProfile()
  remindStore.fetchPlayerRemind()
  try { const res = await getPlayerHome(); homeData.value = res.data || {} } catch (e) {}
  try {
    const res = await getMyOrders({ pageNum: 1, pageSize: 3, status: 'ASSIGNED' })
    recentOrders.value = res.data?.records || []
  } catch (e) {}
})

async function onToggleOnline(e) {
  const newVal = e.detail.value
  try {
    await toggleOnlineStatus(newVal)
    playerStore.fetchProfile()
    uni.showToast({ title: newVal ? '已上线' : '已下线', icon: 'none' })
  } catch (err) {
    uni.showToast({ title: '操作失败', icon: 'none' })
    playerStore.fetchProfile()
  }
}

function go(url) { uni.navigateTo({ url }) }
function backToUser() {
  useChatStore().disconnect()
  appStore.switchToUser()
}
</script>
<style lang="scss" scoped>
.player-mine { background: #ffffff; position: relative; }
.online-bar { display:flex; align-items:center; justify-content:space-between; padding:24rpx 32rpx; margin:0 24rpx; background:#f8fafc; border-radius:12rpx; .online-label { font-size:28rpx; color:#334155; font-weight:500; } .online-right { display:flex; align-items:center; gap:16rpx; } .online-status-text { font-size:26rpx; &.on { color:#22c55e; } &.off { color:#94a3b8; } } }
.user-header { display:flex; align-items:center; padding:48rpx 32rpx; background: #f1f5f9; .avatar { width:100rpx; height:100rpx; border-radius:50%; margin-right:24rpx; } .name { font-size:32rpx; font-weight:bold; } }
.stat-cards { display:flex; gap:20rpx; padding:24rpx;
  .card { flex:1; background: #f1f5f9; padding:24rpx; border-radius:12rpx; text-align:center;
    .num { font-size:36rpx; font-weight:bold; color: #ff4544; display:block; }
    .label { font-size:24rpx; color: #94a3b8; margin-top:8rpx; display:block; }
  }
}
.quick-actions { display:grid; grid-template-columns:repeat(4, 1fr); gap:20rpx; padding:0 24rpx;
  .action { background: #f1f5f9; padding:24rpx 0; text-align:center; border-radius:12rpx; font-size:24rpx; color: #1e293b; display:flex; flex-direction:column; align-items:center; gap:8rpx; .action-icon { width:48rpx; height:48rpx; } }
}
.menu-list { margin-top:20rpx; background: #f1f5f9; }
.menu-item { display:flex; align-items:center; padding:32rpx 24rpx; border-bottom:1rpx solid #f1f5f9; font-size:28rpx; .menu-icon { width:40rpx; height:40rpx; margin-right:16rpx; flex-shrink:0; } text:not(.arrow) { flex:1; } .badge { min-width:32rpx; height:32rpx; line-height:32rpx; padding:0 8rpx; font-size:20rpx; color:#fff; background:#ee0a24; border-radius:32rpx; text-align:center; margin-right:8rpx; flex-shrink:0; } .arrow { color: #cbd5e1; flex-shrink:0; margin-left:auto; } }
.section { padding:24rpx; }
.section-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16rpx; .section-title { font-size:28rpx; font-weight:bold; } .more { font-size:24rpx; color: #94a3b8; } }
.switch-btn { margin:40rpx 24rpx; height:88rpx; line-height:88rpx; text-align:center; background: #f1f5f9; color: #ff4544; font-size:30rpx; border-radius:12rpx; border:1rpx solid rgba(255,69,68,0.5); }
</style>