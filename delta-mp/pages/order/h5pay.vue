<template>
  <view class="h5pay-page">
    <!-- 非微信浏览器：全屏拦截，不请求后端（Spec 决议 #11） -->
    <view v-if="state === 'blocked'" class="full-screen">
      <image class="full-icon" src="/static/icons/警示.svg" mode="aspectFit" />
      <text class="full-title">请在微信中打开</text>
      <text class="full-desc">请返回微信客服会话，点击链接后使用微信内置浏览器打开本页面完成支付</text>
    </view>

    <!-- 加载中：拉取授权/订单信息 -->
    <view v-else-if="state === 'loading'" class="full-screen">
      <view class="spinner" />
      <text class="loading-text">正在加载支付信息...</text>
    </view>

    <!-- 失败/失效：链接过期、订单已支付、状态变更等，均不提供第二支付入口 -->
    <view v-else-if="state === 'error'" class="full-screen">
      <image class="full-icon" src="/static/icons/警示.svg" mode="aspectFit" />
      <text class="full-title">{{ errorTitle }}</text>
      <text class="full-desc">{{ errorMsg }}</text>
    </view>

    <!-- 成功页 -->
    <view v-else-if="state === 'success'" class="full-screen success-screen">
      <image class="full-icon" src="/static/icons/安全认证.svg" mode="aspectFit" />
      <text class="full-title">支付成功</text>
      <PriceText :value="order?.amount" :size="52" />
      <text v-if="order?.orderNo" class="order-no">订单号 {{ order.orderNo }}</text>
      <view class="btn-pay" @click="handleBackToMiniProgram">打开小程序查看订单</view>
      <text class="fallback-text">{{ fallbackText }}</text>
    </view>

    <!-- 待支付：一屏一事，仅金额 + 只读券 + 单一 CTA -->
    <view v-else class="pending-screen">
      <view class="amount-area">
        <text class="label">支付金额</text>
        <PriceText :value="order?.amount" :size="60" />
        <text v-if="order?.productName" class="product-name">{{ order.productName }}</text>
        <text v-if="!expired" class="countdown">支付剩余时间 {{ formatCountdown }}</text>
        <text v-else class="countdown expired">订单已超时，请返回小程序重新发起</text>
      </view>

      <!-- 优惠券：下单时已绑定，H5 只读，禁止改券（Spec 决议 #2） -->
      <view v-if="order?.couponName" class="coupon-section readonly">
        <view class="coupon-left">
          <text class="coupon-label">优惠券</text>
          <text class="coupon-value">{{ order.couponName }}</text>
        </view>
        <view class="coupon-right">
          <text class="discount-tag">-¥{{ Number(order?.couponDiscountAmount || 0).toFixed(2) }}</text>
        </view>
      </view>

      <view class="btn-pay" :class="{ disabled: expired || paying }" @click="handlePay">
        <image class="btn-icon" src="/static/icons/钞票.svg" mode="aspectFit" />
        <text>{{ paying ? '支付处理中...' : '微信支付' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PriceText from '@/components/PriceText.vue'
import { h5Oauth, h5Order, h5Prepay, h5Jsconfig, h5MpAppId } from '@/api/pay'
import { isWeixinBrowser } from '@/utils/weixinUa'

/** loading | blocked | pending | error | success */
const state = ref('loading')
const token = ref('')
const openid = ref('')
const order = ref(null)
const errorTitle = ref('支付链接已失效')
const errorMsg = ref('请返回微信小程序重新发起支付')
const countdown = ref(0)
const expired = ref(false)
const paying = ref(false)
const fallbackText = '请返回微信小程序查看订单'
let timer = null

const formatCountdown = computed(() => {
  const m = Math.floor(countdown.value / 60)
  const s = countdown.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

function showError(title, msg) {
  errorTitle.value = title
  errorMsg.value = msg
  state.value = 'error'
}

onLoad(async (opts = {}) => {
  // 独立 H5 页不应出现在小程序/App 端；本期只服务微信内 H5 场景
  // #ifndef H5
  state.value = 'blocked'
  return
  // #endif

  // #ifdef H5
  if (!isWeixinBrowser()) {
    state.value = 'blocked'
    return
  }

  const incomingToken = opts.token || sessionStorage.getItem('h5pay_token') || ''
  if (!incomingToken) {
    showError('链接无效', '未获取到支付信息，请返回微信小程序重新发起支付')
    return
  }
  token.value = incomingToken
  sessionStorage.setItem('h5pay_token', incomingToken)

  try {
    if (opts.code) {
      const res = await h5Oauth(opts.code)
      openid.value = res.data.openid
      sessionStorage.setItem('h5pay_openid', openid.value)
      cleanOauthQueryFromUrl()
    } else {
      openid.value = sessionStorage.getItem('h5pay_openid') || ''
    }

    if (!openid.value) {
      await redirectToOAuth()
      return
    }

    await loadOrder()
    initWxConfig()
  } catch (e) {
    showError('加载失败', e?.msg || '订单加载失败，请重试')
  }
  // #endif
})

onUnmounted(() => { if (timer) clearInterval(timer) })

// #ifdef H5
/** 授权 code 只可使用一次，成功换取 openid 后从地址上清除 code/state，避免刷新页面重复消费失败 */
function cleanOauthQueryFromUrl() {
  const hash = location.hash || ''
  const [path, query = ''] = hash.replace(/^#/, '').split('?')
  const params = new URLSearchParams(query)
  params.delete('code')
  params.delete('state')
  const qs = params.toString()
  const newHash = `#${path}${qs ? `?${qs}` : ''}`
  history.replaceState(null, '', `${location.pathname}${newHash}`)
}

/**
 * 轻量 snsapi_base 静默授权。
 * 注意：微信 redirect_uri 不能带 hash，否则回调会丢路由导致白屏；
 * 回调落到 /h5/?code&state，由 App.vue 再跳进本页（见 consumeH5OauthCallback）。
 */
async function redirectToOAuth() {
  try {
    const res = await h5MpAppId()
    const appId = res.data?.appId
    if (!appId) {
      showError('暂无法完成支付', '服务号未配置，请联系客服')
      return
    }
    const redirectUri = encodeURIComponent(`${location.origin}/h5/`)
    const authUrl = `https://open.weixin.qq.com/connect/oauth2/authorize?appid=${appId}`
      + `&redirect_uri=${redirectUri}&response_type=code&scope=snsapi_base`
      + `&state=${encodeURIComponent(token.value)}#wechat_redirect`
    location.href = authUrl
  } catch (e) {
    showError('暂无法完成支付', '授权跳转失败，请重试')
  }
}

async function loadOrder() {
  const res = await h5Order(token.value)
  order.value = res.data
  if (order.value.status !== 'PENDING_PAYMENT') {
    if (order.value.status === 'PAID') {
      showError('订单已支付', '该订单已支付成功，无需重复支付')
    } else {
      showError('订单状态已变更', '请返回微信小程序查看订单最新状态')
    }
    return
  }
  if (order.value.payDeadline) {
    const deadlineMs = new Date(order.value.payDeadline.replace(' ', 'T')).getTime()
    const remainSec = Math.floor((deadlineMs - Date.now()) / 1000)
    countdown.value = remainSec > 0 ? remainSec : 0
  } else {
    countdown.value = 900
  }
  expired.value = countdown.value <= 0
  if (!expired.value) startCountdown()
  state.value = 'pending'
}

function startCountdown() {
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      expired.value = true
    }
  }, 1000)
}

let jweixinPromise = null
/** 动态加载微信 JS-SDK，避免全站引入影响非支付页 */
function loadJweixinScript() {
  if (window.wx && window.wx.config) return Promise.resolve()
  if (jweixinPromise) return jweixinPromise
  jweixinPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://res.wx.qq.com/open/js/jweixin-1.6.0.js'
    script.onload = resolve
    script.onerror = reject
    document.head.appendChild(script)
  })
  return jweixinPromise
}

