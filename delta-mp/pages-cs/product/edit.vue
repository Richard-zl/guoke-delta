<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="edit-page">
    <view class="form-card">
      <view class="form-item">
        <text class="label">商品名称</text>
        <input v-model="form.name" placeholder="请输入商品名称" class="input" />
      </view>
      <view class="form-item">
        <text class="label">副标题</text>
        <input v-model="form.subtitle" placeholder="副标题（选填）" class="input" />
      </view>
      <view class="form-item">
        <text class="label">分类</text>
        <picker :value="categoryIndex" :range="categoryNames" @change="onCategoryChange">
          <text :class="['pick-text', form.categoryId ? '' : 'placeholder']">{{ selectedCategoryName || '请选择分类' }}</text>
        </picker>
      </view>
      <view class="form-item">
        <text class="label">价格</text>
        <input type="digit" v-model="form.price" placeholder="0.00" class="input" />
      </view>
      <view class="form-item">
        <text class="label">封面图</text>
        <ImageUploader v-model="coverImages" :max="1" />
      </view>
      <view class="form-item">
        <text class="label">商品图片</text>
        <ImageUploader v-model="productImages" :max="6" />
      </view>
      <view class="form-item">
        <text class="label">商品描述</text>
        <textarea v-model="form.description" placeholder="请输入商品描述" class="textarea" />
      </view>
      <view class="form-item">
        <text class="label">状态</text>
        <view class="status-selector">
          <view class="opt" :class="{active: form.status==='ON'}" @click="form.status='ON'">上架</view>
          <view class="opt" :class="{active: form.status==='OFF'}" @click="form.status='OFF'">下架</view>
        </view>
      </view>
    </view>
    <view class="btn-area">
      <view class="submit-btn" @click="doSave">保存</view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import ImageUploader from '@/components/ImageUploader.vue'
import { saveCsProduct, getCsProductDetail, getCsCategoryList } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const isEdit = ref(false)
const form = reactive({ id: '', name: '', subtitle: '', categoryId: '', price: '', description: '', status: 'ON', coverImage: '', images: '' })
const coverImages = ref([])
const productImages = ref([])
const categories = ref([])
const categoryIndex = ref(-1)
const categoryNames = computed(() => categories.value.map(c => c.name))
const selectedCategoryName = computed(() => categories.value.find(c => c.id === form.categoryId)?.name || '')

onLoad(async (opts) => {
  try { const res = await getCsCategoryList({ pageNum: 1, pageSize: 100 }); categories.value = res.data?.records || res.data || [] } catch (e) {}
  if (opts.id) {
    isEdit.value = true
    try {
      const res = await getCsProductDetail(opts.id)
      const data = res.data
      if (data) {
        Object.assign(form, data)
        if (data.coverImage) coverImages.value = [data.coverImage]
        if (data.images) productImages.value = data.images.split(',')
        categoryIndex.value = categories.value.findIndex(c => c.id === data.categoryId)
      }
    } catch (e) {}
  }
})

function onCategoryChange(e) {
  categoryIndex.value = e.detail.value
  form.categoryId = categories.value[e.detail.value]?.id || ''
}

async function doSave() {
  if (!form.name) return uni.showToast({ title: '请输入名称', icon: 'none' })
  if (!form.price) return uni.showToast({ title: '请输入价格', icon: 'none' })
  form.coverImage = coverImages.value[0] || ''
  form.images = productImages.value.join(',')
  form.price = Number(form.price)
  try {
    await saveCsProduct(form)
    uni.showToast({ title: '保存成功' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (e) {}
}
</script>
<style lang="scss" scoped>
.edit-page { background: #ffffff; min-height: 100vh; }
.form-card { margin: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx; padding: 0 24rpx; }
.form-item { padding: 24rpx 0; border-bottom: 1rpx solid rgba(0,0,0,0.04);
  .label { font-size: 28rpx; color: rgba(0,0,0,0.85); display: block; margin-bottom: 12rpx; } .input { background: rgba(0,0,0,0.05); padding: 16rpx; border-radius: 8rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); }
  .pick-text { font-size: 26rpx; color: rgba(0,0,0,0.85); padding: 16rpx; background: rgba(0,0,0,0.05); border-radius: 8rpx; &.placeholder { color: rgba(0,0,0,0.15); } }
}
.textarea { background: rgba(0,0,0,0.05); padding: 16rpx; border-radius: 8rpx; font-size: 26rpx; width: 100%; height: 200rpx; color: rgba(0,0,0,0.85); }
.status-selector { display: flex; gap: 16rpx; .opt { padding: 12rpx 32rpx; background: rgba(0,0,0,0.05); border-radius: 8rpx; font-size: 26rpx; &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; } } }
.btn-area { padding: 40rpx 24rpx; }
.submit-btn { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; text-align: center; padding: 24rpx; border-radius: 999rpx; font-size: 30rpx; }
</style>
