<template>
  <AuditPlaceholder v-if="pageBlocked" />

  <view v-else class="apply-page">
    <view class="form-item"><text class="label">真实姓名</text><input v-model="form.realName" placeholder="请输入" /></view>
    <view class="form-item"><text class="label">联系电话</text><input v-model="form.phone" type="number" placeholder="请输入" /></view>
    <view class="form-item"><text class="label">{{ clubIdLabel }}</text><input v-model="form.gameLevel" :placeholder="clubIdPlaceholder" /></view>
    <view class="form-item"><text class="label">擅长服务</text><input v-model="form.serviceTypes" placeholder="如：三角洲行动，无畏契约" /></view>
    <view class="form-item"><text class="label">技能标签</text><input v-model="form.skillTags" placeholder="如：突击位，信息位" /></view>
    <view class="form-item"><text class="label">证明截图</text><ImageUploader v-model="proofImages" :max="4" /></view>
    <view v-if="siteStore.depositRequired" class="deposit-tip">申请成为打手需缴纳押金</view>
    <view class="btn" :class="{ disabled: submitting }" @click="submit">{{ submitting ? '提交中...' : (siteStore.depositRequired ? '支付押金并提交' : '提交申请') }}</view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import ImageUploader from '@/components/ImageUploader.vue'
import { applyPlayer } from '@/api/player'
import { createPlayerDeposit } from '@/api/pay'
import { useSiteBranding } from '@/composables/useSiteBranding'
import { isMpWeixin } from '@/utils/platform'
const { siteStore, clubIdLabel, clubIdPlaceholder } = useSiteBranding()

onLoad(async () => {
  if (!siteStore.configLoaded) await siteStore.fetchSiteConfig()
})
const canUseWechatPay = isMpWeixin()
const form = reactive({ realName: '', phone: '', gameLevel: '', serviceTypes: '', skillTags: '' })
const proofImages = ref([])
const submitting = ref(false)
async function submit() {
  if (!form.realName || !form.phone) return uni.showToast({ title: '请填写完整', icon: 'none' })
  if (submitting.value) return
  submitting.value = true
  try {
    if (siteStore.depositRequired) {
      // 需要押金：先支付再提交
      await submitWithDeposit()
    } else {
      // 无需押金：直接提交
      await applyPlayer({
        ...form,
        proofImages: proofImages.value.join(',')
      })
      uni.showToast({ title: '已提交，等待审核' })
      setTimeout(() => uni.navigateBack(), 1500)
    }
  } catch (e) {
    if (e && e.errMsg && !e.errMsg.includes('cancel')) {
      uni.showToast({ title: e.msg || '操作失败', icon: 'none' })
    }
  } finally {
    submitting.value = false
  }
}

async function submitWithDeposit() {
  if (!canUseWechatPay) {
    uni.showToast({ title: 'H5暂未接入押金在线支付', icon: 'none' })
    throw new Error('H5 payment is not supported')
  }
  const res = await createPlayerDeposit()
  const { paymentNo, timeStamp, nonceStr, package: pkg, signType, paySign } = res.data
  await new Promise((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp,
      nonceStr,
      package: pkg,
      signType: signType || 'RSA',
      paySign,
      success: () => resolve(),
      fail: (e) => reject(e)
    })
  })
  const doApply = () => applyPlayer({
    ...form,
    proofImages: proofImages.value.join(','),
    depositPaymentNo: paymentNo
  })
  try {
    await doApply()
  } catch (e) {
    if (e?.msg?.includes('未到账')) {
      uni.showToast({ title: '支付到账中，请稍候...', icon: 'none' })
      await new Promise(r => setTimeout(r, 2500))
      await doApply()
    } else {
      throw e
    }
  }
  uni.showToast({ title: '已提交，等待审核' })
  setTimeout(() => uni.navigateBack(), 1500)
}
</script>
<style lang="scss" scoped>
.apply-page { padding:24rpx; background: #ffffff; min-height: 100vh; }
.form-item { background: #f1f5f9; padding:24rpx; border-radius:12rpx; margin-bottom:20rpx; .label { font-size:26rpx; color: #64748b; margin-bottom:12rpx; display:block; } input { height:72rpx; background: rgba(0,0,0,0.05); border-radius:8rpx; padding:0 24rpx; font-size:28rpx; } }
.deposit-tip { font-size:24rpx; color:#94a3b8; margin-top:16rpx; margin-bottom:8rpx; }
.btn { height:88rpx; line-height:88rpx; text-align:center; background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color:#ffffff; font-weight:bold; font-size:32rpx; border-radius:999rpx; margin-top:40rpx; &.disabled { opacity:0.7; } }
</style>
