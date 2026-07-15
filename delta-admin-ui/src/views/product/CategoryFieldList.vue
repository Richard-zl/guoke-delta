<template>
  <div class="page-container">
    <div class="field-layout">
      <!-- 左侧：父分类选择 -->
      <el-card class="category-card">
        <template #header>
          <div class="card-header">
            <span>游戏分类</span>
            <el-tag size="small" type="info">{{ parentCategories.length }}个</el-tag>
          </div>
        </template>
        <div class="cat-list">
          <div
            v-for="c in parentCategories" :key="c.id"
            class="cat-item" :class="{ active: selectedCategoryId === c.id }"
            @click="selectCategory(c.id)"
          >
            <el-image v-if="c.icon" :src="c.icon" class="cat-icon" fit="cover" />
            <div v-else class="cat-icon-placeholder">{{ c.name?.charAt(0) }}</div>
            <span class="cat-name">{{ c.name }}</span>
            <el-icon v-if="selectedCategoryId === c.id" class="cat-check"><Check /></el-icon>
          </div>
          <el-empty v-if="!parentCategories.length" description="暂无分类" :image-size="60" />
        </div>
      </el-card>
      <!-- 右侧：字段列表 -->
      <el-card class="field-card">
        <template #header>
          <div class="card-header">
            <span v-if="selectedCategoryName">
              <el-tag effect="dark" type="primary" style="margin-right:8px">{{ selectedCategoryName }}</el-tag>
              下单表单字段
            </span>
            <span v-else class="header-hint">请先选择左侧的游戏分类</span>
            <el-button v-if="selectedCategoryId" type="primary" :icon="Plus" @click="openDialog()">新增字段</el-button>
          </div>
        </template>
        <!-- 未选择分类的空状态 -->
        <el-empty v-if="!selectedCategoryId" description="选择左侧分类后可配置下单表单字段" :image-size="120" />
        <!-- 字段表格 -->
        <el-table v-else :data="fieldList" v-loading="loading" stripe>
          <el-table-column prop="fieldLabel" label="字段名称" min-width="130">
            <template #default="{ row }">
              <span style="font-weight:500">{{ row.fieldLabel }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="fieldKey" label="字段Key" width="140">
            <template #default="{ row }">
              <el-text type="info" size="small"><code>{{ row.fieldKey }}</code></el-text>
            </template>
          </el-table-column>
          <el-table-column prop="fieldType" label="类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="{ TEXT: '', SELECT: 'success', TEXTAREA: 'warning' }[row.fieldType]">
                {{ { TEXT: '文本', SELECT: '下拉', TEXTAREA: '多行' }[row.fieldType] || row.fieldType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="options" label="选项" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.fieldType === 'SELECT'">{{ row.options }}</span>
              <el-text v-else type="info">—</el-text>
            </template>
          </el-table-column>
          <el-table-column prop="required" label="必填" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.required ? 'danger' : 'info'" size="small" round>{{ row.required ? '必填' : '选填' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-popconfirm title="确认删除该字段？" @confirm="handleDelete(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="该分类尚未配置字段，点击上方“新增字段”添加" :image-size="80" />
          </template>
        </el-table>
      </el-card>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑字段' : '新增字段'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="字段名称" prop="fieldLabel">
          <el-input v-model="form.fieldLabel" placeholder="如：游戏大区" />
          <div class="form-tip">用户在小程序下单时看到的字段标签</div>
        </el-form-item>
        <el-form-item label="字段Key" prop="fieldKey">
          <el-input v-model="form.fieldKey" placeholder="如：gameRegion" />
          <div class="form-tip">英文标识符，用于数据存储</div>
        </el-form-item>
        <el-form-item label="类型" prop="fieldType">
          <el-select v-model="form.fieldType" style="width:100%">
            <el-option label="文本输入" value="TEXT" />
            <el-option label="下拉选择" value="SELECT" />
            <el-option label="多行文本" value="TEXTAREA" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.fieldType === 'SELECT'" label="选项" prop="options">
          <el-input v-model="form.options" placeholder="逗号分隔，如：电信一区,电信二区,网通一区" />
          <div class="form-tip">多个选项用英文逗号分隔</div>
        </el-form-item>
        <el-form-item label="输入提示">
          <el-input v-model="form.placeholder" placeholder="如：请输入游戏大区" />
        </el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="form.required" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
          <div class="form-tip">数字越小越靠前</div>
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
import { getCategoryTree, getCategoryFieldList, addCategoryField, updateCategoryField, deleteCategoryField } from '@/api/product'
import { ElMessage } from 'element-plus'
import { Plus, Check } from '@element-plus/icons-vue'

const parentCategories = ref([])
const selectedCategoryId = ref(null)
const selectedCategoryName = computed(() => parentCategories.value.find(c => c.id === selectedCategoryId.value)?.name || '')
const fieldList = ref([])
const loading = ref(false)

async function loadCategories() {
  const res = await getCategoryTree()
  parentCategories.value = (res.data || []).filter(c => !c.parentId || c.parentId === 0)
}

function selectCategory(id) {
  selectedCategoryId.value = id
  fetchFields()
}

async function fetchFields() {
  if (!selectedCategoryId.value) return
  loading.value = true
  try {
    const res = await getCategoryFieldList(selectedCategoryId.value)
    fieldList.value = res.data || []
  } finally { loading.value = false }
}

// 弹窗
const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ fieldLabel: '', fieldKey: '', fieldType: 'TEXT', options: '', placeholder: '', required: 1, sortOrder: 0 })
const rules = {
  fieldLabel: [{ required: true, message: '请输入字段名称', trigger: 'blur' }],
  fieldKey: [{ required: true, message: '请输入字段Key', trigger: 'blur' }],
  fieldType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function openDialog(row) {
  editingId.value = row?.id || null
  if (row) {
    Object.assign(form, { fieldLabel: row.fieldLabel, fieldKey: row.fieldKey, fieldType: row.fieldType, options: row.options || '', placeholder: row.placeholder || '', required: row.required ?? 1, sortOrder: row.sortOrder || 0 })
  } else {
    Object.assign(form, { fieldLabel: '', fieldKey: '', fieldType: 'TEXT', options: '', placeholder: '', required: 1, sortOrder: 0 })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = { ...form, categoryId: selectedCategoryId.value }
    if (editingId.value) {
      await updateCategoryField({ ...payload, id: editingId.value })
    } else {
      await addCategoryField(payload)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchFields()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await deleteCategoryField(id)
  ElMessage.success('删除成功')
  fetchFields()
}

onMounted(loadCategories)
</script>

<style scoped>
.field-layout {
  display: flex;
  gap: 16px;
  min-height: 500px;
  align-items: flex-start;
}
.category-card {
  width: 240px;
  flex-shrink: 0;
}
.field-card {
  flex: 1;
  min-width: 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
}
.header-hint {
  color: #909399;
  font-weight: 400;
}
.cat-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cat-item {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  border: 1px solid transparent;
}
.cat-item:hover {
  background: #f5f7fa;
}
.cat-item.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
  border-color: #b3d8ff;
}
.cat-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  margin-right: 10px;
  flex-shrink: 0;
}
.cat-icon-placeholder {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  margin-right: 10px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cat-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cat-check {
  color: #409eff;
  font-size: 16px;
  flex-shrink: 0;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  color: #909399;
}
</style>
