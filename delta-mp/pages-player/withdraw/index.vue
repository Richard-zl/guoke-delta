<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="withdraw-page">
    <view class="balance-card">
      <text class="label">可提现金额（元）</text>
      <text class="amount">¥{{ balance }}</text>
      <text class="pending-label">待入账 ¥{{ pendingBalance }}</text>
      <text v-if="delayDays > 0" class="delay-hint">确认后满 {{ delayDays }} 天转入可提现</text>
    </view>

    <!-- 提现规则（微信审核要求清晰展示） -->
    <view class="rules-card">
      <text class="rules-title">提现规则</text>
      <view v-for="(rule, idx) in withdrawRules" :key="idx" class="rule-item">
        <text class="rule-label">{{ rule.label }}</text>
        <text class="rule-text">{{ rule.text }}</text>
      </view>
    </view>

    <!-- 提现金额 -->
    <view class="form-card">
      <view class="form-item">
        <text class="item-label">提现金额</text>
        <input type="digit" v-model="form.amount" :placeholder="amountPlaceholder" class="input" />
        <text class="all-btn" @click="form.amount = balance">全部提现</text>
      </view>
      <!-- 选择提现账户 -->
      <view class="form-item" @click="showAccountPicker = true">
        <text class="item-label">提现账户</text>
        <text :class="['pick-text', selectedAccount ? '' : 'placeholder']">{{ selectedAccount ? accountDisplay(selectedAccount) : '请选择提现账户' }}</text>
        <text class="arrow">›</text>
      </view>
      <view class="form-item">
        <text class="item-label">备注</text>
        <input v-model="form.remark" placeholder="选填" class="input" />
      </view>
    </view>
    <view class="btn-area">
      <view :class="['submit-btn', { disabled: !inWithdrawWindow }]" @click="doSubmit">确认提现</view>
    </view>
    <view class="links">
      <text class="link" @click="goList">提现记录</text>
      <text class="link" @click="goAccounts">管理账户</text>
    </view>
    <!-- 账户选择弹窗 -->
    <view v-if="showAccountPicker" class="mask" @click="showAccountPicker=false">
      <view class="picker-popup" @click.stop>
        <text class="picker-title">选择提现账户</text>
        <view v-for="a in accounts" :key="a.id" class="account-option" :class="{active: selectedAccount?.id===a.id}" @click="selectAccount(a)">
          <text>{{ accountDisplay(a) }}</text>
        </view>
<EmptyState v-if="accounts.length===0" text="暂无账户，请先添加" image="/pages-player/static/icons/暂无地址.svg" button-text="添加账户" @action="goAccounts" />
      </view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { applyWithdraw, getAccountList, getEarningsSummary, getWithdrawWindow } from '@/api/player'
import {
  WITHDRAW_MIN_AMOUNT,
  WITHDRAW_RULE_ITEMS
} from '@/constants/withdrawRules'

const withdrawRules = ref([...WITHDRAW_RULE_ITEMS])
const amountPlaceholder = computed(() => `请输入提现金额（最低 ¥${WITHDRAW_MIN_AMOUNT}）`)

const balance = ref('0.00')
const pendingBalance = ref('0.00')
const delayDays = ref(0)
const inWithdrawWindow = ref(true)
const windowHint = ref('')
const form = reactive({ amount: '', accountId: '', remark: '' })
const accounts = ref([])
const selectedAccount = ref(null)
const showAccountPicker = ref(false)

onShow(async () => {
  try {
    const res = await getEarningsSummary()
    balance.value = Number(res.data?.balance || 0).toFixed(2)
    pendingBalance.value = Number(res.data?.pendingBalance || 0).toFixed(2)
    delayDays.value = Number(res.data?.delayDays || 0)
  } catch (e) {}
  try { const res = await getAccountList(); accounts.value = res.data || [] } catch (e) {}
  try {
    const res = await getWithdrawWindow()
    const data = res.data || {}
    inWithdrawWindow.value = data.inWithdrawWindow !== false
    windowHint.value = data.nextWindowHint || data.windowsText || ''
    if (data.windowsText) {
      withdrawRules.value = WITHDRAW_RULE_ITEMS.map(rule =>
        rule.label === '提现时间' ? { ...rule, text: data.windowsText } : rule
      )
    }
  } catch (e) {}
})

