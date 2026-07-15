<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="detail-header">
          <el-button link type="primary" @click="router.push({ name: 'ChatRecordList' })">
            <el-icon><ArrowLeft /></el-icon> 返回列表
          </el-button>
          <span class="session-title">会话 #{{ sessionId }} 聊天记录（只读）</span>
        </div>
      </template>
      <div v-loading="loading" class="message-list">
        <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ self: m.senderType === 'CS' || m.senderType === 'SYSTEM' }">
          <div class="msg-meta">
            <el-tag size="small" :type="senderTagType(m.senderType)">{{ senderLabel(m.senderType) }} {{ m.senderId ? '#' + m.senderId : '' }}</el-tag>
            <span class="msg-time">{{ m.createdAt }}</span>
          </div>
          <div class="msg-bubble">
            <template v-if="m.type === 'TEXT'">{{ m.content }}</template>
            <el-image v-else-if="m.type === 'IMAGE'" :src="m.content" style="max-width:200px;border-radius:4px" fit="cover" :preview-src-list="[m.content]" />
            <span v-else-if="m.type === 'PRODUCT'">[商品卡片]</span>
            <span v-else-if="m.type === 'ORDER'">[订单卡片]</span>
            <span v-else>{{ m.content }}</span>
          </div>
        </div>
        <div v-if="!loading && messages.length === 0" class="empty-tip">暂无消息</div>
      </div>
      <Pagination :total="msgTotal" v-model:page="msgQuery.pageNum" v-model:limit="msgQuery.pageSize" @pagination="fetchMessages" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { adminChatMessageList, csChatMessageList } from '@/api/business'
import { useUserStore } from '@/stores/user'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'
const sessionId = computed(() => route.params.id)
const loading = ref(false)
const messages = ref([])
const msgTotal = ref(0)
const msgQuery = reactive({ pageNum: 1, pageSize: 50 })

const senderLabelMap = { USER: '用户', PLAYER: '打手', CS: '客服', SYSTEM: '系统' }
const senderTagMap = { USER: '', PLAYER: 'success', CS: 'warning', SYSTEM: 'info' }
function senderLabel(t) { return senderLabelMap[t] || t }
function senderTagType(t) { return senderTagMap[t] || 'info' }

async function fetchMessages() {
  if (!sessionId.value) return
  loading.value = true
  try {
    const fn = isAdmin ? adminChatMessageList : csChatMessageList
    const res = await fn({
      sessionId: sessionId.value,
      pageNum: msgQuery.pageNum,
      pageSize: msgQuery.pageSize
    })
    const data = res.data || {}
    messages.value = data.records || []
    msgTotal.value = data.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(fetchMessages)
</script>

<style scoped>
.detail-header { display: flex; align-items: center; gap: 16px; }
.session-title { font-size: 14px; color: #606266; }
.message-list { min-height: 200px; padding: 12px 0; }
.msg-row { margin-bottom: 16px; }
.msg-row.self .msg-bubble { background: #ecf5ff; margin-left: 0; margin-right: 40px; }
.msg-meta { margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
.msg-time { font-size: 12px; color: #909399; }
.msg-bubble { display: inline-block; max-width: 70%; padding: 8px 12px; background: #f4f4f5; border-radius: 8px; font-size: 14px; margin-left: 40px; }
.empty-tip { text-align: center; color: #909399; padding: 40px; }
</style>
