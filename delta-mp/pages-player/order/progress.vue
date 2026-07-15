<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="progress-page">
    <textarea v-model="content" placeholder="描述当前进度" :maxlength="500" />
    <ImageUploader v-model="images" :max="6" />
    <view class="btn" @click="submit">提交</view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import ImageUploader from '@/components/ImageUploader.vue'
import { submitWorkProgress } from '@/api/player'
const orderId = ref(0)
const content = ref('')
const images = ref([])
onLoad((opts) => { orderId.value=opts.orderId })
async function submit() {
  if (!content.value) return uni.showToast({title:'请描述进度',icon:'none'})
  await submitWorkProgress(orderId.value, { content:content.value, images:images.value.join(',') })
  uni.showToast({title:'已提交'}); setTimeout(()=>uni.navigateBack(),1500)
}
</script>
<style lang="scss" scoped>
.progress-page { padding:24rpx; background: #ffffff; min-height: 100vh; }
textarea { width:100%; height:240rpx; background: #f1f5f9; border-radius:12rpx; padding:24rpx; font-size:28rpx; margin-bottom:24rpx; }
.btn { height:88rpx; line-height:88rpx; text-align:center; background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color:#ffffff; font-weight:bold; font-size:32rpx; border-radius:999rpx; margin-top:40rpx; }
</style>