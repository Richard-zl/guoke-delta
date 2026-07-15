<template>
  <div class="page-container">
    <el-card>
      <template #header><div class="card-header"><span>公告管理</span><el-button type="primary" @click="openDialog()">新增</el-button></div></template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
        <el-table-column prop="popupDisplay" label="首页弹窗" width="100"><template #default="{ row }"><el-tag :type="row.popupDisplay === 1 ? 'warning' : 'info'" size="small">{{ row.popupDisplay === 1 ? '展示' : '不展示' }}</el-tag></template></el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="success" @click="handlePush(row.id)">推送</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑公告' : '新增公告'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.type" placeholder="选择类型"><el-option label="系统公告" value="system" /><el-option label="活动公告" value="activity" /></el-select></el-form-item>
        <el-form-item label="内容" prop="content"><RichTextEditor v-model="form.content" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="发布" inactive-text="草稿" /></el-form-item>
        <el-form-item label="首页弹窗"><el-switch v-model="form.popupDisplay" :active-value="1" :inactive-value="0" active-text="展示" inactive-text="不展示" /><div style="font-size:12px;color:#999;margin-top:4px">开启后用户进入首页会弹窗展示此公告，多条公告可叠加</div></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getNoticeList, addNotice, updateNotice, deleteNotice, pushNotice } from '@/api/system'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })
const rules = { title: [{ required: true, message: '请输入标题', trigger: 'blur' }], content: [{ required: true, message: '请输入内容', trigger: 'blur' }] }
async function fetchData() { loading.value = true; try { const res = await getNoticeList(query); list.value = res.data.records; total.value = Number(res.data.total) } finally { loading.value = false } }
const dialogVisible = ref(false), editingId = ref(null), submitting = ref(false), formRef = ref(null)
const form = reactive({ title: '', type: 'system', content: '', status: 1, popupDisplay: 0 })
const defaultForm = { title: '', type: 'system', content: '', status: 1, popupDisplay: 0 }
function openDialog(row) { editingId.value = row?.id || null; Object.assign(form, row || defaultForm); dialogVisible.value = true }
async function handleSubmit() { const valid = await formRef.value.validate().catch(() => false); if (!valid) return; submitting.value = true; try { editingId.value ? await updateNotice({ ...form, id: editingId.value }) : await addNotice(form); ElMessage.success('操作成功'); dialogVisible.value = false; fetchData() } finally { submitting.value = false } }
async function handleDelete(id) { await deleteNotice(id); ElMessage.success('删除成功'); fetchData() }
async function handlePush(id) { await pushNotice(id); ElMessage.success('推送成功') }
onMounted(fetchData)
</script>
<style scoped>.card-header { display: flex; justify-content: space-between; align-items: center; }</style>
