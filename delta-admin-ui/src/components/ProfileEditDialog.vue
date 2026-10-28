<template>
  <el-dialog v-model="visible" title="编辑资料" width="400px" destroy-on-close @close="handleClose">
    <el-form ref="formRef" :model="form" label-width="80px">
      <el-form-item label="昵称" required>
        <el-input v-model="form.nickname" placeholder="请输入昵称" maxlength="20" show-word-limit />
      </el-form-item>
      <el-form-item label="头像">
        <ImageUpload v-model="form.avatar" />
      </el-form-item>
      <el-form-item label="原密码">
        <el-input v-model="form.oldPassword" type="password" show-password placeholder="不修改请留空" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位，不修改请留空" />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
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
const form = reactive({ nickname: '', avatar: '', oldPassword: '', newPassword: '', confirmPassword: '' })

watch(visible, async (v) => {
  if (v) {
    try {
      const res = await getCsProfile()
      const d = res.data
      form.nickname = d?.nickname || ''
      form.avatar = d?.avatar || ''
      form.oldPassword = ''
      form.newPassword = ''
      form.confirmPassword = ''
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
  const oldPassword = form.oldPassword.trim()
  const newPassword = form.newPassword.trim()
  const confirmPassword = form.confirmPassword.trim()
  const changingPassword = oldPassword || newPassword || confirmPassword
  if (changingPassword) {
    if (!oldPassword) {
      ElMessage.warning('请输入原密码')
      return
    }
    if (newPassword.length < 6) {
      ElMessage.warning('新密码至少 6 位')
      return
    }
    if (newPassword !== confirmPassword) {
      ElMessage.warning('两次输入的新密码不一致')
      return
    }
  }
  loading.value = true
  try {
    const payload = { nickname: form.nickname.trim(), avatar: form.avatar || undefined }
    if (changingPassword) {
      payload.oldPassword = oldPassword
      payload.newPassword = newPassword
    }
    await updateCsProfile(payload)
    emit('saved', { nickname: form.nickname.trim(), avatar: form.avatar })
    ElMessage.success('保存成功')
    visible.value = false
  } finally {
    loading.value = false
  }
}
</script>