/** 用后端签名初始化 wx.config，供 chooseWXPay 使用；signature 的 url 必须与当前页面地址一致（不含 # 及其后内容） */
function initWxConfig() {
  loadJweixinScript()
    .then(() => h5Jsconfig(location.href.split('#')[0]))
    .then((res) => {
      const cfg = res.data
      window.wx.config({
        debug: false,
        appId: cfg.appId,
        timestamp: cfg.timestamp,
        nonceStr: cfg.nonceStr,
        signature: cfg.signature,
        jsApiList: ['chooseWXPay']
      })
      window.wx.error((err) => { console.warn('[h5pay] wx.config error', err) })
    })
    .catch((e) => { console.warn('[h5pay] init wx jssdk failed', e) })
}

function payWithWx(payParams) {
  return new Promise((resolve, reject) => {
    if (!window.wx || !window.wx.chooseWXPay) {
      reject(new Error('微信支付组件未就绪，请重试'))
      return
    }
    window.wx.chooseWXPay({
      timestamp: payParams.timeStamp,
      nonceStr: payParams.nonceStr,
      package: payParams.package,
      signType: payParams.signType || 'RSA',
      paySign: payParams.paySign,
      success: resolve,
      fail: reject,
      cancel: reject
    })
  })
}

/** 支付成功后尽量跳回小程序（需配置 URL Scheme）；未配置则复制引导文案兜底 */
function handleBackToMiniProgram() {
  const schemeUrl = import.meta.env.VITE_MP_SCHEME_URL
  if (schemeUrl) {
    location.href = schemeUrl
    return
  }
  uni.setClipboardData({
    data: fallbackText,
    success: () => uni.showToast({ title: '已复制，请打开微信小程序查看', icon: 'none' }),
    fail: () => uni.showToast({ title: fallbackText, icon: 'none' })
  })
}
// #endif

