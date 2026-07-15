<template>
  <div class="page-container">
    <el-card v-loading="pageLoading" class="product-form-card">
      <template #header>
        <div class="card-header product-form-header">
          <div>
            <span>{{ isEdit ? '编辑商品' : '新增商品' }}</span>
            <p>{{ isEdit ? '调整商品信息、价格规则和前端展示状态' : '填写商品基础资料，设置价格、展示和限购规则' }}</p>
          </div>
        </div>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px" class="product-form">
        <section class="form-section">
          <div class="section-title">
            <strong>基础信息</strong>
            <span>用户端展示的名称、分类、图片和详情内容</span>
          </div>
          <div class="section-body">
            <el-row :gutter="18">
              <el-col :span="12">
                <el-form-item label="商品名称" prop="name">
                  <el-input v-model="form.name" placeholder="请输入商品名称" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="分类" prop="categoryId">
                  <el-tree-select
                    v-model="form.categoryId"
                    :data="categoryTree"
                    :props="{ label: 'name', value: 'id', children: 'children' }"
                    :render-after-expand="false"
                    check-strictly
                    placeholder="选择子分类"
                    node-key="id"
                    class="full-control"
                  >
                    <template #default="{ data }">
                      <span :class="{ 'is-parent-node': !data.parentId }">{{ data.name }}</span>
                      <el-tag v-if="!data.parentId" size="small" type="info" style="margin-left:6px">父分类</el-tag>
                    </template>
                  </el-tree-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="副标题">
              <el-input v-model="form.subtitle" placeholder="简短卖点描述（选填）" maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="封面图" class="image-form-item">
              <div class="upload-field">
                <ImageUpload v-model="form.coverImage" />
                <span class="form-tip">建议使用清晰横图或正方形图片，作为商品列表主图展示</span>
              </div>
            </el-form-item>
            <el-form-item label="商品图片" class="image-form-item">
              <div class="multi-images">
                <div v-for="(img, idx) in imageList" :key="idx" class="img-item">
                  <el-image :src="img" fit="cover" class="product-image" />
                  <el-button circle type="danger" size="small" class="img-del" @click="imageList.splice(idx, 1)">×</el-button>
                </div>
                <ImageUpload v-model="newImage" @update:model-value="onImageAdd" />
              </div>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.description" type="textarea" :rows="5" placeholder="请输入商品详情、下单说明或注意事项" />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="section-title">
            <strong>价格与规则</strong>
            <span>设置售价、平台抽成、限购和排序</span>
          </div>
          <div class="section-body">
            <el-row :gutter="18">
              <el-col :span="8">
                <el-form-item label="商品价格" prop="price">
                  <el-input-number v-model="form.price" :min="0" :precision="2" class="full-control" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="抽成比例">
                  <el-input-number v-model="form.commissionRate" :min="0" :max="1" :precision="2" :step="0.05" placeholder="默认" :controls="true" class="full-control" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="排序">
                  <el-input-number v-model="form.sortOrder" :min="0" class="full-control" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="18">
              <el-col :span="12">
                <el-form-item label="限购周期">
                  <el-select v-model="form.perUserLimitType" class="full-control" :disabled="form.quantityEnabled === 1">
                    <el-option v-for="item in limitTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <div class="inline-tip">抽成比例填 0.2 表示 20%，留空则使用系统默认比例。开启「可选数量」时不可设置限购。</div>
              </el-col>
            </el-row>
            <el-form-item label="规格选项">
              <div class="variant-editor">
                <div v-for="(v, idx) in variants" :key="idx" class="variant-row">
                  <el-input v-model="v.name" placeholder="规格名(如:王者局)" class="variant-name" />
                  <el-input-number v-model="v.price" :min="0" :precision="2" placeholder="价格" />
                  <el-input-number v-model="v.originalPrice" :min="0" :precision="2" placeholder="原价(选填)" />
                  <el-button type="danger" link @click="variants.splice(idx, 1)">删除</el-button>
                </div>
                <el-button type="primary" link @click="addVariant">+ 添加规格</el-button>
                <div class="form-tip">配置规格后，用户下单须选择规格并按规格价计价；未配置时按「商品价格」计价。</div>
              </div>
            </el-form-item>
            <el-row :gutter="18">
              <el-col :span="8">
                <el-form-item label="可选数量">
                  <el-switch v-model="form.quantityEnabled" :active-value="1" :inactive-value="0" :disabled="form.perUserLimitType !== 0" />
                </el-form-item>
              </el-col>
              <el-col :span="8" v-if="form.quantityEnabled === 1">
                <el-form-item label="单位名">
                  <el-input v-model="form.unitLabel" placeholder="如:小时/局" />
                </el-form-item>
              </el-col>
              <el-col :span="8" v-if="form.quantityEnabled === 1">
                <el-form-item label="最大数量">
                  <el-input-number v-model="form.maxQuantity" :min="1" :max="999" class="full-control" />
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </section>

        <section class="form-section">
          <div class="section-title">
            <strong>展示设置</strong>
            <span>控制商品上下架和首页推荐展示</span>
          </div>
          <div class="section-body compact">
            <el-row :gutter="18">
              <el-col :span="8">
                <el-form-item label="状态">
                  <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="推荐">
                  <el-switch v-model="form.isRecommend" :active-value="1" :inactive-value="0" />
                </el-form-item>
              </el-col>
              <el-col :span="8" v-if="form.isRecommend === 1">
                <el-form-item label="热门分类">
                  <el-select v-model="form.recommendCategoryId" placeholder="选择热门分类" clearable class="full-control">
                    <el-option v-for="c in recommendCategories" :key="c.id" :label="c.name" :value="c.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <div v-if="form.isRecommend === 1" class="inline-tip">不选热门分类时，商品会出现在「全部」；选择后会在首页对应 Tab 展示。</div>
          </div>
        </section>

        <div class="form-actions">
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductDetail, addProduct, updateProduct, getCategoryTree, getRecommendCategories } from '@/api/product'
import { ElMessage } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
const route = useRoute(), router = useRouter()
const isEdit = computed(() => !!route.params.id)
const pageLoading = ref(false), submitting = ref(false), formRef = ref(null), categoryTree = ref([]), recommendCategories = ref([])
const limitTypeOptions = [
  { label: '不限购', value: 0 },
  { label: '永久限购一次', value: 1 },
  { label: '一周限购一次', value: 2 },
  { label: '一月限购一次', value: 3 }
]
const form = reactive({
  name: '', subtitle: '', categoryId: null, coverImage: '', description: '',
  price: null, status: 1, sortOrder: 0, isRecommend: 0, recommendCategoryId: null, commissionRate: null,
  perUserLimitType: 0, quantityEnabled: 0, unitLabel: '', maxQuantity: 24
})
const variants = ref([])
const imageList = ref([])
const newImage = ref('')
const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入商品价格', trigger: 'change' }]
}
function onImageAdd(url) {
  if (url) { imageList.value.push(url); newImage.value = '' }
}
function normalizePerUserLimitType(product) {
  const type = Number(product?.perUserLimitType)
  return [0, 1, 2, 3].includes(type)
    ? type
    : (product?.perUserLimitEnabled === 1 ? 1 : 0)
}
function addVariant() {
  variants.value.push({ name: '', price: null, originalPrice: null, sortOrder: variants.value.length })
}
async function loadDetail() {
  if (!route.params.id) return
  pageLoading.value = true
  try {
    const res = await getProductDetail(route.params.id)
    const data = res.data
    const p = data.product || data
    Object.assign(form, {
      name: p.name, subtitle: p.subtitle || '', categoryId: p.categoryId, coverImage: p.coverImage || '',
      description: p.description || '', price: p.price ?? null,
      status: p.status ?? 1, sortOrder: p.sortOrder || 0, isRecommend: p.isRecommend || 0,
      recommendCategoryId: p.recommendCategoryId ?? null, commissionRate: p.commissionRate ?? null,
      perUserLimitType: normalizePerUserLimitType(p),
      quantityEnabled: p.quantityEnabled ?? 0,
      unitLabel: p.unitLabel || '',
      maxQuantity: p.maxQuantity ?? 24
    })
    variants.value = (p.variants || []).map((v, i) => ({
      name: v.name, price: v.price, originalPrice: v.originalPrice ?? null, sortOrder: v.sortOrder ?? i
    }))
    // 兼容旧数据里的 JSON 数组字符串，提交时统一改为逗号分隔字符串
    if (p.images) {
      try {
        const parsed = JSON.parse(p.images)
        imageList.value = Array.isArray(parsed) ? parsed : [p.images]
      } catch {
        imageList.value = p.images.split(',').filter(Boolean)
      }
    } else {
      imageList.value = []
    }
  } finally { pageLoading.value = false }
}
async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const product = {
      ...form,
      perUserLimitType: Number(form.perUserLimitType ?? 0),
      quantityEnabled: Number(form.quantityEnabled ?? 0),
      images: imageList.value.join(','),
      variants: variants.value
        .filter(v => v.name && v.price != null)
        .map((v, i) => ({ name: v.name.trim(), price: v.price, originalPrice: v.originalPrice, sortOrder: i }))
    }
    if (isEdit.value) product.id = Number(route.params.id)
    isEdit.value ? await updateProduct(product) : await addProduct(product)
    ElMessage.success('操作成功')
    router.push('/product/list')
  } finally { submitting.value = false }
}
function markParentsDisabled(nodes) {
  return nodes.map(n => ({
    ...n,
    disabled: !n.parentId,
    children: n.children?.length ? markParentsDisabled(n.children) : undefined
  }))
}

