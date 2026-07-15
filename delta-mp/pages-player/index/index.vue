<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="player-home tab-page">
    <scroll-view scroll-y class="player-home-scroll tab-page-scroll" :show-scrollbar="false">
      <view class="stat-cards">
        <view class="card"><text class="num">{{ homeData.todayCompleted || 0 }}</text><text class="label">今日订单</text></view>
        <view class="card"><text class="num">￥{{ homeData.todayIncome || '0.00' }}</text><text class="label">今日收入</text></view>
        <view class="card"><text class="num">￥{{ homeData.wallet?.totalIncome || '0.00' }}</text><text class="label">累计收入</text></view>
      </view>
      <view class="quick-actions">
        <view class="action" @click="go('/pages-player/hall/index')">
          <image class="action-icon" src="/pages-player/static/icons/店铺.svg" mode="aspectFit" />
          <text>接单大厅</text>
          <view v-if="homeData.pendingCount" class="pending-badge">{{ homeData.pendingCount }}</view>
        </view>
        <view class="action" @click="go('/pages-player/order/list')"><image class="action-icon" src="/static/icons/订单.svg" mode="aspectFit" /><text>我的订单</text></view>
        <view class="action" @click="go('/pages-player/earnings/index')"><image class="action-icon" src="/static/icons/钞票.svg" mode="aspectFit" /><text>我的收益</text></view>
        <view class="action" @click="go('/pages-player/withdraw/index')"><image class="action-icon" src="/static/icons/理财.svg" mode="aspectFit" /><text>提现</text></view>
      </view>
      <!-- 待处理订单预览 -->
      <view class="section" v-if="recentOrders.length">
        <view class="section-header">
          <text class="section-title">待处理订单</text>
          <text class="more" @click="go('/pages-player/order/list')">查看全部 ›</text>
        </view>
        <OrderCard v-for="o in recentOrders" :key="o.id" :order="o" detail-path="/pages-player/order/detail" />
      </view>
      <view class="tab-page-bottom-spacer" />
    </scroll-view>
    <CustomTabBar :current="0" />
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import OrderCard from '@/components/OrderCard.vue'
import { getPlayerHome, getMyWork } from '@/api/player'

const homeData = ref({})
const recentOrders = ref([])

onShow(async () => {
  try { const res = await getPlayerHome(); homeData.value = res.data || {} } catch (e) {}
  try {
    const res = await getMyWork({ pageNum: 1, pageSize: 3, status: 'ASSIGNED' })
    recentOrders.value = res.data?.records || []
  } catch (e) {}
})

function go(url) { uni.navigateTo({ url }) }
</script>
<style lang="scss" scoped>
.player-home { background: #ffffff; position: relative; }
.stat-cards { display: flex; gap: 20rpx; padding: 24rpx;
  .card { flex: 1; background: rgba(0,0,0,0.04); padding: 24rpx; border-radius: 12rpx; text-align: center;
    .num { font-size: 36rpx; font-weight: bold; color: #ff4544; display: block; }
    .label { font-size: 24rpx; color: rgba(0,0,0,0.3); margin-top: 8rpx; display: block; }
  }
}
.quick-actions { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20rpx; padding: 0 24rpx;
  .action { background: rgba(0,0,0,0.04); padding: 24rpx 0; text-align: center; border-radius: 12rpx; font-size: 24rpx; color: rgba(0,0,0,0.85); position: relative; display: flex; flex-direction: column; align-items: center; gap: 8rpx; .action-icon { width: 48rpx; height: 48rpx; } }
  .pending-badge { position: absolute; top: 8rpx; right: 8rpx; min-width: 32rpx; height: 32rpx; line-height: 32rpx; padding: 0 8rpx; font-size: 20rpx; color: #fff; background: #ee0a24; border-radius: 32rpx; text-align: center; }
}
.section { padding: 24rpx; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; .section-title { font-size: 28rpx; font-weight: bold; } .more { font-size: 24rpx; color: rgba(0,0,0,0.3); } }
</style>
