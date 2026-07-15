<template>
  <div class="page-container">
    <el-card>
      <template #header><div class="card-header"><span>商品管理</span><el-button type="primary" @click="$router.push('/product/create')">新增商品</el-button></div></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item><el-input v-model="query.keyword" placeholder="商品名称" clearable @keyup.enter="fetchData" /></el-form-item>
        <el-form-item><el-select v-model="query.categoryId" placeholder="分类" clearable><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item>
        <el-form-item><el-select v-model="query.status" placeholder="状态" clearable><el-option label="上架" :value="1" /><el-option label="下架" :value="0" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button><el-button @click="query.keyword='';query.categoryId='';query.status='';fetchData()">重置</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图片" width="80"><template #default="{ row }"><el-image v-if="row.coverImage" :src="row.coverImage" style="width:50px;height:50px" fit="cover" /></template></el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="价格" width="120"><template #default="{ row }">{{ row.price != null ? '¥' + Number(row.price).toFixed(2) : '-' }}</template></el-table-column>
        <el-table-column label="分类" width="120"><template #default="{ row }">{{ getCategoryName(row.categoryId) }}</template></el-table-column>
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
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getProductList, deleteProduct, updateProductStatus, getAllCategories } from '@/api/product'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
const loading = ref(false), list = ref([]), total = ref(0), categories = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', categoryId: '', status: '' })
function getCategoryName(cid) { const c = categories.value.find(i => i.id === cid); return c ? c.name : '-' }
async function fetchData() { loading.value = true; try { const res = await getProductList(query); list.value = res.data.records; total.value = Number(res.data.total) } finally { loading.value = false } }
async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateProductStatus(row.id, newStatus)
  ElMessage.success(newStatus === 1 ? '已上架' : '已下架')
  fetchData()
}
async function handleDelete(id) { await deleteProduct(id); ElMessage.success('删除成功'); fetchData() }
onMounted(async () => { fetchData(); try { const res = await getAllCategories(); categories.value = res.data || [] } catch(e) { /* ignore */ } })
</script>
<style scoped>.card-header { display: flex; justify-content: space-between; align-items: center; } .search-form { margin-bottom: 16px; }</style>