onMounted(async () => {
  try {
    const [catRes, recRes] = await Promise.all([getCategoryTree(), getRecommendCategories()])
    categoryTree.value = markParentsDisabled(catRes.data || [])
    recommendCategories.value = recRes.data || []
  } catch (e) { /* ignore */ }
  loadDetail()
})
</script>
<style scoped>
.product-form-card {
  overflow: visible;
}

.product-form-header {
  align-items: flex-start;
}

.product-form-header p {
  margin-top: 4px;
  color: #7b8798;
  font-size: 13px;
  font-weight: 400;
}

.product-form {
  max-width: 1180px;
}

.form-section {
  border: 1px solid #e3eaf3;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.form-section + .form-section {
  margin-top: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #edf2f7;
  background: #f8fafc;
}

.section-title strong {
  color: #172033;
  font-size: 15px;
}

.section-title span {
  color: #7b8798;
  font-size: 13px;
}

.section-body {
  padding: 18px 18px 2px;
}

.section-body.compact {
  padding-bottom: 14px;
}

.full-control {
  width: 100%;
}

.image-form-item {
  align-items: flex-start;
}

.upload-field {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.multi-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  min-height: 92px;
}

.img-item {
  position: relative;
  width: 92px;
  height: 92px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e3eaf3;
  background: #f8fafc;
}

.product-image {
  width: 100%;
  height: 100%;
}

.img-del {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  min-height: 22px;
  padding: 0;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.18);
}

.is-parent-node {
  font-weight: 600;
  color: #172033;
}

.form-tip,
.inline-tip {
  color: #7b8798;
  font-size: 12px;
  line-height: 1.7;
}

.form-tip {
  max-width: 320px;
  padding-top: 6px;
}

.inline-tip {
  min-height: 32px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  border: 1px solid #e3eaf3;
  border-radius: 7px;
  background: #f8fafc;
}

.variant-editor {
  width: 100%;
}

.variant-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.variant-name {
  width: 200px;
}

.form-actions {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
  padding: 14px 18px;
  border: 1px solid #e3eaf3;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 -10px 24px rgba(20, 32, 54, 0.06);
  backdrop-filter: blur(8px);
}

:deep(.el-form-item__label) {
  color: #42526e;
  font-weight: 600;
}

:deep(.el-textarea__inner) {
  min-height: 120px !important;
}

@media (max-width: 1100px) {
  :deep(.el-col-12),
  :deep(.el-col-8) {
    max-width: 100%;
    flex: 0 0 100%;
  }

  .upload-field {
    flex-direction: column;
  }
}
</style>
