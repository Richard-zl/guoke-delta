import { onMounted, onUnmounted, getCurrentInstance } from 'vue'

/**
 * 金沙粒子背景 - Canvas 2D 实现
 * 在页面 <script setup> 中调用 useGoldDust()
 * 在页面 template 中添加 <canvas type="2d" id="goldDust" class="gold-dust-canvas" />
 */
export function useGoldDust(canvasId = 'goldDust') {
  const instance = getCurrentInstance()

  let cvs = null
  let ctx = null
  let raf = null
  let running = true
  let W = 375
  let H = 700

  const COUNT = 120
  const dots = []

  function rand(a, b) { return Math.random() * (b - a) + a }

  function spawn(d, init) {
    d.x = rand(0, W)
    d.y = init ? rand(0, H) : rand(H + 10, H + 60)
    const big = Math.random() < 0.08
    d.r = big ? rand(1.4, 2.2) : rand(0.45, 1.15)
    d.vx = rand(-0.12, 0.12)
    d.vy = big ? rand(-0.18, -0.06) : rand(-0.34, -0.09)
    d.alpha = init ? rand(0.08, 0.32) : 0
    d.peak = big ? rand(0.22, 0.42) : rand(0.12, 0.3)
    d.phase = init ? 1 : 0 // 0=fadein 1=live 2=fadeout
    d.tw = rand(0, Math.PI * 2)
    d.twSpeed = rand(0.01, 0.03)
    d.glowR = big ? d.r * 4.5 : d.r * 3.2
  }

  function draw() {
    if (!running || !ctx) return
    ctx.clearRect(0, 0, W, H)

    for (const d of dots) {
      // Fade in
      if (d.phase === 0) {
        d.alpha += 0.003
        if (d.alpha >= d.peak) { d.alpha = d.peak; d.phase = 1 }
      }
      // Live with twinkle
      if (d.phase === 1) {
        d.tw += d.twSpeed
        d.alpha = d.peak * (0.75 + 0.25 * Math.sin(d.tw))
        if (d.y < H * 0.15) d.phase = 2
      }
      // Fade out
      if (d.phase === 2) {
        d.alpha -= 0.003
        if (d.alpha <= 0) { spawn(d, false); continue }
      }

      // Drift
      d.x += d.vx
      d.y += d.vy
      d.vx += rand(-0.005, 0.005)
      d.vx = Math.max(-0.15, Math.min(0.15, d.vx))

      // Off screen -> respawn
      if (d.y < -20 || d.x < -20 || d.x > W + 20) {
        spawn(d, false)
        continue
      }

      if (d.alpha < 0.01) continue

      const grad = ctx.createRadialGradient(d.x, d.y, 0, d.x, d.y, d.glowR)
      grad.addColorStop(0, `rgba(99, 102, 241, ${d.alpha * 0.35})`)
      grad.addColorStop(0.5, `rgba(99, 102, 241, ${d.alpha * 0.1})`)
      grad.addColorStop(1, 'rgba(99, 102, 241, 0)')
      ctx.fillStyle = grad
      ctx.beginPath()
      ctx.arc(d.x, d.y, d.glowR, 0, Math.PI * 2)
      ctx.fill()

      // Core dot
      ctx.globalAlpha = d.alpha
      ctx.fillStyle = '#6366f1'
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
}
