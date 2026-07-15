<template>
  <view class="gold-dust-wrap">
    <canvas type="2d" :id="canvasId" class="gold-canvas" />
  </view>
</template>

<script setup>
import { onMounted, onUnmounted, getCurrentInstance } from 'vue'

const canvasId = 'gd_' + Math.random().toString(36).slice(2, 8)
const instance = getCurrentInstance()

let cvs = null
let ctx = null
let raf = null
let running = true
let W = 375
let H = 700

const COUNT = 40
const dots = []

function rand(a, b) { return Math.random() * (b - a) + a }

function spawn(d, init) {
  d.x = rand(0, W)
  d.y = init ? rand(0, H) : rand(H + 10, H + 80)
  const big = Math.random() < 0.1
  d.r = big ? rand(2.5, 4.5) : rand(1, 3)
  d.vx = rand(-0.15, 0.15)
  d.vy = big ? rand(-0.2, -0.06) : rand(-0.4, -0.1)
  d.alpha = init ? rand(0.25, 0.7) : 0
  d.peak = big ? rand(0.7, 1) : rand(0.45, 0.85)
  d.phase = init ? 1 : 0
  d.tw = rand(0, Math.PI * 2)
  d.twSpeed = rand(0.02, 0.06)
  d.glowR = big ? d.r * 6 : d.r * 4
}

function draw() {
  if (!running || !ctx) return
  ctx.clearRect(0, 0, W, H)

  for (const d of dots) {
    if (d.phase === 0) {
      d.alpha += 0.006
      if (d.alpha >= d.peak) { d.alpha = d.peak; d.phase = 1 }
    }
    if (d.phase === 1) {
      d.tw += d.twSpeed
      d.alpha = d.peak * (0.7 + 0.3 * Math.sin(d.tw))
      if (d.y < H * 0.2) d.phase = 2
    }
    if (d.phase === 2) {
      d.alpha -= 0.005
      if (d.alpha <= 0) { spawn(d, false); continue }
    }

    d.x += d.vx
    d.y += d.vy
    d.vx += rand(-0.008, 0.008)
    d.vx = Math.max(-0.25, Math.min(0.25, d.vx))

    if (d.y < -30 || d.x < -30 || d.x > W + 30) {
      spawn(d, false)
      continue
    }

    if (d.alpha < 0.02) continue

    ctx.globalAlpha = d.alpha * 0.15
    ctx.fillStyle = '#ffd700'
    ctx.beginPath()
    ctx.arc(d.x, d.y, d.glowR, 0, Math.PI * 2)
    ctx.fill()

    ctx.globalAlpha = d.alpha
    ctx.fillStyle = '#ffd700'
    ctx.beginPath()
    ctx.arc(d.x, d.y, d.r, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.globalAlpha = 1
  scheduleNext()
}

function scheduleNext() {
  if (!running) return
  if (cvs && cvs.requestAnimationFrame) {
    raf = cvs.requestAnimationFrame(draw)
  } else {
    raf = setTimeout(draw, 16)
  }
}

function stop() {
  running = false
  if (raf == null) return
  if (cvs && cvs.cancelAnimationFrame) cvs.cancelAnimationFrame(raf)
  else clearTimeout(raf)
  raf = null
}

onMounted(() => {
  const sys = uni.getSystemInfoSync()
  W = sys.windowWidth || 375
  H = sys.windowHeight || 700
  const dpr = sys.pixelRatio || 2

  setTimeout(() => {
    uni.createSelectorQuery()
      .in(instance.proxy)
      .select('#' + canvasId)
      .fields({ node: true, size: true })
      .exec((res) => {
        if (!res || !res[0] || !res[0].node) return
        cvs = res[0].node
        ctx = cvs.getContext('2d')
        cvs.width = W * dpr
        cvs.height = H * dpr
        ctx.scale(dpr, dpr)

        for (let i = 0; i < COUNT; i++) {
          const d = {}
          spawn(d, true)
          dots.push(d)
        }

        draw()
      })
  }, 300)
})

onUnmounted(() => { stop() })
</script>

<style lang="scss" scoped>
.gold-dust-wrap {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
}
.gold-canvas {
  width: 100%;
  height: 100%;
}
</style>
