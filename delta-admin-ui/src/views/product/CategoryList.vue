<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>分类管理</span>
          <div class="header-right">
            <el-radio-group v-model="parentFilter" size="small" @change="onParentFilterChange">
              <el-radio-button :value="0">全部</el-radio-button>
              <el-radio-button v-for="p in parentList" :key="p.id" :value="p.id">{{ p.name }}</el-radio-button>
            </el-radio-group>
            <el-button type="primary" @click="openDialog()">新增分类</el-button>
          </div>
        </div>
      </template>
      <el-table
        ref="tableRef"
        :data="filteredTree"
        v-loading="loading"
        stripe
        row-key="id"
        :default-expand-all="false"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称">
          <template #default="{ row }">
            <span :class="{ 'parent-name': !row.parentId }">{{ row.name }}</span>
            <el-tag v-if="!row.parentId" size="small" type="info" class="level-tag">父分类</el-tag>
            <el-tag v-else size="small" class="level-tag">子分类</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="80">
          <template #default="{ row }">
            <el-image v-if="row.icon" :src="row.icon" style="width:30px;height:30px" fit="cover" />
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="handleToggleStatus(row)"
              active-text="启用" inactive-text="禁用" inline-prompt
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button v-if="!row.parentId" link type="primary" @click="openDialog(null, row.id)">添加子分类</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm :title="!row.parentId ? '删除父分类会同时删除其下所有子分类，确认？' : '确认删除？'" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="450px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型" prop="parentId">
          <el-select v-model="form.parentId" :disabled="!!editingId" clearable placeholder="无（创建为父分类）">
            <el-option v-for="c in parentList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <div class="form-tip" v-if="!editingId">
            {{ form.parentId ? '将作为子分类添加' : '将作为父分类添加（可包含子分类）' }}
          </div>
        </el-form-item>
        <el-form-item label="图标">
          <ImageUpload v-model="form.icon" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { getCategoryTree, addCategory, updateCategory, deleteCategory } from '@/api/product'
import { ElMessage } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'

const loading = ref(false)
const tree = ref([])
const parentFilter = ref(0)
const tableRef = ref(null)

const parentList = computed(() => tree.value.map(p => ({ id: p.id, name: p.name })))

const filteredTree = computed(() => {
  if (!parentFilter.value) return tree.value
  return tree.value.filter(p => p.id === parentFilter.value)
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getCategoryTree()
    tree.value = (res.data || []).map(p => ({
      ...p,
      children: p.children?.length ? p.children : undefined,
      hasChildren: !!(p.children?.length)
    }))
  } finally {
    loading.value = false
  }
}

function onParentFilterChange() {
  // filter changed, tree re-renders
}

const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ name: '', parentId: null, icon: '', sortOrder: 0, status: 1 })
const defaultForm = { name: '', parentId: null, icon: '', sortOrder: 0, status: 1 }
const rules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

const dialogTitle = computed(() => {
  if (editingId.value) return '编辑分类'
  if (form.parentId) return '新增子分类'
  return '新增分类'
})

function openDialog(row, presetParentId) {
  editingId.value = row?.id || null
  if (row) {
    Object.assign(form, { name: row.name, parentId: row.parentId || null, icon: row.icon || '', sortOrder: row.sortOrder || 0, status: row.status ?? 1 })
  } else {
    Object.assign(form, { ...defaultForm })
    if (presetParentId) form.parentId = presetParentId
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = { ...form }
    if (!payload.parentId) payload.parentId = null
    if (editingId.value) {
      await updateCategory({ ...payload, id: editingId.value })
    } else {
      await addCategory(payload)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateCategory({ ...row, status: newStatus, children: undefined })
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  fetchData()
}

async function handleDelete(id) {
  await deleteCategory(id)
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
  flex-wrap: wrap;
  gap: 12px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.parent-name {
  font-weight: 600;
}
.level-tag {
  margin-left: 8px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
