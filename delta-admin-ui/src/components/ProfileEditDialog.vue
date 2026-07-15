<template>
  <el-dialog v-model="visible" title="编辑资料" width="400px" destroy-on-close @close="handleClose">
    <el-form ref="formRef" :model="form" label-width="80px">
      <el-form-item label="昵称" required>
        <el-input v-model="form.nickname" placeholder="请输入昵称" maxlength="20" show-word-limit />
      </el-form-item>
      <el-form-item label="头像">
        <ImageUpload v-model="form.avatar" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
import { getCsProfile, updateCsProfile } from '@/api/auth'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { if (!v) emit('update:modelValue', false) })

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ nickname: '', avatar: '' })

watch(visible, async (v) => {
  if (v) {
    try {
      const res = await getCsProfile()
      const d = res.data
      form.nickname = d?.nickname || ''
      form.avatar = d?.avatar || ''
    } catch (_) {}
  }
})

function handleClose() {
  emit('update:modelValue', false)
}

async function handleSave() {
  if (!form.nickname?.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }
  loading.value = true
  try {
    await updateCsProfile({ nickname: form.nickname.trim(), avatar: form.avatar || undefined })
    emit('saved', { nickname: form.nickname.trim(), avatar: form.avatar })
    ElMessage.success('保存成功')
    visible.value = false
  } finally {
    loading.value = false
  }
}
</script>
