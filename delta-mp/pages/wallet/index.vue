<template>
  <view class="wallet-page">
    <view class="balance-card">
      <text class="label">账户余额（元）</text>
      <text class="amount">{{balance}}</text>
    </view>
    <view class="section-title">交易记录</view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="t in transactions" :key="t.id" class="tx-item">
        <view><text class="tx-type">{{typeText(t.type)}}</text><text class="tx-remark">{{remarkText(t.remark)}}</text></view>
        <view><text class="tx-amount" :class="{income:t.amount>0}">{{t.amount>0?'+':''}}{{t.amount}}</text><text class="tx-time">{{formatTime(t.createdAt)}}</text></view>
      </view>
<EmptyState v-if="transactions.length===0" text="暂无记录" image="/static/icons/暂无纪录.svg" />
    </scroll-view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getWallet } from '@/api/user'
import { getTransactions } from '@/api/pay'
import { blockIfUnderReview } from '@/composables/useAuditGuard'
const TX_TYPE_MAP = { PAY: '支付', REFUND: '退款', WITHDRAW: '提现', INCOME: '收入', RECHARGE: '充值', BALANCE_PAY: '余额支付' }
function typeText(type) { return TX_TYPE_MAP[type] || '交易' }
function remarkText(remark) {
  if (!remark) return ''
  return /[A-Za-z]/.test(remark) ? '交易' : remark
}
function formatTime(str) {
  if (!str) return ''
  const s = String(str).replace('T', ' ').trim()
  const m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?/)
  if (!m) return s.replace(/[A-Za-z]/g, '') || ''
  const [, y, mo, d, h, mi, sec] = m
  const part = `${y}年${Number(mo)}月${Number(d)}日`
  if (h !== undefined && mi !== undefined) return part + ` ${Number(h)}:${String(Number(mi)).padStart(2,'0')}`
  return part
}
const balance = ref('0.00')
const transactions = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
onLoad(async () => {
  if (await blockIfUnderReview()) return
  const w = await getWallet(); balance.value = Number(w.data?.balance||0).toFixed(2)
  loadData()
})

onShow(async () => {
  if (await blockIfUnderReview()) return
})
async function loadData() {
  loading.value = true
  const res = await getTransactions({ pageNum:pageNum.value, pageSize:20 })
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  transactions.value = pageNum.value === 1 ? list : [...transactions.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
</script>
<style lang="scss" scoped>
.wallet-page { background: #f1f5f9; min-height: 100vh; }
.balance-card { background:linear-gradient(135deg, #ff4544, #e63939); color:#ffffff; padding:48rpx 32rpx; border-radius:16rpx; margin:24rpx; .label { font-size:26rpx; display:block; margin-bottom:16rpx; opacity:0.9; } .amount { font-size:56rpx; font-weight:bold; } }
.section-title { padding:24rpx; font-size:30rpx; font-weight:bold; color:#ff4544; }
.tx-item { display:flex; justify-content:space-between; padding:24rpx; background:#ffffff; border-bottom:1rpx solid #f1f5f9; .tx-type { font-size:28rpx; color:#1e293b; } .tx-remark { font-size:22rpx; color:#94a3b8; margin-left:12rpx; } .tx-amount { font-size:28rpx; color:#1e293b; &.income { color:#07c160; } } .tx-time { font-size:22rpx; color:#94a3b8; display:block; text-align:right; } }
.list { height:calc(100vh - 400rpx); }
</style>
