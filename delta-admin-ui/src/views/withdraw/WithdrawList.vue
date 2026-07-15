<template>
  <div class="page-container">
    <el-card>
      <template #header><span>提现管理</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="待审核" value="PENDING" /><el-option label="已打款" value="COMPLETED" /><el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="打手" min-width="150">
          <template #default="{ row }">
            <div>{{ row.playerName || '-' }}</div>
            <div style="color:#999;font-size:12px">{{ row.playerPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="提现金额" width="110"><template #default="{ row }">¥{{ row.amount }}</template></el-table-column>
        <el-table-column label="收款账户" min-width="220">
          <template #default="{ row }">
            <div v-if="row.accountType">
              <el-tag size="small" :type="{ ALIPAY: 'primary', WECHAT: 'success', BANK: 'warning' }[row.accountType]">{{ { ALIPAY: '支付宝', WECHAT: '微信', BANK: '银行卡' }[row.accountType] || row.accountType }}</el-tag>
              <span style="margin-left:8px">{{ row.accountNo }}</span>
              <span style="margin-left:8px;color:#999">{{ row.accountName }}</span>
            </div>
            <span v-else style="color:#999">未知</span>
          </template>
        </el-table-column>
        <el-table-column label="收款码" width="80" align="center">
          <template #default="{ row }">
            <el-image v-if="row.qrcodeUrl" :src="row.qrcodeUrl" :preview-src-list="[row.qrcodeUrl]" preview-teleported fit="cover" style="width:40px;height:40px;border-radius:4px;cursor:pointer" />
            <span v-else style="color:#999;font-size:12px">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="{ PENDING: 'warning', COMPLETED: 'success', REJECTED: 'danger' }[row.status]" size="small">
              {{ { PENDING: '待审核', COMPLETED: '已打款', REJECTED: '已拒绝' }[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" @click="openApproveDialog(row)">同意打款</el-button>
              <el-button link type="danger" @click="openRejectDialog(row)">拒绝</el-button>
            </template>
            <template v-if="row.status === 'COMPLETED'">
              <el-button link type="primary" @click="openProofDialog(row)">查看凭证</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
    <el-dialog v-model="rejectVisible" title="拒绝原因" width="400px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入拒绝原因" />
      <template #footer><el-button @click="rejectVisible = false">取消</el-button><el-button type="primary" @click="handleReject">确认拒绝</el-button></template>
    </el-dialog>
    <!-- 同意打款弹窗 -->
    <el-dialog v-model="approveVisible" title="同意打款" width="500px">
      <div v-if="approveRow" class="approve-account-info">
        <p>收款账户：<el-tag size="small">{{ { ALIPAY: '支付宝', WECHAT: '微信', BANK: '银行卡' }[approveRow.accountType] || '-' }}</el-tag> {{ approveRow.accountNo }} ({{ approveRow.accountName }})</p>
        <p>提现金额：<b>¥{{ approveRow.amount }}</b></p>
        <div v-if="approveRow.qrcodeUrl" style="margin-top:8px">
          <p style="margin-bottom:4px">收款码：</p>
          <el-image :src="approveRow.qrcodeUrl" :preview-src-list="[approveRow.qrcodeUrl]" preview-teleported fit="contain" style="max-width:200px;max-height:200px;border:1px solid #eee;border-radius:4px" />
        </div>
      </div>
      <el-form label-width="100px" style="margin-top:16px">
        <el-form-item label="打款凭证"><ImageUpload v-model="approveForm.proofImage" /></el-form-item>
        <el-form-item label="打款时间"><el-date-picker v-model="approveForm.processedAt" type="datetime" placeholder="选择打款时间" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="打款备注"><el-input v-model="approveForm.remark" type="textarea" :rows="2" placeholder="选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approveSubmitting" @click="handleApprove">确认打款</el-button>
      </template>
    </el-dialog>
    <!-- 查看打款凭证弹窗 -->
    <el-dialog v-model="proofVisible" title="打款凭证" width="500px">
      <div v-if="proofRow">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="打款时间">{{ proofRow.processedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="打款备注">{{ proofRow.payMethod || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:16px">
          <p style="margin-bottom:8px;font-weight:bold">打款凭证：</p>
          <el-image v-if="proofRow.proofImage" :src="proofRow.proofImage" :preview-src-list="[proofRow.proofImage]" preview-teleported fit="contain" style="max-width:100%;max-height:400px" />
          <span v-else style="color:#999">无凭证</span>
        </div>
      </div>
      <template #footer><el-button @click="proofVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminWithdrawList, adminWithdrawProcess, adminWithdrawReject, csWithdrawList, csWithdrawApprove, csWithdrawReject } from '@/api/business'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import ImageUpload from '@/components/ImageUpload.vue'
const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })
async function fetchData() {
  loading.value = true
  try {
    const fn = isAdmin ? adminWithdrawList : csWithdrawList
    const res = await fn(query)
    list.value = res.data.records; total.value = Number(res.data.total)
  } finally { loading.value = false }
}
// 同意打款
const approveVisible = ref(false), approveId = ref(null), approveSubmitting = ref(false), approveRow = ref(null)
const approveForm = reactive({ proofImage: '', processedAt: '', remark: '' })
function openApproveDialog(row) {
  approveId.value = row.id
  approveRow.value = row
  Object.assign(approveForm, { proofImage: '', processedAt: '', remark: '' })
  approveVisible.value = true
}
async function handleApprove() {
  if (!approveForm.proofImage) return ElMessage.warning('请上传打款凭证')
  approveSubmitting.value = true
  try {
    const data = { proofImage: approveForm.proofImage, processedAt: approveForm.processedAt, payMethod: approveForm.remark }
    if (isAdmin) await adminWithdrawProcess(approveId.value, data)
    else await csWithdrawApprove(approveId.value, data)
    ElMessage.success('已同意打款'); approveVisible.value = false; fetchData()
  } finally { approveSubmitting.value = false }
}
// 查看打款凭证
const proofVisible = ref(false), proofRow = ref(null)
function openProofDialog(row) { proofRow.value = row; proofVisible.value = true }
const rejectVisible = ref(false), rejectId = ref(null), rejectReason = ref('')
function openRejectDialog(row) { rejectId.value = row.id; rejectReason.value = ''; rejectVisible.value = true }
async function handleReject() {
  if (isAdmin) await adminWithdrawReject(rejectId.value, rejectReason.value)
  else await csWithdrawReject(rejectId.value, rejectReason.value)
  ElMessage.success('已拒绝'); rejectVisible.value = false; fetchData()
}
onMounted(fetchData)
</script>
<style scoped>.search-form { margin-bottom: 16px; }</style>
