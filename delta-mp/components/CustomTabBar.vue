<template>
  <view class="custom-tabbar" :style="{ paddingBottom: safeBottom + 'px' }">
    <view
      v-for="(tab, index) in currentTabs"
      :key="tab.pagePath"
      class="tabbar-item"
      :class="{ active: currentIndex === index }"
      @click="switchTab(tab, index)"
    >
      <view class="tabbar-icon">
        <image class="icon-img" :src="currentIndex === index ? tab.iconActive : tab.icon" mode="aspectFit" />
        <view v-if="tabBadgeCount(tab) > 0" class="badge">
          {{ tabBadgeCount(tab) > 99 ? '99+' : tabBadgeCount(tab) }}
        </view>
        <view v-else-if="tabRedDot(tab)" class="red-dot" />
      </view>
      <text class="tabbar-text">{{ tab.text }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useChatStore } from '@/store/chat'
import { useRemindStore } from '@/store/remind'
import { useAuditMode } from '@/composables/useAuditMode'
import { useSiteStore } from '@/store/site'
import { ROLE_TABS } from '@/utils/role'

const props = defineProps({
  current: { type: Number, default: 0 }
})

const appStore = useAppStore()
const chatStore = useChatStore()
const remindStore = useRemindStore()
const siteStore = useSiteStore()
const { isUnderReview } = useAuditMode()
const safeBottom = ref(0)

/** 审核期用户端：隐藏「订单」「消息」，「分类」改为「资讯」 */
const currentTabs = computed(() => {
  const tabs = ROLE_TABS[appStore.role] || ROLE_TABS.user
  if (siteStore.configLoaded && appStore.role === 'user' && isUnderReview.value) {
    return tabs
      .filter(tab => tab.pagePath !== '/pages/order/list' && tab.pagePath !== '/pages/chat/list')
      .map(tab => {
        if (tab.pagePath === '/pages/category/index') {
          return { ...tab, text: '资讯' }
        }
        return tab
      })
  }
  return tabs
})

/** 根据当前页面路由自动计算高亮索引 */
const currentIndex = computed(() => {
  const pages = getCurrentPages()
  if (pages.length) {
    const route = '/' + (pages[pages.length - 1].route || '')
    const idx = currentTabs.value.findIndex(t => t.pagePath === route)
    if (idx >= 0) return idx
  }
  return props.current
})

/** 我的 tab 是否有系统消息未读（显示小红点） */
const mineRedDot = computed(() => {
  const role = appStore.role
  if (role === 'user') return (chatStore.messageUnreadCount || 0) > 0
  return (remindStore.systemUnread || 0) > 0
})

function badgeCount(tab) {
  const role = appStore.role
  if (tab.text === '消息') {
    if (role === 'user') return chatStore.totalUnreadCount
    if (role === 'cs' || role === 'player') return remindStore.messageUnread
  }
  if (role === 'cs' && tab.text === '投诉') return remindStore.complaintUnread
  return 0
}

function tabBadgeCount(tab) {
  return badgeCount(tab)
}

function tabRedDot(tab) {
  if (tab.text === '我的') return mineRedDot.value
  if (appStore.role === 'cs' && tab.text === '工作台') return (remindStore.relayUnread || 0) > 0
  return false
}

onMounted(async () => {
  if (!siteStore.configLoaded) await siteStore.fetchSiteConfig()
  const windowInfo = uni.getWindowInfo()
  safeBottom.value = windowInfo.safeAreaInsets?.bottom || 0
  lockH5PageScroll()
})

onShow(() => {
  lockH5PageScroll()
})

onHide(() => {
  unlockH5PageScroll()
})

onUnmounted(() => {
  unlockH5PageScroll()
})

function lockH5PageScroll() {
  // #ifdef H5
  document.documentElement.classList.add('tabbar-page-locked')
  document.body.classList.add('tabbar-page-locked')
  // #endif
}

function unlockH5PageScroll() {
  // #ifdef H5
  document.documentElement.classList.remove('tabbar-page-locked')
  document.body.classList.remove('tabbar-page-locked')
  // #endif
}

const TAB_BAR_PAGES = [
  '/pages/index/index',
  '/pages/category/index',
  '/pages/order/list',
  '/pages/chat/list',
  '/pages/mine/index'
]

function switchTab(tab, index) {
  if (currentIndex.value === index) return
  if (TAB_BAR_PAGES.includes(tab.pagePath)) {
    uni.switchTab({ url: tab.pagePath })
  } else {
    uni.redirectTo({ url: tab.pagePath })
  }
}
</script>

<style lang="scss" scoped>
.custom-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  height: 110rpx;
  box-sizing: content-box;
  background: rgba(255, 255, 255, 0.98);
  border-top: 1rpx solid #e2e8f0;
  backdrop-filter: blur(20rpx);
  z-index: 999;
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;

  .tabbar-icon {
    position: relative;

    .icon-img {
      width: 48rpx;
      height: 48rpx;
    }

    .badge {
      position: absolute;
      top: -10rpx;
      right: -20rpx;
      min-width: 32rpx;
      height: 32rpx;
      line-height: 32rpx;
      padding: 0 8rpx;
      font-size: 20rpx;
      color: #fff;
      background: #ee0a24;
      border-radius: 32rpx;
      text-align: center;
    }
    .red-dot {
      position: absolute;
      top: -4rpx;
      right: -4rpx;
      width: 16rpx;
      height: 16rpx;
      background: #ee0a24;
      border-radius: 50%;
      border: 2rpx solid #fff;
    }
  }

  .tabbar-text {
    font-size: 20rpx;
    color: #94a3b8;
    margin-top: 4rpx;
  }

  &.active {
    .tabbar-icon {
      color: #ff4544;
      .icon-img {
        filter: brightness(0) saturate(100%) invert(27%) sepia(51%) saturate(2878%) hue-rotate(346deg) brightness(104%) contrast(101%);
      }
    }
    .tabbar-text {
      color: #ff4544;
      font-weight: bold;
    }
  }
}
</style>
