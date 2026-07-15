<script setup>
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { useChatStore } from '@/store/chat'
import { usePlayerStore } from '@/store/player'
import { useRemindStore } from '@/store/remind'
import { useSiteStore } from '@/store/site'
import { getTokenByRole } from '@/utils/auth'
import { setupAuditRouteInterceptor } from '@/composables/useAuditGuard'

function shouldConnectChat() {
  const role = uni.getStorageSync('app_role') || 'user'
  const token = getTokenByRole(role)
  return !!token
}

function syncRemindPolling() {
  const appStore = useAppStore()
  const remindStore = useRemindStore()
  if (!shouldConnectChat()) {
    remindStore.clear()
    return
  }
  if (appStore.role === 'cs' || appStore.role === 'player') {
    remindStore.startPolling(appStore.role)
    return
  }
  remindStore.stopPolling()
}

onLaunch(() => {
  setupAuditRouteInterceptor()
  const appStore = useAppStore()
  const userStore = useUserStore()
  const playerStore = usePlayerStore()
  const siteStore = useSiteStore()
  const chatStore = useChatStore()
  useRemindStore()

  siteStore.fetchSiteConfig()
  appStore.restoreRole()

  if (userStore.token) {
    userStore.fetchProfile()
    if (appStore.role === 'player') {
      playerStore.fetchProfile()
    }
  }
  if (shouldConnectChat()) {
    setTimeout(() => { chatStore.connect() }, 300)
  }
  syncRemindPolling()
})

onShow(() => {
  const chatStore = useChatStore()
  if (shouldConnectChat()) {
    if (!chatStore.connected) chatStore.connect()
    chatStore.fetchMessageUnreadCount()
  }
  syncRemindPolling()
})
</script>

<style lang="scss">
@import '@/uni.scss';

page, view, text, image, scroll-view, textarea, input { box-sizing: border-box; }

page {
  background-color: #f1f5f9;
  font-size: 28rpx;
  color: #1e293b;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 安全区域底部留白（为自定义tabBar腾出空间） */
.safe-area-bottom {
  padding-bottom: calc(env(safe-area-inset-bottom) + 120rpx);
}

.tab-page {
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  padding-bottom: 0 !important;
}

.tab-page-scroll {
  height: calc(100vh - 110rpx - env(safe-area-inset-bottom));
  position: relative;
  z-index: 1;
  box-sizing: border-box;
}

.tab-page-bottom-spacer {
  height: 48rpx;
  flex-shrink: 0;
}

/* #ifdef H5 */
html.tabbar-page-locked,
body.tabbar-page-locked {
  height: 100%;
  overflow: hidden !important;
}

html.tabbar-page-locked uni-app,
html.tabbar-page-locked uni-page,
html.tabbar-page-locked uni-page-wrapper,
html.tabbar-page-locked uni-page-body,
html.tabbar-page-locked #app {
  height: 100%;
  overflow: hidden !important;
}

.tab-page-scroll .uni-scroll-view::-webkit-scrollbar,
.tab-page-scroll .uni-scroll-view-content::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}
/* #endif */

/* 金沙粒子背景画布 */
.gold-dust-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

/* 通用flex布局 */
.flex-row { display: flex; flex-direction: row; align-items: center; }
.flex-col { display: flex; flex-direction: column; }
.flex-1 { flex: 1; }
.flex-center { display: flex; align-items: center; justify-content: center; }

/* 文字溢出省略 */
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.text-ellipsis-2 {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
