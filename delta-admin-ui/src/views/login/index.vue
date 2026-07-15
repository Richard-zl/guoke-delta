<template>
  <div class="login-container" :class="{ 'is-staging': isStaging }">
    <EnvBanner v-if="isStaging" class="login-env-banner" />
    <canvas ref="canvasRef" class="particle-canvas" />
    <div class="login-box">
      <h2 class="login-title">{{ siteStore.siteName }}</h2>
      <p class="login-subtitle">{{ isStaging ? '管理后台登录（测试环境）' : '管理后台登录' }}</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item prop="role">
          <el-radio-group v-model="form.role" class="role-group">
            <el-radio value="admin">管理员</el-radio>
            <el-radio value="cs">客服</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { ElMessage } from 'element-plus'
import EnvBanner from '@/components/EnvBanner.vue'
import { isStagingEnv } from '@/utils/env'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const siteStore = useSiteStore()
const isStaging = isStagingEnv()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '', role: 'admin' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function handleLogin() {
  if (loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    // error handled by request interceptor
  } finally {
    loading.value = false
  }
}

/* ---- 金色粒子动画 ---- */
const canvasRef = ref(null)
let animId = 0
class Particle {
  constructor(w, h) { this.reset(w, h, true) }
  reset(w, h, init = false) {
    this.x = Math.random() * w
    this.y = init ? Math.random() * h : h + 10
    this.r = Math.random() * 2 + 0.5
    this.vx = (Math.random() - 0.5) * 0.6
    this.vy = -(Math.random() * 0.8 + 0.3)
    this.life = 1
    this.decay = Math.random() * 0.006 + 0.002
    const gold = [212, 175, 55]
    const bright = [201, 164, 74]
    const t = Math.random()
    this.r_ = Math.round(gold[0] * t + bright[0] * (1 - t))
    this.g_ = Math.round(gold[1] * t + bright[1] * (1 - t))
    this.b_ = Math.round(gold[2] * t + bright[2] * (1 - t))
  }
  update() { this.x += this.vx; this.y += this.vy; this.life -= this.decay }
  draw(ctx) {
    ctx.globalAlpha = this.life * 0.8
    ctx.fillStyle = `rgb(${this.r_},${this.g_},${this.b_})`
    ctx.beginPath(); ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2); ctx.fill()
  }
}
onMounted(() => {
  const canvas = canvasRef.value; if (!canvas) return
  const ctx = canvas.getContext('2d')
  let w, h, particles = []
  function resize() { w = canvas.width = window.innerWidth; h = canvas.height = window.innerHeight }
  resize(); window.addEventListener('resize', resize)
  for (let i = 0; i < 120; i++) particles.push(new Particle(w, h))
  function loop() {
    ctx.clearRect(0, 0, w, h)
    for (const p of particles) {
      p.update(); p.draw(ctx)
      if (p.life <= 0) p.reset(w, h)
    }
    animId = requestAnimationFrame(loop)
  }
  loop()
})
onUnmounted(() => { cancelAnimationFrame(animId) })
</script>

<style lang="scss" scoped>
.login-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0e0e14;
  position: relative;
  overflow: hidden;
}
.particle-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}
.login-box {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 44px 40px;
  background: rgba(30, 30, 42, 0.85);
  border: 1px solid rgba(212, 175, 55, 0.18);
  border-radius: 16px;
  box-shadow: 0 8px 48px rgba(0, 0, 0, 0.5), 0 0 80px rgba(212, 175, 55, 0.06);
  backdrop-filter: blur(12px);
}
.login-title {
  text-align: center;
  font-size: 26px;
  color: #d4af37;
  margin-bottom: 4px;
  letter-spacing: 2px;
}
.login-subtitle {
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  margin-bottom: 32px;
  font-size: 14px;
}
.role-group {
  width: 100%;
  display: flex;
  justify-content: center;
  :deep(.el-radio) {
    color: rgba(255, 255, 255, 0.7);
  }
  :deep(.el-radio__input.is-checked + .el-radio__label) {
    color: #d4af37;
  }
  :deep(.el-radio__input.is-checked .el-radio__inner) {
    border-color: #d4af37;
    background: #d4af37;
  }
}
.login-btn {
  width: 100%;
  background: linear-gradient(135deg, #c9a44a, #d4af37, #b8860b);
  border: none;
  color: #14141c;
  font-weight: bold;
  font-size: 16px;
  letter-spacing: 4px;
  &:hover {
    background: linear-gradient(135deg, #d4af37, #e6c84e, #c9a44a);
  }
}
/* 深色主题 input 覆盖 */
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(212, 175, 55, 0.15);
  box-shadow: none !important;
  &:hover { border-color: rgba(212, 175, 55, 0.35); }
  &.is-focus { border-color: #d4af37; }
}
:deep(.el-input__inner) {
  color: rgba(255, 255, 255, 0.9);
  &::placeholder { color: rgba(255, 255, 255, 0.3); }
}
:deep(.el-input__prefix .el-icon) {
  color: rgba(212, 175, 55, 0.5);
}
:deep(.el-form-item__error) {
  color: #f56c6c;
}
.login-env-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 20;
}
.is-staging.login-container {
  padding-top: 44px;
}
</style>
