/**
 * 角色切换工具
 * 负责角色切换时的页面跳转和tabBar重置
 */
import { ROLE } from './constants'

/**
 * 各角色首页路径
 */
export const ROLE_HOME_PATH = {
  [ROLE.USER]: '/pages/index/index',
  [ROLE.PLAYER]: '/pages-player/mine/index',
  [ROLE.CS]: '/pages-cs/dashboard/index'
}

/**
 * 各角色tabBar配置（使用static/tabbar/图片）
 */
export const ROLE_TABS = {
  [ROLE.USER]: [
    { pagePath: '/pages/index/index', text: '首页', icon: '/static/tabbar/home.svg', iconActive: '/static/tabbar/home-active.svg' },
    { pagePath: '/pages/category/index', text: '分类', icon: '/static/tabbar/category.svg', iconActive: '/static/tabbar/category-active.svg' },
    { pagePath: '/pages/order/list', text: '订单', icon: '/static/tabbar/order.svg', iconActive: '/static/tabbar/order-active.svg' },
    { pagePath: '/pages/chat/list', text: '消息', icon: '/static/tabbar/chat.svg', iconActive: '/static/tabbar/chat-active.svg' },
    { pagePath: '/pages/mine/index', text: '我的', icon: '/static/tabbar/mine.svg', iconActive: '/static/tabbar/mine-active.svg' }
  ],
  [ROLE.PLAYER]: [
    { pagePath: '/pages-player/hall/index', text: '大厅', icon: '/static/tabbar/player-hall.svg', iconActive: '/static/tabbar/player-hall-active.svg' },
    { pagePath: '/pages-player/order/list', text: '订单', icon: '/static/tabbar/player-order.svg', iconActive: '/static/tabbar/player-order-active.svg' },
    { pagePath: '/pages-player/chat/list', text: '消息', icon: '/static/tabbar/player-chat.svg', iconActive: '/static/tabbar/player-chat-active.svg' },
    { pagePath: '/pages-player/mine/index', text: '我的', icon: '/static/tabbar/player-mine.svg', iconActive: '/static/tabbar/player-mine-active.svg' }
  ],
  [ROLE.CS]: [
    { pagePath: '/pages-cs/dashboard/index', text: '工作台', icon: '/static/tabbar/cs-dashboard.svg', iconActive: '/static/tabbar/cs-dashboard-active.svg' },
    { pagePath: '/pages-cs/order/list', text: '订单', icon: '/static/tabbar/cs-order.svg', iconActive: '/static/tabbar/cs-order-active.svg' },
    { pagePath: '/pages-cs/complaint/list', text: '投诉', icon: '/static/tabbar/cs-complaint.svg', iconActive: '/static/tabbar/cs-complaint-active.svg' },
    { pagePath: '/pages-cs/chat/list', text: '消息', icon: '/static/tabbar/cs-chat.svg', iconActive: '/static/tabbar/cs-chat-active.svg' },
    { pagePath: '/pages-cs/mine/index', text: '我的', icon: '/static/tabbar/cs-mine.svg', iconActive: '/static/tabbar/cs-mine-active.svg' }
  ]
}

/**
 * 切换角色并跳转到对应首页
 */
export function navigateToRole(role) {
  const path = ROLE_HOME_PATH[role]
  if (!path) return
  uni.reLaunch({ url: path })
}
