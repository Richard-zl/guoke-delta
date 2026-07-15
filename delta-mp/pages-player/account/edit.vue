<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="edit-page">
    <view class="form-card">
      <view class="form-item">
        <text class="label">账户类型</text>
        <view class="type-selector">
          <view v-for="t in types" :key="t.value" class="type-opt" :class="{active: form.type===t.value}" @click="form.type=t.value">
            <text>{{ t.icon }} {{ t.label }}</text>
          </view>
        </view>
      </view>
      <view class="form-item">
        <text class="label">账号</text>
        <input v-model="form.accountNo" placeholder="请输入账号" class="input" />
      </view>
      <view class="form-item">
        <text class="label">真实姓名</text>
        <input v-model="form.accountName" placeholder="请输入真实姓名" class="input" />
      </view>
      <view v-if="form.type==='ALIPAY'||form.type==='WECHAT'" class="form-item form-item-col">
        <text class="label">收款码图片</text>
        <view class="qrcode-upload">
          <view v-if="form.qrcodeUrl" class="qrcode-preview" @click="chooseQrcode">
            <image :src="form.qrcodeUrl" mode="aspectFit" class="qrcode-img" />
            <text class="qrcode-tip">点击更换</text>
          </view>
          <view class="qrcode-placeholder" @click="chooseQrcode">
            <text class="placeholder-icon">📷</text>
            <text class="placeholder-text">上传收款码</text>
          </view>
        </view>
      </view>
      <view v-if="form.type==='BANK'" class="form-item">
        <text class="label">开户行</text>
        <input v-model="form.bankName" placeholder="请输入开户行" class="input" />
      </view>
      <view class="form-item">
        <text class="label">设为默认</text>
        <switch :checked="form.isDefault===1" @change="form.isDefault=$event.detail.value?1:0" />
      </view>
    </view>
    <view class="btn-area">
      <view class="submit-btn" @click="doSave">保存</view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { addAccount, updateAccount } from '@/api/player'
import { chooseAndUpload } from '@/api/file'

const types = [
  { value: 'ALIPAY', label: '支付宝', icon: '💙' },
  { value: 'WECHAT', label: '微信', icon: '💚' },
  { value: 'BANK', label: '银行卡', icon: '🏦' }
]
const isEdit = ref(false)
const form = reactive({ id: '', type: 'ALIPAY', accountNo: '', accountName: '', bankName: '', qrcodeUrl: '', isDefault: 0 })

onLoad((opts) => {
  if (opts.id && opts.data) {
    isEdit.value = true
    const data = JSON.parse(decodeURIComponent(opts.data))
    Object.assign(form, { ...form, ...data, qrcodeUrl: data.qrcodeUrl || '' })
  }
})

async function chooseQrcode() {
  try {
    const urls = await chooseAndUpload(1)
    if (urls.length) form.qrcodeUrl = urls[0]
  } catch (_) {}
}

async function doSave() {
  if (!form.accountNo) return uni.showToast({ title: '请输入账号', icon: 'none' })
  if (!form.accountName) return uni.showToast({ title: '请输入姓名', icon: 'none' })
  if ((form.type === 'ALIPAY' || form.type === 'WECHAT') && !form.qrcodeUrl) return uni.showToast({ title: '请上传收款码图片', icon: 'none' })
  if (form.type === 'BANK' && !form.bankName) return uni.showToast({ title: '请输入开户行', icon: 'none' })
  try {
    if (isEdit.value) { await updateAccount(form) } else { await addAccount(form) }
    uni.showToast({ title: '保存成功' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (e) {}
}
</script>
<style lang="scss" scoped>
.edit-page { background: #ffffff; min-height: 100vh; }
.form-card { margin: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx; padding: 0 24rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid rgba(0,0,0,0.04);
  .label { font-size: 28rpx; color: rgba(0,0,0,0.85); width: 160rpx; flex-shrink: 0; } .input { flex: 1; font-size: 28rpx; }
}
.form-item-col { flex-direction: column; align-items: flex-start; }
.qrcode-upload { margin-top: 12rpx; }
.qrcode-preview, .qrcode-placeholder { width: 200rpx; height: 200rpx; border: 2rpx dashed rgba(0,0,0,0.15); border-radius: 12rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; background: #f8fafc; }
.qrcode-img { width: 180rpx; height: 180rpx; }
.qrcode-tip { font-size: 22rpx; color: #94a3b8; margin-top: 8rpx; }
.placeholder-icon { font-size: 48rpx; margin-bottom: 8rpx; }
.placeholder-text { font-size: 24rpx; color: #64748b; }
.type-selector { display: flex; gap: 16rpx; flex: 1; }
.type-opt { padding: 12rpx 20rpx; background: rgba(0,0,0,0.05); border-radius: 8rpx; font-size: 24rpx; &.active { background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; } }
.btn-area { padding: 40rpx 24rpx; }
.submit-btn { background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; text-align: center; padding: 24rpx; border-radius: 999rpx; font-size: 30rpx; }
</style>