async function handlePay() {
  if (expired.value || paying.value) return
  paying.value = true
  try {
    const res = await h5Prepay(token.value, openid.value)
    // #ifdef H5
    await payWithWx(res.data)
    // #endif
    state.value = 'success'
  } catch (e) {
    if (e && e.code === 4010) {
      showError('订单已支付', '该订单已支付成功，无需重复支付')
    } else if (e && e.errMsg && /cancel/i.test(e.errMsg)) {
      uni.showToast({ title: '已取消支付', icon: 'none' })
    } else {
      uni.showToast({ title: e?.msg || '支付失败，请重试', icon: 'none' })
    }
  } finally {
    paying.value = false
  }
}
</script>

<style lang="scss" scoped>
.h5pay-page { min-height: 100vh; background: #f1f5f9; padding: 40rpx 24rpx; box-sizing: border-box; }

.full-screen {
  min-height: 80vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 48rpx;
  text-align: center;

  .full-icon { width: 160rpx; height: 160rpx; margin-bottom: 32rpx; }
  .full-title { font-size: 34rpx; font-weight: bold; color: #1e293b; margin-bottom: 16rpx; }
  .full-desc { font-size: 26rpx; color: #64748b; line-height: 1.6; }
  .loading-text { font-size: 26rpx; color: #64748b; margin-top: 24rpx; }

  .spinner {
    width: 64rpx;
    height: 64rpx;
    border: 6rpx solid #e2e8f0;
    border-top-color: #ff4544;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }
}

.success-screen {
  .order-no { font-size: 24rpx; color: #94a3b8; margin-top: 16rpx; }
  .fallback-text { font-size: 24rpx; color: #94a3b8; margin-top: 24rpx; }
  .btn-pay { width: 100%; margin-top: 48rpx; }
}

@keyframes spin { to { transform: rotate(360deg); } }

.pending-screen {
  .amount-area {
    text-align: center;
    padding: 60rpx 0 40rpx;

    .label { font-size: 28rpx; color: #64748b; display: block; margin-bottom: 20rpx; }
    .product-name { font-size: 26rpx; color: #94a3b8; display: block; margin-top: 16rpx; }
    .countdown { font-size: 26rpx; color: #ff9900; display: block; margin-top: 16rpx; &.expired { color: #ee0a24; } }
  }
}

.coupon-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 60rpx;

  .coupon-left {
    flex: 1;
    .coupon-label { font-size: 28rpx; color: #1e293b; font-weight: 500; display: block; }
    .coupon-value { font-size: 24rpx; color: #94a3b8; margin-top: 8rpx; display: block; }
  }

  .coupon-right {
    .discount-tag { color: #ff4544; font-size: 28rpx; font-weight: 600; }
  }
}

.btn-pay {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: linear-gradient(135deg, #ff4544, #e63939);
  color: #ffffff;
  font-weight: bold;
  font-size: 32rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;

  .btn-icon { width: 40rpx; height: 40rpx; }

  &.disabled { background: #e2e8f0; color: #94a3b8; }
}
</style>
