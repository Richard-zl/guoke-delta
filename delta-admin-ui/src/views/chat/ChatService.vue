<template>
  <div class="chat-service">
    <!-- 左侧会话列表 -->
    <div class="session-panel">
      <div class="panel-header">
        <span>客服会话</span>
        <el-button link type="primary" @click="fetchSessions">刷新</el-button>
      </div>
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: currentSession?.id === s.id }"
          @click="selectSession(s)"
        >
          <div class="session-row">
            <el-avatar :src="s.avatar" :size="38" class="session-avatar">{{ (s.targetName || '')[0] || '用' }}</el-avatar>
            <div class="session-body">
              <div class="session-top">
                <span class="session-name">{{ s.targetName || '用户 #' + s.id }}</span>
                <span class="session-time">{{ formatTime(s.lastMessageAt) }}</span>
              </div>
              <div class="session-bottom">
                <span class="session-last-msg">{{ s.lastMessage || '暂无消息' }}</span>
                <span v-if="s.unreadCount > 0 && currentSession?.id !== s.id" class="unread-badge">{{ s.unreadCount > 99 ? '99+' : s.unreadCount }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="sessions.length === 0" class="empty-tip">暂无会话</div>
      </div>
    </div>

    <!-- 右侧聊天区 -->
    <div class="chat-panel">
      <template v-if="currentSession">
        <div class="chat-header">
          <span>{{ currentSession.targetName || '用户 #' + currentSession.id }}</span>
          <el-tag size="small">{{ typeMap[currentSession.type] || currentSession.type }}</el-tag>
          <span v-if="currentSession.orderId && currentSession.orderId !== 0" class="order-link">
            关联订单: {{ currentSession.orderId }}
          </span>
        </div>

        <div class="chat-messages" ref="msgContainer">
          <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ self: isSelf(m) }">
            <div class="msg-bubble" :class="{ self: isSelf(m) }">
              <!-- 文本 -->
              <template v-if="m.type === 'TEXT'">{{ m.content }}</template>
              <!-- 图片 -->
              <el-image v-else-if="m.type === 'IMAGE'" :src="m.content" style="max-width:200px;border-radius:4px" fit="cover" :preview-src-list="[m.content]" />
              <!-- 商品卡片 -->
              <div v-else-if="m.type === 'PRODUCT'" class="card-msg" @click="showProductDetail(parseJSON(m.content))">
                <el-image v-if="parseJSON(m.content).coverImage" :src="parseJSON(m.content).coverImage" class="card-cover" fit="cover" />
                <div class="card-info">
                  <div class="card-name">{{ parseJSON(m.content).name }}</div>
                  <div class="card-price">¥{{ Number(parseJSON(m.content).price || 0).toFixed(2) }}</div>
                </div>
                <el-tag size="small" type="success" class="card-tag">商品</el-tag>
              </div>
              <!-- 订单卡片 -->
              <div v-else-if="m.type === 'ORDER'" class="card-msg">
                <div class="card-info" style="flex:1" @click="showOrderDetail(parseJSON(m.content))">
                  <div class="card-name">{{ parseJSON(m.content).productName || parseJSON(m.content).orderNo }}</div>
                  <div class="card-meta">
                    <span class="card-price">¥{{ Number(parseJSON(m.content).amount || 0).toFixed(2) }}</span>
                    <el-tag size="small">{{ orderStatusMap[parseJSON(m.content).status] || parseJSON(m.content).status }}</el-tag>
                  </div>
                  <div class="card-sub">{{ parseJSON(m.content).orderNo }}</div>
                </div>
                <div class="card-actions">
                  <el-tag size="small" type="warning" class="card-tag">订单</el-tag>
                  <el-button v-if="!isSelf(m)" size="small" type="danger" plain class="card-complaint-btn" @click.stop="openComplaintFromOrder(parseJSON(m.content))">创建投诉</el-button>
                </div>
              </div>
              <!-- 系统消息 -->
              <template v-else>{{ m.content }}</template>
            </div>
            <div class="msg-time">{{ formatTime(m.createdAt) }}</div>
          </div>
          <div v-if="messages.length === 0" class="empty-tip" style="padding:40px 0">暂无消息</div>
        </div>

        <!-- 快捷回复栏 -->
        <div v-if="showQuickReply" class="quick-reply-bar">
          <div class="qr-header">
            <span>快捷回复</span>
            <el-button link size="small" @click="showQuickReply = false">收起</el-button>
          </div>
          <div class="qr-list">
            <div v-for="qr in quickReplies" :key="qr.id" class="qr-item" @click="useQuickReply(qr)">
              {{ qr.content }}
            </div>
            <div v-if="quickReplies.length === 0" class="qr-empty">暂无快捷回复，请在系统管理中添加</div>
          </div>
        </div>
        <div class="chat-input">
          <el-button @click="toggleQuickReply" :type="showQuickReply ? 'warning' : ''" plain size="small">快捷</el-button>
          <el-button plain size="small" @click="pickImage">🖼️ 图片</el-button>
          <el-button plain size="small" @click="openProductPicker">🛒 商品</el-button>
          <el-button v-if="currentSession?.type === 'USER_CS'" plain size="small" type="danger" @click="openComplaintFromChat">📋 投诉</el-button>
          <el-input
            v-model="inputText"
            placeholder="输入回复内容..."
            @keyup.enter="sendText"
            :disabled="sending"
          />
          <el-button type="primary" @click="sendText" :loading="sending">发送</el-button>
          <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onFileChange" />
        </div>

        <!-- 商品选择弹窗 -->
        <el-dialog v-model="productPickerVisible" title="选择商品发送" width="560px" destroy-on-close>
          <div class="picker-search">
            <el-input v-model="pickerKeyword" placeholder="搜索商品名称..." clearable prefix-icon="Search" />
          </div>
          <div class="picker-grid" v-loading="pickerLoading">
            <div
              v-for="p in filteredPickerProducts"
              :key="p.id"
              class="picker-card"
              @click="doSendProduct(p)"
            >
              <div class="picker-card-cover">
                <el-image v-if="p.coverImage" :src="p.coverImage" fit="cover" style="width:100%;height:100%" />
                <div v-else class="picker-card-noimg">暂无图片</div>
              </div>
              <div class="picker-card-body">
                <div class="picker-card-name">{{ p.name }}</div>
                <div class="picker-card-bottom">
                  <span class="picker-card-price">¥{{ Number(p.price||0).toFixed(2) }}</span>
                  <el-button type="primary" size="small" plain>发送</el-button>
                </div>
              </div>
            </div>
            <div v-if="!pickerLoading && filteredPickerProducts.length===0" class="picker-empty">
              <el-empty description="暂无商品" :image-size="80" />
            </div>
          </div>
        </el-dialog>
      </template>
      <div v-else class="empty-chat">
        <el-empty description="选择一个会话开始回复" />
      </div>
    </div>

    <!-- 商品详情弹窗（完整版） -->
    <el-dialog v-model="productDialogVisible" title="商品详情" width="560px" destroy-on-close>
      <div v-loading="productDetailLoading">
        <div v-if="productDetail" class="product-full">
          <!-- 封面 + 图片轮播 -->
          <div class="product-gallery">
            <el-carousel v-if="productAllImages.length > 1" height="280px" indicator-position="outside">
              <el-carousel-item v-for="(img, i) in productAllImages" :key="i">
                <el-image :src="img" fit="contain" style="width:100%;height:280px" :preview-src-list="productAllImages" :initial-index="i" />
              </el-carousel-item>
            </el-carousel>
            <el-image v-else-if="productAllImages.length === 1" :src="productAllImages[0]" fit="contain" style="width:100%;height:280px;border-radius:8px" :preview-src-list="productAllImages" />
            <div v-else class="no-image">暂无图片</div>
          </div>
          <!-- 信息区 -->
          <div class="product-main">
            <div class="product-title">{{ productDetail.name }}</div>
            <div class="product-price-row">
              <span class="price-val">¥{{ Number(productDetail.price || 0).toFixed(2) }}</span>
              <el-tag v-if="productDetail.isRecommend === 1" type="danger" size="small">推荐</el-tag>
              <el-tag :type="productDetail.status === 1 ? 'success' : 'info'" size="small">{{ productDetail.status === 1 ? '上架中' : '已下架' }}</el-tag>
            </div>
          </div>
          <el-descriptions :column="2" border size="small" class="product-desc-table">
            <el-descriptions-item label="分类ID">{{ productDetail.categoryId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="排序">{{ productDetail.sortOrder ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="销量">{{ productDetail.salesCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="评分">{{ productDetail.avgRating ? Number(productDetail.avgRating).toFixed(1) + ' 分' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="评价数">{{ productDetail.reviewCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ productDetail.createdAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              <div class="product-description">{{ productDetail.description || '暂无描述' }}</div>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-dialog>

    <!-- 投诉工单创建弹窗 -->
    <el-dialog v-model="complaintVisible" title="创建投诉工单" width="550px" destroy-on-close>
      <el-form ref="complaintFormRef" :model="complaintForm" :rules="complaintRules" label-width="90px">
        <el-form-item label="用户">
          <div class="selected-info">
            <el-avatar :src="complaintForm._userAvatar" :size="28">{{ (complaintForm._userName || '')[0] || '用' }}</el-avatar>
            <span>{{ complaintForm._userName || '-' }}</span>
            <el-tag size="small" type="info">ID: {{ complaintForm.userId }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="关联订单" prop="orderId">
          <el-select
            v-model="complaintForm.orderId"
            placeholder="选择订单"
            filterable
            style="width:100%"
            :loading="complaintOrdersLoading"
            @change="onComplaintOrderChange"
          >
            <el-option v-for="o in complaintOrders" :key="o.id" :value="o.id" :label="o.orderNo + ' - ' + o.productName">
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span>{{ o.orderNo }} - {{ o.productName }}</span>
                <span style="font-size:12px;color:#999">¥{{ o.amount }} · {{ orderStatusMap[o.status] || o.status }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="投诉类型" prop="type">
          <el-select v-model="complaintForm.type" placeholder="选择类型">
            <el-option label="服务态度" value="服务态度" /><el-option label="代练质量" value="代练质量" />
            <el-option label="账号安全" value="账号安全" /><el-option label="违规操作" value="违规操作" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="投诉内容" prop="content">
          <el-input v-model="complaintForm.content" type="textarea" :rows="4" placeholder="描述投诉内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="complaintVisible = false">取消</el-button>
        <el-button type="primary" :loading="complaintSubmitting" @click="submitComplaint">创建</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情弹窗（含进度时间线 + 打手/用户信息） -->
    <el-dialog v-model="orderDialogVisible" title="订单详情" width="720px" destroy-on-close>
      <div v-loading="orderDetailLoading">
        <template v-if="orderDetail">
          <!-- 用户 & 打手信息卡片 -->
          <div class="order-people">
            <div class="person-card">
              <el-avatar :src="orderDetail.userAvatar" :size="42">用户</el-avatar>
              <div class="person-info">
                <div class="person-label">下单用户</div>
                <div class="person-name">{{ orderDetail.userNickname || ('ID: ' + orderDetail.userId) }}</div>
              </div>
            </div>
            <div class="person-arrow">→</div>
            <div class="person-card">
              <el-avatar :src="orderDetail.playerAvatar" :size="42">打手</el-avatar>
              <div class="person-info">
                <div class="person-label">服务打手</div>
                <div class="person-name">{{ orderDetail.playerName || (orderDetail.playerId ? 'ID: ' + orderDetail.playerId : '未指派') }}</div>
              </div>
            </div>
          </div>

          <!-- 订单基本信息 -->
          <el-descriptions :column="2" border size="small" style="margin-top:16px">
            <el-descriptions-item label="订单号">{{ orderDetail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="orderTagType(orderDetail.status)" size="small">{{ orderStatusMap[orderDetail.status] || orderDetail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="商品">{{ orderDetail.productName }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ orderDetail.specInfo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="金额"><span style="color:#e6a23c;font-weight:bold">¥{{ orderDetail.amount }}</span></el-descriptions-item>
            <el-descriptions-item label="结算状态">{{ orderDetail.settled === 1 ? '已结算' : '未结算' }}</el-descriptions-item>
            <el-descriptions-item label="游戏账号">{{ orderDetail.gameAccount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系方式">{{ orderDetail.contact || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ orderDetail.remark || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 操作按钮 -->
          <div style="margin-top:16px;text-align:right">
            <el-button
              v-if="orderDetail.status === 'COMPLETED'"
              type="primary"
              :loading="orderConfirmLoading"
              @click="handleOrderConfirm"
            >
              结单
            </el-button>
            <el-button type="danger" plain @click="openComplaintFromOrderDetail">创建投诉工单</el-button>
          </div>

          <!-- 订单进度时间线 -->
          <div class="order-progress-section">
            <div class="section-title">订单进度</div>
            <el-timeline v-if="orderProgressList.length > 0">
              <el-timeline-item
                v-for="p in orderProgressList"
                :key="p.id"
                :timestamp="formatTime(p.createdAt)"
                :type="progressNodeType(p)"
                placement="top"
              >
                <div class="progress-content">{{ p.content }}</div>
                <div v-if="p.remark" class="progress-remark">{{ p.remark }}</div>
                <div v-if="p.images" class="progress-images">
                  <el-image
                    v-for="(img, imgIdx) in p.images.split(',')"
                    :key="img + imgIdx"
                    :src="img"
                    :preview-src-list="p.images.split(',')"
                    :initial-index="imgIdx"
                    fit="cover"
                    class="progress-image"
                  />
                </div>
              </el-timeline-item>
            </el-timeline>
            <div v-else class="empty-progress">暂无进度记录</div>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { csChatSessionList, chatMessageList, chatMarkRead, adminOrderDetail, adminOrderConfirm, csOrderDetail, csOrderConfirm, csOrderProgress, getActiveQuickReplies, uploadFile, csComplaintCreate, csOrderList } from '@/api/business'
import { getProductDetail, getProductList } from '@/api/product'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { sendWsMessage } from '@/utils/websocket'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const chatStore = useChatStore()

const isAdmin = userStore.role === 'admin'
const typeMap = { USER_PLAYER: '订单', USER_CS: '客服', PLAYER_CS: '打手客服', PLAYER_PLAYER: '组队' }
const orderStatusMap = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ASSIGNED: '已指派', ACCEPTED: '已接单',
  WAITING_TEAMMATE: '组队中', IN_PROGRESS: '进行中', COMPLETED: '待确认',
  CONFIRMED: '已完成', REVIEWED: '已评价', CANCELLED: '已取消',
  REFUNDING: '退款中', REFUNDED: '已退款', DISPUTED: '争议中', ARBITRATED: '已仲裁'
}
const orderTagTypeMap = {
  PENDING_PAYMENT: 'info', PAID: '', IN_PROGRESS: 'warning', COMPLETED: 'success',
  CONFIRMED: 'success', CANCELLED: 'info', REFUNDING: 'danger', DISPUTED: 'danger'
}
function orderTagType(s) { return orderTagTypeMap[s] || '' }

// ===== 会话列表 =====
const sessions = ref([])
async function fetchSessions() {
  try {
    const res = await csChatSessionList({ pageNum: 1, pageSize: 100, type: 'USER_CS' })
    sessions.value = res.data?.records || res.data || []
  } catch { sessions.value = [] }
}

// ===== 聊天消息 =====
const currentSession = ref(null)
const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const msgContainer = ref(null)

function isSelf(m) {
  return m.senderType === 'CS' || m.senderType === 'ADMIN'
}

function scrollToBottom() {
  nextTick(() => {
    nextTick(() => {
      if (msgContainer.value) {
        msgContainer.value.scrollTop = msgContainer.value.scrollHeight
      }
    })
  })
}

async function selectSession(s) {
  currentSession.value = s
  chatStore.currentSessionId = s.id
  messages.value = []
  await fetchMessages()
  // 标记消息已读
  chatMarkRead(s.id).catch(() => {})
  // 刷新会话列表清除未读数
  fetchSessions()
}

async function fetchMessages() {
  if (!currentSession.value) return
  try {
    const res = await chatMessageList({ sessionId: currentSession.value.id, pageNum: 1, pageSize: 100 })
    messages.value = (res.data?.records || []).reverse()
    scrollToBottom()
  } catch { /* ignore */ }
}

const senderType = isAdmin ? 'ADMIN' : 'CS'
const fileInput = ref(null)
const productPickerVisible = ref(false)
const pickerProducts = ref([])
const pickerKeyword = ref('')
const pickerLoading = ref(false)
const filteredPickerProducts = computed(() => {
  const kw = pickerKeyword.value.trim().toLowerCase()
  if (!kw) return pickerProducts.value
  return pickerProducts.value.filter(p => (p.name || '').toLowerCase().includes(kw))
})

/** 通用发送 */
function sendViaWs(type, content) {
  if (!currentSession.value) return false
  const sent = sendWsMessage({
    action: 'send',
    sessionId: currentSession.value.id,
    senderType,
    senderId: userStore.adminId,
    chatRole: senderType,
    type,
    content
  })
  if (!sent) {
    ElMessage.error('WebSocket未连接，请稍后重试')
    return false
  }
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const createdAt = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  messages.value.push({
    id: Date.now(),
    sessionId: currentSession.value.id,
    senderId: userStore.adminId,
    senderType,
    type,
    content,
    createdAt
  })
  scrollToBottom()
  return true
}

async function sendText() {
  if (!inputText.value.trim() || !currentSession.value) return
  sending.value = true
  try {
    sendViaWs('TEXT', inputText.value.trim())
    inputText.value = ''
  } finally {
    sending.value = false
  }
}

function pickImage() {
  fileInput.value?.click()
}
async function onFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadFile(file)
    const url = res.data
    if (url) sendViaWs('IMAGE', url)
  } catch {
    ElMessage.error('图片上传失败')
  } finally {
    fileInput.value.value = ''
  }
}
async function openProductPicker() {
  productPickerVisible.value = true
  pickerKeyword.value = ''
  pickerLoading.value = true
  try {
    const res = await getProductList({ pageNum: 1, pageSize: 50, status: 1 })
    pickerProducts.value = res.data?.records || res.data || []
  } catch { pickerProducts.value = [] }
  finally { pickerLoading.value = false }
}
function doSendProduct(p) {
  productPickerVisible.value = false
  sendViaWs('PRODUCT', JSON.stringify({ id: p.id, name: p.name, price: p.price, coverImage: p.coverImage || p.image }))
}

// ===== 快捷回复 =====
const showQuickReply = ref(false)
const quickReplies = ref([])

async function toggleQuickReply() {
  showQuickReply.value = !showQuickReply.value
  if (showQuickReply.value && quickReplies.value.length === 0) {
    try {
      const res = await getActiveQuickReplies()
      quickReplies.value = res.data || []
    } catch { quickReplies.value = [] }
  }
}

function useQuickReply(qr) {
  inputText.value = qr.content
  showQuickReply.value = false
}

function parseJSON(str) {
  try { return JSON.parse(str) } catch { return {} }
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

// 监听全局 store 的新消息
watch(() => chatStore.newMessage, (msg) => {
  if (!msg) return
  if (currentSession.value && String(msg.sessionId) === String(currentSession.value.id)) {
    // 防重复：ID匹配 或 相同内容+类型且时间差<5s
    const exists = messages.value.some(m =>
      String(m.id) === String(msg.id) ||
      (m.type === msg.type && m.content === msg.content &&
        Math.abs(new Date(m.createdAt || 0).getTime() - new Date(msg.createdAt || 0).getTime()) < 5000)
    )
    if (!exists) {
      messages.value.push(msg)
      scrollToBottom()
    }
  }
  // 刷新会话列表（更新最后消息/未读数）
  fetchSessions()
})

// ===== 商品详情弹窗 =====
const productDialogVisible = ref(false)
const productDetail = ref(null)
const productDetailLoading = ref(false)

// 计算商品所有图片（封面 + images JSON数组）
const productAllImages = computed(() => {
  if (!productDetail.value) return []
  const imgs = []
  if (productDetail.value.coverImage) imgs.push(productDetail.value.coverImage)
  if (productDetail.value.images) {
    try {
      const parsed = JSON.parse(productDetail.value.images)
      if (Array.isArray(parsed)) {
        parsed.forEach(u => { if (u && !imgs.includes(u)) imgs.push(u) })
      }
    } catch {
      // images 可能是逗号分隔字符串
      productDetail.value.images.split(',').forEach(u => {
        const trimmed = u.trim()
        if (trimmed && !imgs.includes(trimmed)) imgs.push(trimmed)
      })
    }
  }
  return imgs
})

async function showProductDetail(data) {
  if (!data?.id) return
  productDialogVisible.value = true
  productDetailLoading.value = true
  productDetail.value = null
  try {
    const res = await getProductDetail(data.id)
    productDetail.value = res.data
  } catch {
    productDetail.value = data
  } finally {
    productDetailLoading.value = false
  }
}

// ===== 订单详情弹窗 =====
const orderDialogVisible = ref(false)
const orderDetail = ref(null)
const orderDetailLoading = ref(false)
const orderProgressList = ref([])
const orderConfirmLoading = ref(false)

// 修改打手弹窗
const assignDialogVisible = ref(false)
const assignPlayerLoading = ref(false)
const assignPlayers = ref([])
const assignKeyword = ref('')

async function loadAssignPlayers() {
  if (!orderDetail.value) return
  assignPlayerLoading.value = true
  try {
    const fn = isAdmin ? adminPlayerList : csPlayerList
    const res = await fn({ pageNum: 1, pageSize: 20, keyword: assignKeyword.value, status: 'ACTIVE' })
    assignPlayers.value = res.data?.records || []
  } finally {
    assignPlayerLoading.value = false
  }
}

function openAssignFromOrderDetail() {
  if (!orderDetail.value) return
  assignKeyword.value = ''
  assignPlayers.value = []
  assignDialogVisible.value = true
  loadAssignPlayers()
}

async function handleAssignPlayer(player) {
  if (!orderDetail.value || !player?.id) return
  await orderAssignByRole(orderDetail.value.id, player.id, isAdmin)
  ElMessage.success('修改打手成功')
  assignDialogVisible.value = false
  // 重新加载订单详情
  await showOrderDetail({ id: orderDetail.value.id })
}

function progressNodeType(p) {
  const s = (p.toStatus || '').toUpperCase()
  if (s === 'CONFIRMED' || s === 'COMPLETED') return 'success'
  if (s === 'CANCELLED' || s === 'REFUNDED' || s === 'DISPUTED') return 'danger'
  if (s === 'IN_PROGRESS') return 'warning'
  return 'primary'
}

async function showOrderDetail(data) {
  if (!data?.id) return
  orderDialogVisible.value = true
  orderDetailLoading.value = true
  orderDetail.value = null
  orderProgressList.value = []
  try {
    // admin 用 admin 接口，cs 用 cs 接口（避免 403）
    const detailFn = isAdmin ? adminOrderDetail : csOrderDetail
    const [detailRes, progressRes] = await Promise.all([
      detailFn(data.id),
      csOrderProgress(data.id)
    ])
    orderDetail.value = detailRes.data
    orderProgressList.value = progressRes.data || []
  } catch {
    orderDetail.value = data
  } finally {
    orderDetailLoading.value = false
  }
}

async function handleOrderConfirm() {
  if (!orderDetail.value?.id) return
  await ElMessageBox.confirm('确定手动结单？订单将变为已确认状态。', '结单确认', { type: 'warning' })
  orderConfirmLoading.value = true
  try {
    const fn = isAdmin ? adminOrderConfirm : csOrderConfirm
    await fn(orderDetail.value.id)
    ElMessage.success('结单成功')
    await showOrderDetail({ id: orderDetail.value.id })
  } finally {
    orderConfirmLoading.value = false
  }
}

// ===== 投诉工单创建 =====
const complaintVisible = ref(false)
const complaintSubmitting = ref(false)
const complaintFormRef = ref(null)
const complaintOrdersLoading = ref(false)
const complaintOrders = ref([])
const complaintForm = reactive({
  userId: null, orderId: null, type: '', content: '',
  _userName: '', _userAvatar: ''
})
const complaintRules = {
  orderId: [{ required: true, message: '请选择关联订单', trigger: 'change' }],
  type: [{ required: true, message: '请选择投诉类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入投诉内容', trigger: 'blur' }]
}

function resetComplaintForm() {
  Object.assign(complaintForm, { userId: null, orderId: null, type: '', content: '', _userName: '', _userAvatar: '' })
  complaintOrders.value = []
}

async function loadUserOrders(userId) {
  complaintOrdersLoading.value = true
  try {
    const res = await csOrderList({ userId, pageNum: 1, pageSize: 50 })
    complaintOrders.value = (res.data?.records || []).filter(o =>
      ['IN_PROGRESS', 'COMPLETED', 'CONFIRMED', 'REVIEWED', 'DISPUTED', 'ARBITRATED'].includes(o.status)
    )
  } catch { complaintOrders.value = [] }
  finally { complaintOrdersLoading.value = false }
}

function onComplaintOrderChange(orderId) {
  const order = complaintOrders.value.find(o => o.id === orderId)
  if (order) complaintForm._orderInfo = order
}

/** 从聊天工具栏打开，自动填充用户信息，拉取用户订单列表 */
async function openComplaintFromChat() {
  if (!currentSession.value) return
  resetComplaintForm()
  const s = currentSession.value
  complaintForm.userId = s.targetId
  complaintForm._userName = s.targetName
  complaintForm._userAvatar = s.avatar
  complaintVisible.value = true
  if (s.targetId) await loadUserOrders(s.targetId)
}

/** 从订单卡片消息打开，自动填充用户+订单 */
async function openComplaintFromOrder(orderData) {
  if (!currentSession.value) return
  resetComplaintForm()
  const s = currentSession.value
  complaintForm.userId = s.targetId
  complaintForm._userName = s.targetName
  complaintForm._userAvatar = s.avatar
  complaintVisible.value = true
  if (s.targetId) await loadUserOrders(s.targetId)
  // 自动选中该订单
  if (orderData?.id) {
    complaintForm.orderId = orderData.id
    onComplaintOrderChange(orderData.id)
  }
}

/** 从订单详情弹窗打开 */
async function openComplaintFromOrderDetail() {
  if (!orderDetail.value) return
  const od = orderDetail.value
  resetComplaintForm()
  complaintForm.userId = od.userId
  complaintForm._userName = od.userNickname || ('ID: ' + od.userId)
  complaintForm._userAvatar = od.userAvatar || ''
  complaintVisible.value = true
  orderDialogVisible.value = false
  if (od.userId) await loadUserOrders(od.userId)
  complaintForm.orderId = od.id
  onComplaintOrderChange(od.id)
}

async function submitComplaint() {
  const valid = await complaintFormRef.value?.validate().catch(() => false)
  if (!valid) return
  complaintSubmitting.value = true
  try {
    await csComplaintCreate({
      userId: complaintForm.userId,
      orderId: complaintForm.orderId,
      type: complaintForm.type,
      content: complaintForm.content
    })
    ElMessage.success('投诉工单已创建')
    complaintVisible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.msg || '创建失败')
  } finally {
    complaintSubmitting.value = false
  }
}

onMounted(() => {
  fetchSessions()
  // 进入聊天页时清除全局未读
  chatStore.clearUnread()
})
onUnmounted(() => {
  // 离开聊天页时清除当前会话标记
  chatStore.currentSessionId = null
})
</script>

<style scoped>
.chat-service {
  display: flex;
  height: calc(100vh - 130px);
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
}

/* 左侧会话列表 */
.session-panel {
  width: 280px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 15px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
}
.session-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s;
}
.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #ecf5ff; border-left: 3px solid #409eff; }
.session-row { display: flex; align-items: center; gap: 10px; width: 100%; }
.session-avatar { flex-shrink: 0; }
.session-body { flex: 1; overflow: hidden; }
.session-top { display: flex; justify-content: space-between; align-items: center; }
.session-name { font-size: 14px; font-weight: 500; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.session-time { font-size: 11px; color: #c0c4cc; flex-shrink: 0; margin-left: 8px; }
.session-bottom { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
.session-last-msg { font-size: 12px; color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.unread-badge {
  min-width: 18px; height: 18px; line-height: 18px; padding: 0 5px;
  font-size: 11px; color: #fff; background: #f56c6c; border-radius: 10px;
  text-align: center; flex-shrink: 0; margin-left: 6px;
}

/* 右侧聊天区 */
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-header {
  padding: 12px 20px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 500;
}
.order-link { font-size: 13px; color: #909399; }
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background: #f5f5f5;
}
.msg-row {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.msg-row.self { align-items: flex-end; }
.msg-bubble {
  max-width: 60%;
  padding: 10px 14px;
  background: #fff;
  border-radius: 8px;
  font-size: 14px;
  color: #303133;
  box-shadow: 0 1px 2px rgba(0,0,0,0.06);
  word-break: break-all;
}
.msg-bubble.self {
  background: #409eff;
  color: #fff;
}
.msg-time { font-size: 11px; color: #c0c4cc; margin-top: 4px; }

/* 卡片消息 */
.card-msg {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  min-width: 240px;
  padding: 0;
  position: relative;
}
.card-msg:hover { opacity: 0.85; }
.card-cover { width: 60px; height: 60px; border-radius: 4px; flex-shrink: 0; }
.card-info { flex: 1; overflow: hidden; }
.card-name { font-size: 13px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-price { font-size: 14px; color: #e6a23c; font-weight: bold; margin-top: 2px; }
.card-meta { display: flex; align-items: center; gap: 8px; margin-top: 2px; }
.card-sub { font-size: 11px; color: #909399; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-actions { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; flex-shrink: 0; }
.card-actions .card-tag { position: static; }
.card-complaint-btn { font-size: 11px !important; padding: 2px 6px !important; }
.selected-info { display: flex; align-items: center; gap: 8px; }
.msg-bubble.self .card-name { color: #fff; }
.msg-bubble.self .card-price { color: #ffd; }
.msg-bubble.self .card-sub { color: rgba(255,255,255,0.7); }

/* 快捷回复栏 */
.quick-reply-bar {
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
  max-height: 160px;
  display: flex;
  flex-direction: column;
}
.qr-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  border-bottom: 1px solid #eee;
}
.qr-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.qr-item {
  padding: 4px 12px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  color: #606266;
  white-space: nowrap;
  transition: all 0.2s;
}
.qr-item:hover { border-color: #409eff; color: #409eff; background: #ecf5ff; }
.qr-empty { font-size: 12px; color: #c0c4cc; padding: 8px; }

/* 输入区 */
.chat-input {
  padding: 12px 20px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  align-items: center;
}
.chat-input .el-input { flex: 1; }

/* 商品弹窗完整版 */
.product-full {}
.product-gallery { margin-bottom: 16px; background: #fafafa; border-radius: 8px; overflow: hidden; }
.no-image { height: 120px; display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 14px; }
.product-main { margin-bottom: 12px; }
.product-title { font-size: 17px; font-weight: 600; color: #303133; line-height: 1.4; }
.product-price-row { display: flex; align-items: center; gap: 10px; margin-top: 8px; }
.price-val { font-size: 22px; color: #e6a23c; font-weight: bold; }
.product-desc-table { margin-top: 4px; }
.product-description { white-space: pre-wrap; line-height: 1.6; color: #606266; max-height: 160px; overflow-y: auto; }

/* 订单弹窗 - 人员卡片 */
.order-people {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}
.person-card {
  display: flex;
  align-items: center;
  gap: 10px;
}
.person-info {}
.person-label { font-size: 12px; color: #909399; }
.person-name { font-size: 14px; font-weight: 500; color: #303133; margin-top: 2px; }
.person-arrow { font-size: 20px; color: #c0c4cc; }

/* 订单弹窗 - 进度时间线 */
.order-progress-section { margin-top: 20px; }
.section-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 12px; padding-left: 8px; border-left: 3px solid #409eff; }
.progress-content { font-size: 14px; color: #303133; }
.progress-remark { font-size: 12px; color: #909399; margin-top: 2px; }
.progress-images { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.progress-image { width: 72px; height: 72px; border-radius: 6px; overflow: hidden; }
.empty-progress { text-align: center; padding: 20px; color: #c0c4cc; font-size: 13px; }

.empty-chat { flex: 1; display: flex; align-items: center; justify-content: center; }
.empty-tip { text-align: center; padding: 20px; color: #c0c4cc; font-size: 13px; }

/* 商品选择弹窗 */
.picker-search { margin-bottom: 16px; }
.picker-grid {
  max-height: 440px;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 2px;
}
.picker-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s;
  background: #fff;
}
.picker-card:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}
.picker-card-cover {
  width: 100%;
  height: 140px;
  background: #f5f7fa;
  overflow: hidden;
}
.picker-card-noimg {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  color: #c0c4cc; font-size: 13px;
}
.picker-card-body {
  padding: 10px 12px;
}
.picker-card-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}
.picker-card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.picker-card-price {
  font-size: 16px;
  font-weight: bold;
  color: #e6a23c;
}
.picker-empty {
  grid-column: 1 / -1;
  padding: 20px 0;
}
</style>
