<template>
  <div class="layout-wrap" :class="{ 'is-staging': isStaging }">
    <EnvBanner class="layout-env-banner" />
    <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <div class="logo-mark">{{ logoInitial }}</div>
        <h1 v-if="!isCollapse">{{ siteStore.adminTitle }}</h1>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :router="true"
        class="layout-menu"
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <!-- 单级菜单 -->
          <el-menu-item
            v-if="route.children?.length === 1 && !route.children[0].children"
            :index="resolveMenuPath(route.path, route.children[0].path)"
          >
            <el-icon><component :is="route.children[0].meta?.icon || route.meta?.icon" /></el-icon>
            <template #title>{{ route.children[0].meta?.title }}</template>
          </el-menu-item>
          <!-- 多级菜单 -->
          <el-sub-menu v-else :index="route.path">
            <template #title>
              <el-icon><component :is="route.meta?.icon" /></el-icon>
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in visibleChildren(route.children)"
              :key="child.path"
              :index="route.path + '/' + child.path"
            >
              {{ child.meta?.title }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <button class="collapse-btn" type="button" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </button>
          <!-- 面包屑 -->
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta?.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag v-if="isStaging" type="warning" effect="dark" size="small" class="staging-tag">测试环境</el-tag>
          <div class="chat-notify" @click="goChat" title="客服消息">
            <el-icon :size="20"><ChatDotRound /></el-icon>
            <span v-if="chatStore.globalUnread > 0" class="notify-badge">{{ chatStore.globalUnread > 99 ? '99+' : chatStore.globalUnread }}</span>
          </div>
          <el-tag :type="role === 'admin' ? 'danger' : 'warning'" size="small" class="role-tag">
            {{ role === 'admin' ? '管理员' : '客服' }}
          </el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar v-if="userStore.avatar" :src="userStore.avatar" :size="28" style="margin-right:8px" />
              {{ nickname }} <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">编辑资料</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 编辑资料弹窗 -->
    <ProfileEditDialog v-model="profileDialogVisible" @saved="onProfileSaved" />
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Fold, Expand, ArrowDown, ChatDotRound } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import ProfileEditDialog from '@/components/ProfileEditDialog.vue'
import EnvBanner from '@/components/EnvBanner.vue'
import { useChatStore } from '@/stores/chat'
import { useSiteStore } from '@/stores/site'
import { isStagingEnv } from '@/utils/env'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()
const siteStore = useSiteStore()

const isStaging = isStagingEnv()
const isCollapse = ref(false)
const nickname = computed(() => userStore.nickname)
const role = computed(() => userStore.role)
const logoInitial = computed(() => {
  const title = siteStore.adminTitle || siteStore.siteName || '护'
  return title.trim().slice(0, 1)
})

const activeMenu = computed(() => route.path)
const breadcrumbs = computed(() => route.matched.filter((r) => r.meta?.title))

// 拼接菜单路径，避免双斜杠
function resolveMenuPath(parentPath, childPath) {
  if (!childPath) return parentPath
  return (parentPath + '/' + childPath).replace(/\/+/g, '/')
}

// 根据角色过滤路由，生成菜单
const menuRoutes = computed(() => {
  return router.options.routes.filter((r) => {
    if (!r.children) return false
    if (r.path === '/login' || r.path === '/403' || r.path.includes(':pathMatch')) return false
    const roles = r.meta?.roles || r.children?.[0]?.meta?.roles
    return !roles || roles.includes(role.value)
  })
})

function visibleChildren(children) {
  return (children || []).filter((c) => !c.meta?.hidden)
}

const profileDialogVisible = ref(false)

function handleCommand(cmd) {
  if (cmd === 'profile') {
    profileDialogVisible.value = true
  } else if (cmd === 'logout') {
    chatStore.stop()
    userStore.logout()
    router.push('/login')
  }
}

function onProfileSaved(data) {
  userStore.updateProfile(data)
}

function goChat() {
  chatStore.clearUnread()
  router.push('/chat/service')
}

