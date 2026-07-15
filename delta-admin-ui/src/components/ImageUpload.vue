<template>
  <el-upload
    :http-request="handleUpload"
    :show-file-list="false"
    :before-upload="beforeUpload"
    accept="image/*"
  >
    <img v-if="modelValue" :src="modelValue" class="upload-image" />
    <el-icon v-else class="upload-icon"><Plus /></el-icon>
  </el-upload>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '@/api/business'

defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])

function beforeUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) ElMessage.error('只能上传图片文件')
  if (!isLt5M) ElMessage.error('图片大小不能超过 5MB')
  return isImage && isLt5M
}

async function handleUpload({ file }) {
  try {
    const res = await uploadFile(file)
    emit('update:modelValue', res.data)
  } catch (e) {
    ElMessage.error('上传失败')
  }
}
</script>

<style scoped>
.upload-image {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 6px;
}
.upload-icon {
  width: 120px;
  height: 120px;
  font-size: 28px;
  color: #8c939d;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.upload-icon:hover {
  border-color: #409eff;
  color: #409eff;
}
</style>
