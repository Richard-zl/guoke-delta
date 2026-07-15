<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>热门分类管理</span>
          <el-button type="primary" @click="openDialog()">新增分类</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="name" label="展示名称" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？删除后已选该分类的推荐商品将只出现在「全部」" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新增分类'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="如 HOT、ESCORT" :disabled="!!editingId" maxlength="32" show-word-limit />
          <div class="form-tip" v-if="!editingId">英文标识，创建后不可修改</div>
        </el-form-item>
        <el-form-item label="展示名称" prop="name">
          <el-input v-model="form.name" placeholder="如 热门推荐、护航专区推荐" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
          <div class="form-tip">数值越小越靠前</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getRecommendCategoryList, addRecommendCategory, updateRecommendCategory, deleteRecommendCategory } from '@/api/product'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ code: '', name: '', sortOrder: 0 })
const defaultForm = { code: '', name: '', sortOrder: 0 }
const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入展示名称', trigger: 'blur' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getRecommendCategoryList()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  editingId.value = row?.id ?? null
  Object.assign(form, row ? { code: row.code, name: row.name, sortOrder: row.sortOrder ?? 0 } : defaultForm)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateRecommendCategory(payload)
    } else {
      await addRecommendCategory(payload)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  await deleteRecommendCategory(id)
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
