/**
 * 统一请求封装
 * 自动注入token、统一错误处理、loading管理
 */
import { getTokenByRole } from '@/utils/auth'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const IS_DEV = import.meta.env.DEV

/** 仅 dev 模式下打印日志 */
function devLog(...args) {
  if (IS_DEV) console.log(...args)
}

let loadingCount = 0
let _handlingUnauthorized = false  // 防止多个401同时触发重复弹出跳转

function showLoading() {
  if (loadingCount === 0) {
    uni.showLoading({ title: '加载中...', mask: true })
  }
  loadingCount++
}

function hideLoading() {
  loadingCount--
  if (loadingCount <= 0) {
    loadingCount = 0
    try {
      uni.hideLoading()
    } catch (e) {
      // 真机下若 toast 已被系统关闭（如弹出订阅窗时），hideLoading 会报错，忽略即可
    }
  }
}

/**
 * 获取当前角色（从Storage读取，避免循环依赖store）
 */
function getCurrentRole() {
  return uni.getStorageSync('app_role') || 'user'
}

/**
 * 核心请求函数
 * @param {Object} options
 * @param {string} options.url - 接口路径（不含baseURL）
 * @param {string} options.method - GET/POST/PUT/DELETE
 * @param {Object} options.data - 请求参数
 * @param {boolean} options.loading - 是否显示loading，默认true
 * @param {boolean} options.auth - 是否需要鉴权，默认true
 * @param {string} options.role - 指定角色获取token，默认当前角色
 */
export function request(options) {
  const {
    url,
    method = 'GET',
    data = {},
    loading = true,
    auth = true,
    role,
    header = {}
  } = options

  return new Promise((resolve, reject) => {
    let loadingHandled = false
    const finishLoading = () => {
      if (loading && !loadingHandled) {
        loadingHandled = true
        hideLoading()
      }
    }

    if (loading) showLoading()

    // 注入token
    const currentRole = role || getCurrentRole()
    if (auth) {
      const token = getTokenByRole(currentRole)
      if (token) {
        header['Authorization'] = `Bearer ${token}`
      }
    }

    const _startTime = Date.now()
    let finalUrl = `${BASE_URL}${url}`
    let bodyData = data
    if (method === 'GET' && data && Object.keys(data).length > 0) {
      const qs = Object.entries(data)
        .filter(([, v]) => v !== undefined && v !== null && v !== '')
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
        .join('&')
      if (qs) finalUrl += (url.includes('?') ? '&' : '?') + qs
      bodyData = {}
    }
    devLog(
      `%c[REQ >>>] ${method} ${finalUrl}`,
      'color:#1890ff;font-weight:bold',
      ...(method !== 'GET' && data && Object.keys(data).length > 0 ? ['\nBody:', data] : [])
    )
    uni.request({
      url: finalUrl,
      method,
      data: bodyData,
      header: {
        'Content-Type': 'application/json',
        ...header
      },
      success(res) {
        const elapsed = Date.now() - _startTime
        const { statusCode, data: resData } = res
        const isOk = statusCode === 200 && (resData?.code === 0 || resData?.code === 200)
        devLog(
          `%c[REQ <<<] ${method} ${url} ${elapsed}ms [${statusCode}]`,
          isOk ? 'color:#52c41a;font-weight:bold' : 'color:#ff4d4f;font-weight:bold',
          '\nResponse:', resData
        )

        if (statusCode === 200) {
          // 业务层code判断
          if (resData.code === 0 || resData.code === 200) {
            resolve(resData)
          } else if (resData.code === 401) {
            // token失效
            finishLoading()
            handleUnauthorized(currentRole)
            reject(resData)
          } else {
            // 业务错误
            finishLoading()
            uni.showToast({ title: resData.msg || '请求失败', icon: 'none' })
            reject(resData)
          }
        } else if (statusCode === 401) {
          finishLoading()
          handleUnauthorized(currentRole)
          reject(resData)
        } else {
          finishLoading()
          uni.showToast({ title: `网络错误(${statusCode})`, icon: 'none' })
          reject(resData)
        }
      },
      fail(err) {
        devLog(`%c[REQ ERR] ${method} ${url}`, 'color:#ff4d4f;font-weight:bold', '\nError:', err)
        finishLoading()
        uni.showToast({ title: '网络连接失败', icon: 'none' })
        reject(err)
      },
      complete() {
        finishLoading()
      }
    })
  })
}

/**
 * 处理未授权
 */
function handleUnauthorized(role) {
  // 防止多个并发401请求重复触发弹出和跳转
  if (_handlingUnauthorized) return
  _handlingUnauthorized = true

  if (role === 'cs') {
    uni.removeStorageSync('cs_token')
  } else if (role === 'player') {
    uni.removeStorageSync('player_token')
  } else {
    uni.removeStorageSync('user_token')
  }

  uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })

  setTimeout(() => {
    _handlingUnauthorized = false
    if (role === 'cs') {
      uni.reLaunch({ url: '/pages/login/index' })
    } else if (role === 'player') {
      uni.setStorageSync('app_role', 'user')
      uni.reLaunch({ url: '/pages/mine/index' })
    } else {
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }, 1500)
}

/**
 * 文件上传
 */
export function uploadFile(filePath) {
  return new Promise((resolve, reject) => {
    const role = getCurrentRole()
    const token = getTokenByRole(role)
    const header = {}
    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    uni.uploadFile({
      url: `${BASE_URL}/common/file/upload`,
      filePath,
      name: 'file',
      header,
      success(res) {
        if (res.statusCode === 200) {
          const data = JSON.parse(res.data)
          if (data.code === 0 || data.code === 200) {
            resolve(data.data) // 返回文件URL
          } else {
            uni.showToast({ title: data.msg || '上传失败', icon: 'none' })
            reject(data)
          }
        } else {
          reject(res)
        }
      },
      fail(err) {
        uni.showToast({ title: '上传失败', icon: 'none' })
        reject(err)
      }
    })
  })
}

// 快捷方法
export const get = (url, data, options = {}) => request({ url, method: 'GET', data, ...options })
export const post = (url, data, options = {}) => request({ url, method: 'POST', data, ...options })
export const put = (url, data, options = {}) => request({ url, method: 'PUT', data, ...options })
export const del = (url, data, options = {}) => request({ url, method: 'DELETE', data, ...options })
