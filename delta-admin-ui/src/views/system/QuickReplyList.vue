<template>
  <div class="page-container">
    <el-card>
      <template #header><div class="card-header"><span>快捷回复管理</span><el-button type="primary" @click="openDialog()">新增</el-button></div></template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑' : '新增'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="分类"><el-input v-model="form.category" placeholder="如：问候、售后" /></el-form-item>
        <el-form-item label="内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getQuickReplyList, addQuickReply, updateQuickReply, deleteQuickReply } from '@/api/system'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })
const rules = { content: [{ required: true, message: '请输入内容', trigger: 'blur' }] }
async function fetchData() { loading.value = true; try { const res = await getQuickReplyList(query); list.value = res.data.records; total.value = Number(res.data.total) } finally { loading.value = false } }
const dialogVisible = ref(false), editingId = ref(null), submitting = ref(false), formRef = ref(null)
const form = reactive({ category: '', content: '', sortOrder: 0, status: 1 })
const defaultForm = { category: '', content: '', sortOrder: 0, status: 1 }
function openDialog(row) { editingId.value = row?.id || null; Object.assign(form, row || defaultForm); dialogVisible.value = true }
async function handleSubmit() { const valid = await formRef.value.validate().catch(() => false); if (!valid) return; submitting.value = true; try { editingId.value ? await updateQuickReply({ ...form, id: editingId.value }) : await addQuickReply(form); ElMessage.success('操作成功'); dialogVisible.value = false; fetchData() } finally { submitting.value = false } }
async function handleDelete(id) { await deleteQuickReply(id); ElMessage.success('删除成功'); fetchData() }
onMounted(fetchData)
</script>
<style scoped>.card-header { display: flex; justify-content: space-between; align-items: center; }</style>
