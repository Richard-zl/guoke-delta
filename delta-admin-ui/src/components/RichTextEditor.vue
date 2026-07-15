<template>
  <div class="rich-text-editor">
    <div class="toolbar">
      <el-button-group>
        <el-button size="small" @click="exec('bold')"><strong>B</strong></el-button>
        <el-button size="small" @click="exec('italic')"><em>I</em></el-button>
        <el-button size="small" @click="exec('underline')"><u>U</u></el-button>
        <el-button size="small" @click="exec('strikeThrough')"><s>S</s></el-button>
      </el-button-group>
      <el-button-group>
        <el-button size="small" @click="exec('formatBlock', 'h2')">H2</el-button>
        <el-button size="small" @click="exec('formatBlock', 'h3')">H3</el-button>
        <el-button size="small" @click="exec('formatBlock', 'p')">P</el-button>
      </el-button-group>
      <el-button-group>
        <el-button size="small" @click="exec('insertUnorderedList')">● 列表</el-button>
        <el-button size="small" @click="exec('insertOrderedList')">1. 列表</el-button>
      </el-button-group>
      <el-button-group>
        <el-button size="small" @click="insertLink">🔗 链接</el-button>
        <el-button size="small" @click="insertImage">🖼️ 图片</el-button>
      </el-button-group>
      <el-button-group>
        <el-button size="small" :type="mode === 'visual' ? 'primary' : ''" @click="mode = 'visual'">可视化</el-button>
        <el-button size="small" :type="mode === 'source' ? 'primary' : ''" @click="mode = 'source'">源代码</el-button>
      </el-button-group>
    </div>
    <div v-show="mode === 'visual'" ref="editorRef" class="editor-content" contenteditable="true" @input="onInput" @blur="onInput" v-html="innerHtml" />
    <el-input v-show="mode === 'source'" type="textarea" :rows="rows" :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" />
  </div>
</template>
<script setup>
import { ref, watch, nextTick } from 'vue'
import { uploadFile } from '@/api/business'

const props = defineProps({
  modelValue: { type: String, default: '' },
  rows: { type: Number, default: 10 }
})
const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
const innerHtml = ref(props.modelValue || '')
const mode = ref('visual')

watch(() => props.modelValue, (val) => {
  if (editorRef.value && val !== editorRef.value.innerHTML) {
    innerHtml.value = val
  }
})

function onInput() {
  if (editorRef.value) {
    emit('update:modelValue', editorRef.value.innerHTML)
  }
}

function exec(cmd, value) {
  document.execCommand(cmd, false, value || null)
  editorRef.value?.focus()
  onInput()
}

function insertLink() {
  const url = prompt('请输入链接地址', 'https://')
  if (url) exec('createLink', url)
}

async function insertImage() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    try {
      const res = await uploadFile(file)
      const url = res.data || res
      exec('insertImage', url)
    } catch (err) {
      console.error('upload failed', err)
    }
  }
  input.click()
}
</script>
<style scoped>
.rich-text-editor { border: 1px solid #dcdfe6; border-radius: 4px; overflow: hidden; }
.toolbar { padding: 6px 8px; background: #f5f7fa; border-bottom: 1px solid #dcdfe6; display: flex; gap: 8px; flex-wrap: wrap; }
.editor-content { min-height: 200px; padding: 12px 16px; outline: none; font-size: 14px; line-height: 1.6; overflow-y: auto; max-height: 500px; }
.editor-content :deep(img) { max-width: 100%; height: auto; }
.editor-content :deep(a) { color: #409eff; }
</style>
