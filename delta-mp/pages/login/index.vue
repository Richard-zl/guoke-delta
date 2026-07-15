<template>
  <view class="login-page">
    <view class="logo-area">
      <image class="logo" :src="siteStore.logo || '/static/images/logo.png'" mode="aspectFit" />
      <text class="title">{{ siteStore.siteName }}</text>
      <text class="subtitle">{{ siteStore.subtitle }}</text>
    </view>

    <!-- 登录方式切换 -->
    <view class="tab-bar">
      <view class="tab-item" :class="{ active: loginMode === 'wechat' }" @click="switchWechatMode">{{ quickLoginTitle }}</view>
      <view class="tab-item" :class="{ active: loginMode === 'account' }" @click="loginMode = 'account'">账号登录</view>
    </view>

    <view class="login-area">
      <!-- 快速登录 -->
      <template v-if="loginMode === 'wechat'">
        <template v-if="canUseWechatLogin">
          <view v-if="needPhoneAuth" class="register-profile">
            <text class="register-title">完善资料（可选，推荐使用微信昵称）</text>
            <view class="register-avatar-row">
              <button class="register-avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseRegisterAvatar">
                <image class="register-avatar" :src="registerAvatar || '/static/images/default-avatar.png'" mode="aspectFill" />
                <text class="register-avatar-hint">选择头像</text>
              </button>
            </view>
            <input
              v-model="registerNickname"
              type="nickname"
              class="input register-nickname"
              placeholder="点击使用微信昵称"
            />
          </view>
          <button v-if="!needPhoneAuth" class="login-btn wechat-btn" @click="handleWechatLogin">快速登录</button>
          <button v-else class="login-btn wechat-btn" open-type="getPhoneNumber" @getphonenumber="handlePhoneAuthorize">
            首次登录请授权手机号
          </button>
          <view v-if="needPhoneAuth" class="wechat-tip">检测到是首次登录，授权手机号后即可完成注册</view>
        </template>
        <template v-else>
          <input v-model="phone" type="number" maxlength="11" placeholder="请输入手机号" class="input" />
          <input v-model="verifyCode" type="number" maxlength="6" placeholder="请输入验证码" class="input" />
          <view class="login-btn wechat-btn" @click="handleH5Login">登录</view>
        </template>
      </template>

      <!-- 账号密码登录 -->
      <template v-else>
        <input v-model="username" placeholder="请输入账号" class="input" />
        <input v-model="password" type="password" placeholder="请输入密码" class="input" />
        <view class="login-btn account-btn" @click="handleAccountLogin">登录</view>
      </template>

      <view class="agreement">
        <view class="agree-row">
          <view class="checkbox" :class="{ checked: agreed }" @click="toggleAgree">
            <text v-if="agreed" class="check-icon">✔</text>
          </view>
          <text class="text">我已阅读并同意</text>
          <text class="link" @click="goPage('/pages/agreement/user')">《用户协议》</text>
          <text class="text">和</text>
          <text class="link" @click="goPage('/pages/agreement/privacy')">《隐私政策》</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useSiteStore } from '@/store/site'
import { useChatStore } from '@/store/chat'
import { csLogin } from '@/api/auth'
import { upload } from '@/api/file'
import { setCsToken, setCsInfo } from '@/utils/auth'
import { isMpWeixin } from '@/utils/platform'

const userStore = useUserStore()
const chatStore = useChatStore()
const appStore = useAppStore()
const siteStore = useSiteStore()

const loginMode = ref('wechat')
const username = ref('')
const password = ref('')
const phone = ref('')
const verifyCode = ref('')
const agreed = ref(false)
const needPhoneAuth = ref(false)
const registerNickname = ref('')
const registerAvatar = ref('')
const registerAvatarTemp = ref('')
const canUseWechatLogin = isMpWeixin()
const quickLoginTitle = canUseWechatLogin ? '快速登录' : '手机登录'

function onChooseRegisterAvatar(e) {
  const tempUrl = e.detail?.avatarUrl
  if (!tempUrl) return
  registerAvatar.value = tempUrl
  registerAvatarTemp.value = tempUrl
}

async function buildRegisterProfile() {
  const profile = {}
  const nickname = registerNickname.value.trim()
  if (nickname) profile.nickname = nickname
  if (registerAvatarTemp.value) {
    try {
      profile.avatar = await upload(registerAvatarTemp.value)
    } catch (e) {
      uni.showToast({ title: '头像上传失败，将跳过头像', icon: 'none' })
    }
  }
  return profile
}

async function handleWechatLogin() {
  if (!agreed.value) {
    return uni.showToast({ title: '请先阅读并勾选同意协议', icon: 'none' })
  }
  const result = await userStore.login()
  if (result.success) {
    completeWechatLogin()
    return
  }
  if (result.code === 1006) {
    needPhoneAuth.value = true
  }
}

async function handlePhoneAuthorize(e) {
  if (!agreed.value) {
    return uni.showToast({ title: '请先阅读并勾选同意协议', icon: 'none' })
  }
  if (e.detail.errMsg && e.detail.errMsg.indexOf('deny') > -1) {
    return uni.showToast({ title: '首次登录需授权手机号', icon: 'none' })
  }
  const phoneCode = e.detail.code || ''
  uni.showLoading({ title: '注册中...' })
  try {
    const profile = await buildRegisterProfile()
    const result = await userStore.login(phoneCode, profile)
    if (result.success) completeWechatLogin()
  } finally {
    uni.hideLoading()
  }
}

