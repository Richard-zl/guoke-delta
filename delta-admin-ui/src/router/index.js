import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUserInfo } from '@/utils/auth'

const Layout = () => import('@/layout/index.vue')

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer', roles: ['admin', 'cs'] }
      }
    ]
  },
  // 商品管理（仅管理员）
  {
    path: '/product',
    component: Layout,
    redirect: '/product/list',
    meta: { title: '商品管理', icon: 'Goods', roles: ['admin'] },
    children: [
      {
        path: 'list',
        name: 'ProductList',
        component: () => import('@/views/product/ProductList.vue'),
        meta: { title: '商品列表', roles: ['admin'] }
      },
      {
        path: 'category',
        name: 'CategoryList',
        component: () => import('@/views/product/CategoryList.vue'),
        meta: { title: '分类管理', roles: ['admin'] }
      },
      {
        path: 'recommend-category',
        name: 'RecommendCategoryList',
        component: () => import('@/views/product/RecommendCategoryList.vue'),
        meta: { title: '热门分类管理', roles: ['admin'] }
      },
      {
        path: 'form-fields',
        name: 'CategoryFieldList',
        component: () => import('@/views/product/CategoryFieldList.vue'),
        meta: { title: '下单字段配置', roles: ['admin'] }
      },
      {
        path: 'create',
        name: 'ProductCreate',
        component: () => import('@/views/product/ProductForm.vue'),
        meta: { title: '新增商品', roles: ['admin'], hidden: true }
      },
      {
        path: 'edit/:id',
        name: 'ProductEdit',
        component: () => import('@/views/product/ProductForm.vue'),
        meta: { title: '编辑商品', roles: ['admin'], hidden: true }
      }
    ]
  },
  // 订单管理
  {
    path: '/order',
    component: Layout,
    redirect: '/order/list',
    meta: { title: '订单管理', icon: 'List', roles: ['admin', 'cs'] },
    children: [
      {
        path: 'list',
        name: 'OrderList',
        component: () => import('@/views/order/OrderList.vue'),
        meta: { title: '订单列表', roles: ['admin', 'cs'] }
      }
    ]
  },
  // 用户管理
  {
    path: '/user',
    component: Layout,
    redirect: '/user/list',
    meta: { title: '用户管理', icon: 'User', roles: ['admin', 'cs'] },
    children: [
      {
        path: 'list',
        name: 'UserList',
        component: () => import('@/views/user/UserList.vue'),
        meta: { title: '用户列表', roles: ['admin', 'cs'] }
      },
      {
        path: 'spending-rank',
        name: 'SpendingRank',
        component: () => import('@/views/user/SpendingRank.vue'),
        meta: { title: '消费榜单', roles: ['admin', 'cs'] }
      }
    ]
  },
  // 打手管理
  {
    path: '/player',
    component: Layout,
    children: [
      {
        path: '',
        name: 'PlayerList',
        component: () => import('@/views/player/PlayerList.vue'),
        meta: { title: '打手管理', icon: 'Avatar', roles: ['admin', 'cs'] }
      }
    ]
  },
  // 数据统计（仅管理员）
  {
    path: '/statistics',
    component: Layout,
    redirect: '/statistics/income-daily',
    meta: { title: '数据统计', icon: 'DataAnalysis', roles: ['admin'] },
    children: [
      {
        path: 'income-daily',
        name: 'IncomeDaily',
        component: () => import('@/views/statistics/IncomeDaily.vue'),
        meta: { title: '收益日报', roles: ['admin'] }
      }
    ]
  },
  // 投诉处理
  {
    path: '/complaint',
    component: Layout,
    children: [
      {
        path: '',
        name: 'ComplaintList',
        component: () => import('@/views/complaint/ComplaintList.vue'),
        meta: { title: '投诉处理', icon: 'Warning', roles: ['admin', 'cs'] }
      }
    ]
  },
  // 换人申请
  {
    path: '/replace',
    component: Layout,
    children: [
      {
        path: '',
        name: 'ReplaceList',
        component: () => import('@/views/replace/ReplaceList.vue'),
        meta: { title: '换人申请', icon: 'Switch', roles: ['admin', 'cs'] }
      }
    ]
  },
  // 接力申请（打手申请中途换人由客服指定新打手接手）
  {
    path: '/relay',
    component: Layout,
    children: [
      {
        path: '',
        name: 'RelayList',
        component: () => import('@/views/relay/RelayList.vue'),
        meta: { title: '接力审核', icon: 'ArrowRight', roles: ['admin', 'cs'] }
      }
    ]
  },
  // 提现管理（仅管理员）
  {
    path: '/withdraw',
    component: Layout,
    children: [
      {
        path: '',
        name: 'WithdrawList',
        component: () => import('@/views/withdraw/WithdrawList.vue'),
        meta: { title: '提现管理', icon: 'Wallet', roles: ['admin'] }
      }
    ]
  },
  // 客服聊天
  {
    path: '/chat',
    component: Layout,
    redirect: '/chat/service',
    meta: { title: '客服聊天', icon: 'ChatDotRound', roles: ['admin', 'cs'] },
    children: [
      {
        path: 'service',
        name: 'ChatService',
        component: () => import('@/views/chat/ChatService.vue'),
        meta: { title: '在线回复', roles: ['admin', 'cs'] }
      },
      {
        path: 'quick-reply',
        name: 'ChatQuickReply',
        component: () => import('@/views/system/QuickReplyList.vue'),
        meta: { title: '快捷回复', roles: ['admin', 'cs'] }
      },
      {
        path: 'record',
        name: 'ChatRecordList',
        component: () => import('@/views/chat/ChatRecordList.vue'),
        meta: { title: '聊天记录', roles: ['admin', 'cs'] }
      },
      {
        path: 'record/:id',
        name: 'ChatRecordDetail',
        component: () => import('@/views/chat/ChatRecordDetail.vue'),
        meta: { title: '聊天记录详情', roles: ['admin', 'cs'], hidden: true }
      }
    ]
  },
  // 系统管理 (仅 admin)
  {
    path: '/system',
    component: Layout,
    redirect: '/system/admin',
    meta: { title: '系统管理', icon: 'Setting', roles: ['admin'] },
    children: [
      {
        path: 'admin',
        name: 'AdminManage',
        component: () => import('@/views/system/AdminList.vue'),
        meta: { title: '管理员管理', roles: ['admin'] }
      },
      {
        path: 'notice',
        name: 'NoticeManage',
        component: () => import('@/views/system/NoticeList.vue'),
        meta: { title: '公告管理', roles: ['admin'] }
      },
      {
        path: 'banner',
        name: 'BannerManage',
        component: () => import('@/views/system/BannerList.vue'),
        meta: { title: '轮播图管理', roles: ['admin'] }
      },
      {
        path: 'config',
        name: 'SysConfig',
        component: () => import('@/views/system/SysConfig.vue'),
        meta: { title: '系统配置', roles: ['admin'] }
      },
      {
        path: 'operation-log',
        name: 'OperationLog',
        component: () => import('@/views/system/OperationLog.vue'),
        meta: { title: '操作日志', roles: ['admin'] }
      }
    ]
  },
  // 403
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限' }
  },
  // 404
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
const whiteList = ['/login']
router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || ''} - 护航管理后台`
  const token = getToken()

  if (token) {
    if (to.path === '/login') {
      next('/')
    } else {
      const user = getUserInfo()
      const roles = to.meta.roles
      if (roles && !roles.includes(user?.role)) {
        next('/403')
      } else {
        next()
      }
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

export default router
