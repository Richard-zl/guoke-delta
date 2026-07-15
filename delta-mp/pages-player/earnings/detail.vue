<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="detail-page">
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="item in records" :key="item.id" class="record-item">
        <view class="left">
          <text class="desc">{{ item.type === 'INCOME' ? '订单收益' : TX_TYPE_MAP[item.type] || item.type }}</text>
          <text class="time">{{ item.createdAt }}</text>
          <view v-if="item.remarkInfo" class="remark-info">
            <text>订单金额: ¥{{ item.remarkInfo.orderAmount }}</text>
            <text>抽成: {{ item.remarkInfo.commissionRate }}% (¥{{ item.remarkInfo.commissionAmount }})</text>
          </view>
        </view>
        <text class="amount" :class="{ income: Number(item.amount) > 0 }">
          {{ Number(item.amount) > 0 ? '+' : '' }}¥{{ Number(item.amount).toFixed(2) }}
        </text>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && records.length === 0" text="暂无收益明细" image="/static/icons/暂无纪录.svg" />
    </scroll-view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getEarningsDetail } from '@/api/player'

const TX_TYPE_MAP = { PAY: '支付', REFUND: '退款', WITHDRAW: '提现', INCOME: '订单收益', RECHARGE: '充值' }
const records = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onLoad(() => { loadData() })

/** 解析 remark 字符串为结构化数据 */
function parseRemark(remark) {
  if (!remark) return null
  try {
    const orderAmount = remark.match(/订单金额:¥([\d.]+)/)?.[1]
    const commissionRate = remark.match(/平台抽成:([\d.]+)%/)?.[1]
    const commissionAmount = remark.match(/抽成金额:¥([\d.]+)/)?.[1]
    const income = remark.match(/实际收入:¥([\d.]+)/)?.[1]
    if (orderAmount) return { orderAmount, commissionRate, commissionAmount, income }
  } catch (e) {}
  return null
}

async function loadData() {
  loading.value = true
  const res = await getEarningsDetail({ pageNum: pageNum.value, pageSize: 20 })
  const list = (res.data?.records || []).map(item => ({
    ...item,
    remarkInfo: parseRemark(item.remark)
  }))
  if (list.length < 20) finished.value = true
  records.value = pageNum.value === 1 ? list : [...records.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
</script>
<style lang="scss" scoped>
.detail-page { background: #ffffff; min-height: 100vh; }
.list { height: 100vh; }
.record-item { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; background: #f1f5f9; border-bottom: 1rpx solid #f1f5f9;
  .left { flex: 1; .desc { font-size: 28rpx; color: #1e293b; display: block; } .time { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; }
    .remark-info { margin-top: 8rpx; text { display: block; font-size: 22rpx; color: #64748b; line-height: 1.6; } }
  }
  .amount { font-size: 30rpx; color: #1e293b; font-weight: bold; &.income { color: #07c160; } }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
</style>
