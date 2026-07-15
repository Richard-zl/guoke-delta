<template>
  <AuditPlaceholder v-if="pageBlocked" />

  <view v-else class="profile-edit-page">
    <view class="form-card">
      <view class="form-item form-item-avatar">
        <text class="label">头像</text>
        <view class="avatar-wrap" @click="chooseAvatar">
          <image v-if="form.avatar" class="avatar-img" :src="form.avatar" mode="aspectFill" />
          <view class="avatar-placeholder">
            <text class="placeholder-text">点击上传</text>
          </view>
        </view>
      </view>
      <view class="form-item">
        <text class="label">昵称</text>
        <input v-model="form.nickname" placeholder="请输入昵称" class="input" />
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
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getCsProfile, updateCsProfile } from '@/pages-cs/api/cs'
import { chooseAndUpload } from '@/api/file'
import { setCsInfo, getCsInfo } from '@/utils/auth'

const { pageBlocked } = useAuditPageGuard()
const form = reactive({ nickname: '', avatar: '' })

onLoad(async () => {
  try {
    const res = await getCsProfile()
    const d = res.data
    if (d) {
      form.nickname = d.nickname || ''
      form.avatar = d.avatar || ''
    } else {
      const info = getCsInfo()
      form.nickname = info?.nickname || ''
      form.avatar = info?.avatar || ''
    }
  } catch (_) {}
})

async function chooseAvatar() {
  try {
    const urls = await chooseAndUpload(1)
    if (urls.length) form.avatar = urls[0]
  } catch (_) {}
}

async function doSave() {
  if (!form.nickname?.trim()) return uni.showToast({ title: '请输入昵称', icon: 'none' })
  try {
    await updateCsProfile({ nickname: form.nickname.trim(), avatar: form.avatar || undefined })
    // 更新本地 cs_info
    const info = getCsInfo() || {}
    setCsInfo({ ...info, nickname: form.nickname.trim(), avatar: form.avatar })
    uni.showToast({ title: '保存成功' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch (e) {}
}
</script>
<style lang="scss" scoped>
.profile-edit-page { background: #ffffff; min-height: 100vh; }
.form-card { margin: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx; padding: 24rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid rgba(0,0,0,0.04);
  .label { font-size: 28rpx; color: rgba(0,0,0,0.85); width: 160rpx; flex-shrink: 0; }
  .input { flex: 1; font-size: 28rpx; }
}
.form-item-avatar { flex-direction: column; align-items: flex-start; }
.avatar-wrap { margin-top: 12rpx; width: 160rpx; height: 160rpx; border-radius: 50%; overflow: hidden; border: 2rpx dashed rgba(0,0,0,0.15); }
.avatar-img { width: 100%; height: 100%; }
.avatar-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #f8fafc; }
.placeholder-text { font-size: 24rpx; color: #94a3b8; }
.btn-area { padding: 40rpx 24rpx; }
.submit-btn { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; text-align: center; padding: 24rpx; border-radius: 999rpx; font-size: 30rpx; }
</style>
