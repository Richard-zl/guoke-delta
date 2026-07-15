<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="account-page">
    <view v-for="a in accounts" :key="a.id" class="account-card">
      <view class="card-top">
        <image class="type-icon" :src="a.type === 'ALIPAY' ? '/static/icons/钞票.svg' : a.type === 'WECHAT' ? '/static/icons/钞票.svg' : '/static/icons/理财.svg'" mode="aspectFit" />
        <text class="type-name">{{ a.type === 'ALIPAY' ? '支付宝' : a.type === 'WECHAT' ? '微信' : '银行卡' }}</text>
        <view v-if="a.isDefault" class="default-tag">默认</view>
      </view>
      <text class="account-no">{{ a.account }}</text>
      <text v-if="a.realName" class="real-name">{{ a.realName }}</text>
      <view class="card-actions">
        <text class="action-btn" @click="goEdit(a)">编辑</text>
        <text class="action-btn danger" @click="doDelete(a.id)">删除</text>
      </view>
    </view>
    <EmptyState v-if="accounts.length === 0" text="暂无收款账户" image="/pages-player/static/icons/暂无地址.svg" button-text="添加账户" @action="goAdd" />
    <view class="add-btn" @click="goAdd">+ 添加账户</view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getAccountList, deleteAccount } from '@/api/player'

const accounts = ref([])

onShow(async () => {
  try { const res = await getAccountList(); accounts.value = res.data || [] } catch (e) {}
})

function goAdd() { uni.navigateTo({ url: '/pages-player/account/edit' }) }
function goEdit(a) { uni.navigateTo({ url: '/pages-player/account/edit?id=' + a.id + '&data=' + encodeURIComponent(JSON.stringify(a)) }) }
function doDelete(id) {
  uni.showModal({ title: '提示', content: '确认删除该账户吗', success: async (r) => {
    if (r.confirm) {
      try { await deleteAccount(id); uni.showToast({ title: '已删除' }); accounts.value = accounts.value.filter(a => a.id !== id) } catch (e) {}
    }
  }})
}
</script>
<style lang="scss" scoped>
.account-page { background: #ffffff; min-height: 100vh; padding: 24rpx; }
.account-card { background: #f1f5f9; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx;
  .card-top { display: flex; align-items: center; gap: 8rpx; margin-bottom: 12rpx;
    .type-icon { width: 36rpx; height: 36rpx; } .type-name { font-size: 28rpx; font-weight: bold; }
    .default-tag { font-size: 20rpx; color: #ff4544; background: #f0f5ff; padding: 2rpx 12rpx; border-radius: 4rpx; margin-left: auto; }
  }
  .account-no { font-size: 32rpx; color: #1e293b; letter-spacing: 2rpx; display: block; }
  .real-name { font-size: 24rpx; color: #94a3b8; display: block; margin-top: 4rpx; }
  .card-actions { display: flex; justify-content: flex-end; gap: 24rpx; margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx solid #f1f5f9;
    .action-btn { font-size: 26rpx; color: #ff4544; &.danger { color: #ee0a24; } }
  }
}
.add-btn { text-align: center; padding: 24rpx; background: #f1f5f9; border-radius: 12rpx; font-size: 28rpx; color: #ff4544; margin-top: 20rpx; }
</style>
