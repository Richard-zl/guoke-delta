<template>
  <div class="page-container">
    <el-card>
      <template #header><div class="card-header"><span>轮播图管理</span><el-button type="primary" @click="openDialog()">新增</el-button></div></template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图片" width="120"><template #default="{ row }"><el-image :src="row.imageUrl" style="width:80px;height:45px" fit="cover" :preview-src-list="[row.imageUrl]" /></template></el-table-column>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="linkUrl" label="链接" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '显示' : '隐藏' }}</el-tag></template></el-table-column>
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
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="图片" prop="imageUrl"><ImageUpload v-model="form.imageUrl" /></el-form-item>
        <el-form-item label="链接"><el-input v-model="form.linkUrl" placeholder="跳转链接" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getBannerList, addBanner, updateBanner, deleteBanner } from '@/api/system'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import ImageUpload from '@/components/ImageUpload.vue'
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })
const rules = { title: [{ required: true, message: '请输入', trigger: 'blur' }], imageUrl: [{ required: true, message: '请上传图片', trigger: 'change' }] }
async function fetchData() { loading.value = true; try { const res = await getBannerList(query); list.value = res.data.records; total.value = Number(res.data.total) } finally { loading.value = false } }
const dialogVisible = ref(false), editingId = ref(null), submitting = ref(false), formRef = ref(null)
const form = reactive({ title: '', imageUrl: '', linkUrl: '', sort: 0, status: 1 })
const defaultForm = { title: '', imageUrl: '', linkUrl: '', sort: 0, status: 1 }
function openDialog(row) { editingId.value = row?.id || null; Object.assign(form, row || defaultForm); dialogVisible.value = true }
async function handleSubmit() { const valid = await formRef.value.validate().catch(() => false); if (!valid) return; submitting.value = true; try { editingId.value ? await updateBanner({ ...form, id: editingId.value }) : await addBanner(form); ElMessage.success('操作成功'); dialogVisible.value = false; fetchData() } finally { submitting.value = false } }
async function handleDelete(id) { await deleteBanner(id); ElMessage.success('删除成功'); fetchData() }
onMounted(fetchData)
</script>
<style scoped>.card-header { display: flex; justify-content: space-between; align-items: center; }</style>