onMounted(() => {
  chatStore.start()
})
onUnmounted(() => {
  chatStore.stop()
})
</script>

<style lang="scss" scoped>
.layout-wrap {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f4f7fb;
}
.layout-env-banner {
  flex-shrink: 0;
}
.layout-container {
  flex: 1;
  min-height: 0;
  background: #f4f7fb;
}
.is-staging .layout-aside .logo {
  box-shadow: inset 0 -2px 0 #d97706;
}
.staging-tag {
  font-weight: 700;
}
.layout-aside {
  background: #172033;
  box-shadow: 8px 0 28px rgba(20, 32, 54, 0.14);
  transition: width 0.24s ease;
  overflow: hidden;
  .logo {
    height: 64px;
    padding: 0 16px;
    display: flex;
    align-items: center;
    gap: 10px;
    color: #f8fafc;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    .logo-mark {
      width: 34px;
      height: 34px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      color: #172033;
      font-size: 17px;
      font-weight: 800;
      background: linear-gradient(135deg, #f7c948, #f59e0b);
      box-shadow: 0 10px 24px rgba(245, 158, 11, 0.24);
    }
    h1 {
      font-size: 15px;
      line-height: 1.2;
      font-weight: 700;
      margin: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}
.layout-menu {
  border-right: none;
  height: calc(100vh - 64px);
  overflow-y: auto;
  padding: 12px 10px 18px;
  background: transparent;

  :deep(.el-menu) {
    background: transparent;
  }
  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    height: 42px;
    margin: 4px 0;
    border-radius: 9px;
    color: #aeb9cc;
    transition: background 0.18s ease, color 0.18s ease;
  }
  :deep(.el-menu-item:hover),
  :deep(.el-sub-menu__title:hover) {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.08);
  }
  :deep(.el-menu-item.is-active) {
    color: #172033;
    font-weight: 700;
    background: linear-gradient(135deg, #f7c948, #f59e0b);
    box-shadow: 0 10px 22px rgba(245, 158, 11, 0.18);
  }
  :deep(.el-sub-menu .el-menu-item) {
    padding-left: 42px !important;
  }
  :deep(.el-menu--collapse .el-sub-menu__title),
  :deep(.el-menu--collapse .el-menu-item) {
    justify-content: center;
  }
}
.layout-header {
  height: 64px;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid #e7edf5;
  box-shadow: 0 8px 24px rgba(20, 32, 54, 0.05);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
    .collapse-btn {
      width: 34px;
      height: 34px;
      border: 1px solid #dce5f0;
      border-radius: 8px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: #42526e;
      background: #fff;
      cursor: pointer;
      transition: all 0.18s ease;
      &:hover {
        color: #1d4ed8;
        border-color: #bfdbfe;
        background: #eff6ff;
      }
      svg {
        width: 18px;
        height: 18px;
      }
    }
    :deep(.el-breadcrumb__inner) {
      color: #64748b;
      font-weight: 500;
    }
    :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
      color: #172033;
      font-weight: 700;
    }
  }
  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;
    .chat-notify {
      position: relative;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      transition: background 0.2s;
      color: #475569;
      &:hover {
        background: #eff6ff;
        color: #1d4ed8;
      }
      .notify-badge {
        position: absolute;
        top: -4px;
        right: -8px;
        min-width: 16px;
        height: 16px;
        line-height: 16px;
        padding: 0 4px;
        font-size: 10px;
        color: #fff;
        background: #f56c6c;
        border-radius: 10px;
        text-align: center;
      }
    }
    .role-tag {
      margin-right: 4px;
    }
    .user-info {
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      min-height: 34px;
      padding: 0 10px 0 6px;
      border-radius: 999px;
      color: #172033;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
    }
  }
}
.layout-main {
  height: calc(100% - 64px);
  overflow: auto;
  background:
    linear-gradient(180deg, rgba(234, 241, 250, 0.82) 0%, rgba(244, 247, 251, 0) 210px),
    #f4f7fb;
  padding: 22px 24px 28px;
}
</style>
