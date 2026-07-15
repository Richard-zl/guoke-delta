<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="audit-page" v-if="player">
    <view class="card">
      <view class="profile-top">
        <image class="avatar" :src="player.avatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
        <view class="profile-info">
          <text class="name">{{ player.nickname || player.realName }}</text>
          <text class="status" :class="player.status?.toLowerCase()">{{ STATUS_TEXT[player.status] || player.status }}</text>
        </view>
      </view>
      <view class="info-row"><text class="label">真实姓名</text><text class="value">{{ player.realName }}</text></view>
      <view class="info-row"><text class="label">手机号</text><text class="value">{{ player.phone }}</text></view>
      <view class="info-row"><text class="label">{{ clubIdLabel }}</text><text class="value">{{ player.gameLevel }}</text></view>
      <view class="info-row"><text class="label">擅长服务</text><text class="value">{{ player.serviceTypes }}</text></view>
      <view v-if="player.skillTags && player.skillTags.length" class="info-row"><text class="label">技能标签</text><text class="value">{{ Array.isArray(player.skillTags) ? player.skillTags.join(', ') : player.skillTags }}</text></view>
    </view>
    <!-- 证明图片 -->
    <view class="card" v-if="player.proofImages">
      <text class="card-title">证明截图</text>
      <view class="images">
        <image v-for="(img,i) in player.proofImages.split(',')" :key="i" :src="img" mode="aspectFill" class="img" lazy-load @click="previewImg(player.proofImages.split(','),i)" />
      </view>
    </view>
    <!-- 审核操作 -->
    <view v-if="player.status==='PENDING' || player.status==='FROZEN'" class="card">
      <text class="card-title">审核操作</text>
      <view class="form-item">
        <text class="label">审核意见</text>
        <textarea v-model="auditRemark" placeholder="请输入审核意见（可选）" class="textarea" />
      </view>
      <view class="btn-group">
        <view class="btn-approve" @click="doAudit('ACTIVE')">通过</view>
        <view class="btn-reject" @click="doAudit('REJECTED')">拒绝</view>
      </view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getCsPlayerDetail, auditCsPlayer } from '@/pages-cs/api/cs'
import { useSiteBranding } from '@/composables/useSiteBranding'
const { pageBlocked } = useAuditPageGuard()
const { siteStore, clubIdLabel } = useSiteBranding()

const STATUS_TEXT = { PENDING: '待审核', ACTIVE: '已激活', FROZEN: '已冻结', REJECTED: '已拒绝' }
const player = ref(null)
const playerId = ref('')
const auditRemark = ref('')

onLoad(async (opts) => {
  if (!siteStore.configLoaded) await siteStore.fetchSiteConfig()
  playerId.value = opts.id
  const res = await getCsPlayerDetail(opts.id)
  player.value = res.data
})

async function doAudit(status) {
  const action = status === 'ACTIVE' ? '通过' : '拒绝'
  uni.showModal({ title: '确认', content: `确定${action}该接单员申请？`, success: async (r) => {
    if (r.confirm) {
      try {
        await auditCsPlayer(playerId.value, { status, rejectReason: auditRemark.value })
        uni.showToast({ title: `已${action}` })
        setTimeout(() => uni.navigateBack(), 1500)
      } catch (e) {}
    }
  }})
}
function previewImg(imgs, idx) { uni.previewImage({ urls: imgs, current: idx }) }
</script>
<style lang="scss" scoped>
.audit-page { background: #ffffff; min-height: 100vh; }
.card { margin: 20rpx 24rpx; padding: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx; }
.card-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; color: rgba(0,0,0,0.85); }
.profile-top { display: flex; align-items: center; gap: 16rpx; margin-bottom: 20rpx; padding-bottom: 20rpx; border-bottom: 1rpx solid rgba(0,0,0,0.04);
  .avatar { width: 96rpx; height: 96rpx; border-radius: 50%; }
  .profile-info { .name { font-size: 32rpx; font-weight: bold; display: block; color: rgba(0,0,0,0.85); } .status { font-size: 24rpx; display: block; margin-top: 4rpx; &.pending { color: #ff9900; } &.active { color: #07c160; } &.frozen { color: #ee0a24; } } }
}
.info-row { display: flex; padding: 10rpx 0; .label { font-size: 26rpx; color: rgba(0,0,0,0.3); width: 140rpx; } .value { font-size: 26rpx; color: rgba(0,0,0,0.85); flex: 1; } }
.images { display: flex; gap: 12rpx; flex-wrap: wrap; .img { width: 200rpx; height: 200rpx; border-radius: 8rpx; } }
.form-item { margin-bottom: 24rpx; .label { font-size: 26rpx; color: rgba(0,0,0,0.85); display: block; margin-bottom: 12rpx; } }
.textarea { background: rgba(0,0,0,0.05); padding: 16rpx; border-radius: 8rpx; font-size: 26rpx; width: 100%; height: 160rpx; color: rgba(0,0,0,0.85); }
.btn-group { display: flex; gap: 20rpx; }
.btn-approve { flex: 1; text-align: center; padding: 20rpx; background: #07c160; color: #fff; border-radius: 999rpx; font-size: 28rpx; }
.btn-reject { flex: 1; text-align: center; padding: 20rpx; background: #ee0a24; color: #fff; border-radius: 999rpx; font-size: 28rpx; }
</style>
