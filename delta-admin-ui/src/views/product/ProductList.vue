<template>
  <div class="page-container">
    <el-card>
      <template #header><div class="card-header"><span>商品管理</span><el-button type="primary" @click="$router.push('/product/create')">新增商品</el-button></div></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="商品名称" clearable @keyup.enter="fetchData" />
        </el-form-item>
        <el-form-item>
          <!-- 树形筛选：选父=整组，选子=精确；同名靠层级区分 -->
          <el-tree-select
            v-model="selectedCategoryId"
            :data="categoryTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            :render-after-expand="false"
            check-strictly
            clearable
            filterable
            placeholder="按分类筛选"
            node-key="id"
            style="width: 220px"
          >
            <template #default="{ data }">
              <span>{{ data.name }}</span>
              <el-tag v-if="isParentCategory(data)" size="small" type="info" style="margin-left:6px">父分类</el-tag>
            </template>
          </el-tree-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-image v-if="row.coverImage" :src="row.coverImage" style="width:50px;height:50px" fit="cover" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">{{ row.price != null ? '¥' + Number(row.price).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column label="分类" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ getCategoryPath(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="salesCount" label="销量" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="handleToggleStatus(row)" active-text="上架" inactive-text="下架" inline-prompt />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/product/edit/${row.id}`)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getProductList, deleteProduct, updateProductStatus, getCategoryTree } from '@/api/product'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const categoryTree = ref([])
/** id -> { name, parentId, parentName }，用于路径展示与父子判断 */
const categoryMap = ref({})
/** 筛选用选中节点；请求时再拆成 categoryId / parentCategoryId */
const selectedCategoryId = ref(null)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  categoryId: '',
  parentCategoryId: '',
  status: ''
})

function isParentCategory(node) {
  return !node.parentId || node.parentId === 0
}

/** 展平树并记录父级名称，便于「父 / 子」展示 */
function buildCategoryMap(nodes, parentName = '') {
  const map = {}
  for (const n of nodes || []) {
    map[n.id] = {
      name: n.name,
      parentId: n.parentId || 0,
      parentName
    }
    if (n.children?.length) {
      Object.assign(map, buildCategoryMap(n.children, n.name))
    }
  }
  return map
}

function getCategoryPath(cid) {
  const c = categoryMap.value[cid]
  if (!c) return '-'
  if (c.parentName) return `${c.parentName} / ${c.name}`
  return c.name
}

/** 按选中节点类型写入互斥的筛选参数 */
function applyCategoryFilter() {
  query.categoryId = ''
  query.parentCategoryId = ''
  const id = selectedCategoryId.value
  if (id == null || id === '') return
  const info = categoryMap.value[id]
  if (!info) return
  if (!info.parentId) {
    query.parentCategoryId = id
  } else {
    query.categoryId = id
  }
}

async function fetchData() {
  applyCategoryFilter()
  loading.value = true
  try {
    const res = await getProductList(query)
    list.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.categoryId = ''
  query.parentCategoryId = ''
  selectedCategoryId.value = null
  query.pageNum = 1
  fetchData()
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateProductStatus(row.id, newStatus)
  ElMessage.success(newStatus === 1 ? '已上架' : '已下架')
  fetchData()
}

async function handleDelete(id) {
  await deleteProduct(id)
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(async () => {
  fetchData()
  try {
    const res = await getCategoryTree()
    categoryTree.value = res.data || []
    categoryMap.value = buildCategoryMap(categoryTree.value)
  } catch (e) { /* ignore */ }
})
</script>
<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
</style>
