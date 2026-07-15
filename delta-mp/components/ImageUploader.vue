<template>
  <view class="image-uploader">
    <view v-for="(img, index) in modelValue" :key="index" class="preview-item">
      <image :src="img" mode="aspectFill" lazy-load @click="previewImage(index)" />
      <view class="remove-btn" @click.stop="removeImage(index)">×</view>
    </view>
    <view v-if="modelValue.length < max" class="add-btn" @click="chooseImage">
      <text class="add-icon">+</text>
      <text class="add-text">上传图片</text>
    </view>
  </view>
</template>

<script setup>
import { chooseAndUpload } from '@/api/file'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 9 }
})

const emit = defineEmits(['update:modelValue'])

async function chooseImage() {
  const remain = props.max - props.modelValue.length
  if (remain <= 0) return
  try {
    const urls = await chooseAndUpload(remain)
    emit('update:modelValue', [...props.modelValue, ...urls])
  } catch (e) {
    console.error('upload failed', e)
  }
}

function removeImage(index) {
  const list = [...props.modelValue]
  list.splice(index, 1)
  emit('update:modelValue', list)
}

function previewImage(index) {
  uni.previewImage({
    urls: props.modelValue,
    current: index
  })
}
</script>

<style lang="scss" scoped>
.image-uploader {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;

  .preview-item {
    position: relative;
    width: 160rpx;
    height: 160rpx;

    image {
      width: 100%;
      height: 100%;
      border-radius: 8rpx;
    }

    .remove-btn {
      position: absolute;
      top: -12rpx;
      right: -12rpx;
      width: 36rpx;
      height: 36rpx;
      line-height: 34rpx;
      text-align: center;
      font-size: 28rpx;
      color: #fff;
      background: rgba(0, 0, 0, 0.5);
      border-radius: 50%;
    }
  }

  .add-btn {
    width: 160rpx;
    height: 160rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border: 2rpx dashed rgba(99, 102, 241, 0.3);
    border-radius: 8rpx;

    .add-icon { font-size: 48rpx; color: rgba(99, 102, 241, 0.5); }
    .add-text { font-size: 22rpx; color: #94a3b8; margin-top: 4rpx; }
  }
}
</style>
