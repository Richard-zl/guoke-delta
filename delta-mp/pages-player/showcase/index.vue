<template>
  <view class="page">
    <view class="card">
      <text class="label">上墙状态</text>
      <text class="status" :class="info.onWall ? 'on' : 'off'">{{ info.onWall ? '已上墙' : '未上墙' }}</text>
      <text class="hint">封面、简介、排序由运营配置。重录语音不会下架。</text>
    </view>
    <view class="card">
      <text class="label">语音介绍（15–60秒）</text>
      <text v-if="info.introVoiceSeconds" class="meta">当前 {{ info.introVoiceSeconds }} 秒</text>
      <view class="row-btns">
        <view v-if="info.introVoiceUrl" class="ghost" @click="playVoice">试听</view>
        <view class="ghost" @click="toggleRecord">{{ recording ? '停止并保存' : (info.introVoiceUrl ? '重录' : '开始录音') }}</view>
      </view>
      <text v-if="recording" class="rec">录音中 {{ recordSeconds }}s</text>
    </view>
    <view class="card">
      <text class="label">高光图库（最多9张）</text>
      <view class="gallery">
        <view v-for="(img, i) in images" :key="img" class="g-item" @longpress="removeImage(i)">
          <image :src="img" mode="aspectFill" @click="preview(i)" />
        </view>
        <view v-if="images.length < 9" class="add" @click="addImages">+</view>
      </view>
      <text class="hint">长按删除。对外展示由运营勾选。</text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { getMyShowcase, saveMyVoice, saveMyGallery } from '@/api/showcase'
import { upload, chooseAndUpload } from '@/api/file'

const info = reactive({ onWall: false, introVoiceUrl: '', introVoiceSeconds: 0 })
const images = ref([])
const recording = ref(false)
const recordSeconds = ref(0)
let recorder = null
let timer = null
let startedAt = 0
let audio = null

onLoad(() => loadMe())
onUnload(() => {
  stopTimer()
  if (recorder && recording.value) recorder.stop()
  if (audio) { audio.stop(); audio.destroy() }
})

async function loadMe() {
  const res = await getMyShowcase()
  const data = res.data || {}
  info.onWall = !!data.onWall
  info.introVoiceUrl = data.introVoiceUrl || ''
  info.introVoiceSeconds = data.introVoiceSeconds || 0
  images.value = data.highlightImages || []
}

function playVoice() {
  if (!info.introVoiceUrl) return
  if (audio) { audio.stop(); audio.destroy() }
  audio = uni.createInnerAudioContext()
  audio.src = info.introVoiceUrl
  audio.onError(() => uni.showToast({ title: '语音加载失败', icon: 'none' }))
  audio.play()
}

function toggleRecord() {
  if (recording.value) { recorder.stop(); return }
  recorder = uni.getRecorderManager()
  recorder.onStop(async (res) => {
    recording.value = false
    stopTimer()
    const seconds = Math.round((Date.now() - startedAt) / 1000)
    if (seconds < 15) return uni.showToast({ title: '至少录15秒', icon: 'none' })
    try {
      const url = await upload(res.tempFilePath)
      await saveMyVoice({ url, seconds: Math.min(seconds, 60) })
      uni.showToast({ title: '已保存', icon: 'success' })
      loadMe()
    } catch (e) {}
  })
  recorder.onError(() => {
    recording.value = false
    stopTimer()
    uni.showToast({ title: '录音失败', icon: 'none' })
  })
  startedAt = Date.now()
  recordSeconds.value = 0
  recording.value = true
  timer = setInterval(() => {
    recordSeconds.value = Math.round((Date.now() - startedAt) / 1000)
    if (recordSeconds.value >= 60) recorder.stop()
  }, 500)
  recorder.start({ duration: 60000, format: 'mp3' })
}

function stopTimer() { if (timer) { clearInterval(timer); timer = null } }

async function addImages() {
  const remain = 9 - images.value.length
  if (remain <= 0) return
  const urls = await chooseAndUpload(remain)
  const next = images.value.concat(urls).slice(0, 9)
  await saveMyGallery({ urls: next })
  images.value = next
}

function removeImage(i) {
  uni.showModal({
    title: '删除这张图？',
    success: async (r) => {
      if (!r.confirm) return
      const next = images.value.filter((_, idx) => idx !== i)
      await saveMyGallery({ urls: next })
      images.value = next
    }
  })
}

function preview(i) { uni.previewImage({ urls: images.value, current: i }) }
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f1f5f9; padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; }
.label { display: block; font-size: 28rpx; font-weight: 700; }
.status { display: inline-block; margin-top: 12rpx; font-size: 26rpx; }
.status.on { color: #16a34a; }
.status.off { color: #94a3b8; }
.hint { display: block; margin-top: 12rpx; font-size: 22rpx; color: #94a3b8; }
.meta { display: block; margin-top: 8rpx; color: #64748b; font-size: 24rpx; }
.row-btns { display: flex; gap: 16rpx; margin-top: 16rpx; }
.ghost { flex: 1; height: 72rpx; line-height: 72rpx; text-align: center; border: 1rpx solid #ff4544; color: #ff4544; border-radius: 12rpx; }
.rec { display: block; margin-top: 12rpx; color: #ff4544; }
.gallery { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
.g-item image { width: 160rpx; height: 160rpx; border-radius: 12rpx; }
.add { width: 160rpx; height: 160rpx; border-radius: 12rpx; background: #f1f5f9; color: #94a3b8; text-align: center; line-height: 160rpx; font-size: 48rpx; }
</style>
