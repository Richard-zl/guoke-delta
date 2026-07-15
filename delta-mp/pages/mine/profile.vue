<template>
  <view class="profile-page">
    <!-- 头像区域 -->
    <view class="avatar-section">
      <!-- #ifdef MP-WEIXIN -->
      <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseWxAvatar">
        <image class="avatar-img" :src="avatar || '/static/images/default-avatar.png'" mode="aspectFill" />
        <view class="avatar-edit-hint">
          <text class="hint-text">点击更换头像</text>
        </view>
      </button>
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="avatar-btn" @click="chooseAvatarImage">
        <image class="avatar-img" :src="avatar || '/static/images/default-avatar.png'" mode="aspectFill" />
        <view class="avatar-edit-hint">
          <text class="hint-text">点击更换头像</text>
        </view>
      </view>
      <!-- #endif -->
    </view>

    <!-- 表单 -->
    <view class="form-card">
      <view class="form-item">
        <text class="label">昵称</text>
        <!-- #ifdef MP-WEIXIN -->
        <input v-model="nickname" type="nickname" placeholder="点击使用微信昵称" class="input" />
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <input v-model="nickname" placeholder="请输入昵称" class="input" />
        <!-- #endif -->
      </view>
      <view class="form-item" v-if="phone">
        <text class="label">手机号</text>
        <text class="value">{{ phone }}</text>
      </view>
      <view class="form-item" v-if="userId">
        <text class="label">用户ID</text>
        <text class="value uid">{{ userId }}</text>
      </view>
    </view>

    <view class="btn-save" @click="save">保存</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getUserProfile, updateUserProfile } from '@/api/user'
import { chooseAndUpload, upload } from '@/api/file'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const nickname = ref('')
const avatar = ref('')
const phone = ref('')
const userId = ref('')

onLoad(async () => {
  try {
    const res = await getUserProfile()
    const d = res.data || {}
    nickname.value = d.nickname || ''
    avatar.value = d.avatar || ''
    phone.value = d.phone || ''
    userId.value = d.id || ''
  } catch (e) { /* ignore */ }
})

async function onChooseWxAvatar(e) {
  const tempUrl = e.detail.avatarUrl
  if (!tempUrl) return
  await uploadAvatar(tempUrl)
}

async function chooseAvatarImage() {
  try {
    uni.showLoading({ title: '上传中...' })
    const urls = await chooseAndUpload(1)
    avatar.value = urls[0] || avatar.value
    uni.hideLoading()
  } catch (err) {
    uni.hideLoading()
    if (err?.errMsg && err.errMsg.includes('cancel')) return
    uni.showToast({ title: '上传失败', icon: 'none' })
  }
}

async function uploadAvatar(tempUrl) {
  try {
    uni.showLoading({ title: '上传中...' })
    const url = await upload(tempUrl)
    avatar.value = url
    uni.hideLoading()
  } catch (err) {
    uni.hideLoading()
    uni.showToast({ title: '上传失败', icon: 'none' })
  }
}

async function save() {
  if (!nickname.value.trim()) {
    return uni.showToast({ title: '请输入昵称', icon: 'none' })
  }
  try {
    await updateUserProfile({ nickname: nickname.value, avatar: avatar.value })
    await userStore.fetchProfile(true)
    uni.showToast({ title: '已保存' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (e) {
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  padding: 24rpx;
  background: #f1f5f9;
  min-height: 100vh;
}

.avatar-section {
  display: flex;
  justify-content: center;
  padding: 48rpx 0 32rpx;
}

.avatar-btn {
  background: transparent;
  border: none;
  padding: 0;
  margin: 0;
  line-height: 1;
  display: flex;
  flex-direction: column;
  align-items: center;

  &::after { border: none; }
}

.avatar-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(99, 102, 241, 0.2);
}

.avatar-edit-hint {
  margin-top: 16rpx;
  .hint-text {
    font-size: 24rpx;
    color: #ff4544;
  }
}

.form-card {
  background: #ffffff;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(99, 102, 241, 0.08);
  overflow: hidden;
}

.form-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 28rpx;
  border-bottom: 1rpx solid #f1f5f9;

  &:last-child { border-bottom: none; }

  .label {
    font-size: 28rpx;
    color: #334155;
    flex-shrink: 0;
    margin-right: 20rpx;
  }

  .input {
    flex: 1;
    text-align: right;
    font-size: 28rpx;
    color: #1e293b;
  }

  .value {
    font-size: 28rpx;
    color: #64748b;
  }

  .uid {
    font-size: 24rpx;
    color: #94a3b8;
  }
}

.btn-save {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: linear-gradient(135deg, #ff4544, #e63939);
  color: #ffffff;
  font-weight: bold;
  font-size: 32rpx;
  border-radius: 999rpx;
  margin-top: 60rpx;
}
</style>