function accountDisplay(a) { return `${a.type === 'ALIPAY' ? '支付宝' : a.type === 'WECHAT' ? '微信' : a.type === 'BANK' ? '银行卡' : a.type} ${(a.accountNo || '').replace(/(.{3}).*(.{4})/, '$1****$2')}` }
function selectAccount(a) { selectedAccount.value = a; form.accountId = a.id; showAccountPicker.value = false }
async function doSubmit() {
  if (!inWithdrawWindow.value) {
    return uni.showToast({ title: windowHint.value || '当前不在提现时间窗口内', icon: 'none' })
  }
  if (!form.amount || Number(form.amount) <= 0) return uni.showToast({ title: '请输入金额', icon: 'none' })
  if (Number(form.amount) < WITHDRAW_MIN_AMOUNT) {
    return uni.showToast({ title: `最低提现金额为 ¥${WITHDRAW_MIN_AMOUNT}`, icon: 'none' })
  }
  if (Number(form.amount) > Number(balance.value)) return uni.showToast({ title: '超过可提现金额', icon: 'none' })
  if (!form.accountId) return uni.showToast({ title: '请选择提现账户', icon: 'none' })
  try {
    await applyWithdraw({ amount: Number(form.amount), accountId: form.accountId, remark: form.remark })
    uni.showToast({ title: '提现申请已提交' })
    setTimeout(() => uni.navigateTo({ url: '/pages-player/withdraw/list' }), 1500)
  } catch (e) {}
}
function goList() { uni.navigateTo({ url: '/pages-player/withdraw/list' }) }
function goAccounts() { uni.navigateTo({ url: '/pages-player/account/list' }) }
</script>
<style lang="scss" scoped>
.withdraw-page { background: #ffffff; min-height: 100vh; }
.balance-card { background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; margin: 24rpx; border-radius: 16rpx; padding: 40rpx 32rpx;
  .label { font-size: 26rpx; opacity: 0.85; display: block; }
  .amount { font-size: 56rpx; font-weight: bold; display: block; margin-top: 12rpx; }
  .pending-label { font-size: 24rpx; opacity: 0.85; display: block; margin-top: 12rpx; }
  .delay-hint { font-size: 22rpx; opacity: 0.8; display: block; margin-top: 8rpx; }
}
.rules-card {
  margin: 0 24rpx 24rpx;
  padding: 28rpx 24rpx;
  background: #f8fafc;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  .rules-title { font-size: 28rpx; font-weight: bold; color: #1e293b; display: block; margin-bottom: 20rpx; }
  .rule-item { margin-bottom: 20rpx; &:last-child { margin-bottom: 0; } }
  .rule-label { font-size: 26rpx; font-weight: 600; color: #334155; display: block; margin-bottom: 6rpx; }
  .rule-text { font-size: 24rpx; color: #64748b; line-height: 1.6; display: block; }
}
.form-card { margin: 24rpx; background: #f1f5f9; border-radius: 12rpx; padding: 0 24rpx; }
.form-item { display: flex; align-items: center; padding: 28rpx 0; border-bottom: 1rpx solid #f1f5f9;
  .item-label { font-size: 28rpx; color: #1e293b; width: 160rpx; } .input { flex: 1; font-size: 28rpx; }
  .pick-text { flex: 1; font-size: 28rpx; color: #1e293b; &.placeholder { color: #cbd5e1; } } .arrow { color: #cbd5e1; } .all-btn { font-size: 26rpx; color: #ff4544; }
}
.btn-area { padding: 40rpx 24rpx 16rpx; }
.submit-btn { background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; text-align: center; padding: 24rpx; border-radius: 999rpx; font-size: 30rpx;
  &.disabled { opacity: 0.5; }
}
.links { display: flex; justify-content: center; gap: 48rpx; padding: 24rpx; .link { font-size: 26rpx; color: #ff4544; } }
.mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: flex-end; z-index: 999; }
.picker-popup { width: 100%; background: #ffffff; border-radius: 24rpx 24rpx 0 0; border-top: 1rpx solid #e2e8f0; padding: 32rpx 24rpx; max-height: 60vh; overflow-y: auto;
  .picker-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 24rpx; text-align: center; }
}
.account-option { padding: 24rpx; border-bottom: 1rpx solid #f1f5f9; font-size: 28rpx; &.active { color: #ff4544; background: #f0f5ff; border-radius: 8rpx; } }
</style>