async function handleH5Login() {
  if (!agreed.value) {
    return uni.showToast({ title: '请先阅读并勾选同意协议', icon: 'none' })
  }
  if (!/^1\d{10}$/.test(phone.value)) {
    return uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
  }
  if (!verifyCode.value) {
    return uni.showToast({ title: '请输入验证码', icon: 'none' })
  }
  const result = await userStore.loginByPhone(phone.value, verifyCode.value)
  if (result.success) completeWechatLogin()
}

async function handleAccountLogin() {
  if (!agreed.value) {
    return uni.showToast({ title: '请先阅读并勾选同意协议', icon: 'none' })
  }
  if (!username.value || !password.value) {
    return uni.showToast({ title: '请输入账号密码', icon: 'none' })
  }
  try {
    const res = await csLogin({ username: username.value, password: password.value, role: 'cs' })
    setCsToken(res.data.token)
    setCsInfo({ adminId: res.data.adminId, nickname: res.data.nickname, avatar: res.data.avatar, role: res.data.role })
    appStore.switchToCs()
    chatStore.connect()
    chatStore.fetchMessageUnreadCount()
  } catch (e) {
    uni.showToast({ title: '登录失败，请检查账号密码', icon: 'none' })
  }
}

function goPage(url) {
  uni.navigateTo({ url })
}

function switchWechatMode() {
  loginMode.value = 'wechat'
}

function completeWechatLogin() {
  needPhoneAuth.value = false
  registerNickname.value = ''
  registerAvatar.value = ''
  registerAvatarTemp.value = ''
  chatStore.connect()
  uni.reLaunch({ url: '/pages/index/index' })
}

function toggleAgree() {
  agreed.value = !agreed.value
}
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f1f5f9;
  padding: 0 60rpx;
  position: relative;
}

.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 80rpx;
  position: relative;
  z-index: 1;

  .logo { width: 160rpx; height: 160rpx; margin-bottom: 24rpx; border-radius: 24rpx; }
  .title { font-size: 44rpx; font-weight: bold; color: #ff4544; }
  .subtitle { font-size: 26rpx; color: #94a3b8; margin-top: 12rpx; }
}

.tab-bar {
  display: flex;
  width: 100%;
  margin-bottom: 60rpx;
  border-bottom: 2rpx solid #e2e8f0;
  position: relative;
  z-index: 1;

  .tab-item {
    flex: 1;
    text-align: center;
    font-size: 30rpx;
    color: #94a3b8;
    padding-bottom: 20rpx;
    position: relative;
    transition: color 0.2s;

    &.active {
      color: #ff4544;
      font-weight: bold;

      &::after {
        content: '';
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 48rpx;
        height: 4rpx;
        background: linear-gradient(90deg, #ff4544, #e63939);
        border-radius: 2rpx;
      }
    }
  }
}

.login-area {
  width: 100%;
  position: relative;
  z-index: 1;

  .input {
    height: 88rpx;
    background: #f1f5f9;
    border: 1rpx solid #e2e8f0;
    border-radius: 12rpx;
    padding: 0 32rpx;
    font-size: 30rpx;
    color: #1e293b;
    margin-bottom: 32rpx;
  }

  .login-btn {
    width: 100%;
    height: 88rpx;
    line-height: 88rpx;
    text-align: center;
    color: #ffffff;
    font-size: 32rpx;
    font-weight: bold;
    border-radius: 999rpx;
    border: none;

    &::after { border: none; }
  }

  .wechat-btn {
    background: linear-gradient(135deg, #ff4544, #e63939);
    color: #ffffff;
  }

  .register-profile {
    margin-bottom: 32rpx;
    padding: 28rpx;
    background: #ffffff;
    border-radius: 16rpx;
    border: 1rpx solid #e2e8f0;
  }

  .register-title {
    display: block;
    font-size: 24rpx;
    color: #64748b;
    text-align: center;
    margin-bottom: 24rpx;
  }

  .register-avatar-row {
    display: flex;
    justify-content: center;
    margin-bottom: 24rpx;
  }

  .register-avatar-btn {
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

  .register-avatar {
    width: 120rpx;
    height: 120rpx;
    border-radius: 50%;
    border: 2rpx solid #e2e8f0;
  }

  .register-avatar-hint {
    margin-top: 12rpx;
    font-size: 22rpx;
    color: #ff4544;
  }

  .register-nickname {
    margin-bottom: 0;
  }

  .wechat-tip {
    margin-top: 20rpx;
    font-size: 24rpx;
    line-height: 1.6;
    color: #94a3b8;
    text-align: center;
  }

  .account-btn {
    background: linear-gradient(135deg, #ff4544, #e63939);
    margin-top: 8rpx;
  }

  .agreement {
    margin-top: 32rpx;
    font-size: 24rpx;
    display: flex;
    justify-content: center;

    .agree-row {
      display: flex;
      align-items: center;
      gap: 8rpx;
    }

    .checkbox {
      width: 32rpx;
      height: 32rpx;
      border-radius: 6rpx;
      border: 2rpx solid rgba(99, 102, 241, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      box-sizing: border-box;
    }

    .checkbox.checked {
      background: linear-gradient(135deg, #ff4544, #e63939);
      border-color: transparent;
    }

    .check-icon {
      font-size: 22rpx;
      color: #ffffff;
    }

    .text { color: #94a3b8; }
    .link { color: #ff4544; }
  }
}
</style>
