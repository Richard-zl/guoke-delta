<template>
  <view class="agreement-page">
    <view class="section">
      <text class="title">{{ siteStore.fullName }}隐私政策</text>
      <text class="update-time">更新日期：2026年1月1日</text>
    </view>

    <view class="section">
      <text class="heading">一、引言</text>
      <text class="content">{{ siteStore.fullName }}（以下简称"我们"）深知个人信息对您的重要性，我们将按照法律法规的规定，保护您的个人信息及隐私安全。我们制定本隐私政策以帮助您了解我们如何收集、使用、存储和保护您的信息。</text>
    </view>

    <view class="section">
      <text class="heading">二、我们收集的信息</text>
      <text class="content">2.1 注册信息：当您注册账号时，我们会收集您的用户昵称、头像、手机号码等基本信息。</text>
      <text class="content">2.2 订单信息：当您下单时，我们会收集您的游戏账号、联系方式、订单备注等信息，用于提供服务。</text>
      <text class="content">2.3 交易信息：我们会收集您的支付记录、钱包余额、提现记录等交易相关信息。</text>
      <text class="content">2.4 设备信息：我们可能会收集您的设备型号、操作系统版本、唯一设备标识符等信息，用于保障服务安全。</text>
      <text class="content">2.5 日志信息：我们会记录您的访问时间、浏览页面、操作日志等信息，用于改善服务质量。</text>
    </view>

    <view class="section">
      <text class="heading">三、我们如何使用您的信息</text>
      <text class="content">3.1 为您提供撮合、订单管理、在线沟通等核心服务。</text>
      <text class="content">3.2 用于身份验证、安全防护、风控检测，保障您的账号和资金安全。</text>
      <text class="content">3.3 用于订单通知、系统公告、服务提醒等必要的消息推送。</text>
      <text class="content">3.4 用于改进和优化我们的产品和服务体验。</text>
      <text class="content">3.5 用于处理您的投诉、纠纷等客服需求。</text>
    </view>

    <view class="section">
      <text class="heading">四、信息共享与披露</text>
      <text class="content">4.1 我们不会将您的个人信息出售给任何第三方。</text>
      <text class="content">4.2 为完成服务，您的部分订单信息（如游戏账号）将提供给接单员，但我们会要求接单员严格保密。</text>
      <text class="content">4.3 为完成支付，我们会将必要的交易信息共享给支付服务提供商。</text>
      <text class="content">4.4 当法律法规要求或政府机关依法调查时，我们可能会依法披露您的相关信息。</text>
    </view>

    <view class="section">
      <text class="heading">五、信息存储与保护</text>
      <text class="content">5.1 您的个人信息存储于中华人民共和国境内的安全服务器上。</text>
      <text class="content">5.2 我们采用加密传输、访问控制、安全审计等技术和管理措施保护您的信息安全。</text>
      <text class="content">5.3 我们会在实现服务目的所必需的最短时间内保留您的个人信息。当超出保留期限后，我们将删除或匿名化处理您的信息。</text>
    </view>

    <view class="section">
      <text class="heading">六、您的权利</text>
      <text class="content">6.1 您有权访问、更正您的个人信息，可通过"编辑资料"功能自行操作。</text>
      <text class="content">6.2 您有权删除您的个人信息或注销账号，可联系客服进行处理。</text>
      <text class="content">6.3 您有权撤回对信息收集的授权同意，但这可能导致我们无法为您提供对应服务。</text>
    </view>

    <view class="section">
      <text class="heading">七、未成年人保护</text>
      <text class="content">7.1 本平台的服务面向年满18周岁的成年人。如果您是未成年人，请在法定监护人的陪同和指导下使用本平台。</text>
      <text class="content">7.2 如我们发现在未获得监护人同意的情况下收集了未成年人的信息，将尽快删除相关数据。</text>
    </view>

    <view class="section">
      <text class="heading">八、隐私政策的更新</text>
      <text class="content">8.1 我们可能会适时修订本隐私政策。重大变更时，我们将通过平台公告或推送通知等方式告知您。</text>
      <text class="content">8.2 更新后的隐私政策发布后，您继续使用我们的服务即表示您同意更新后的内容。</text>
    </view>

    <view class="section">
      <text class="heading">九、联系我们</text>
      <text class="content">如您对本隐私政策有任何疑问或建议，可通过平台"系统通知"或在线客服与我们联系，我们将在15个工作日内回复您。</text>
    </view>
  </view>
</template>

<script setup>
import { useSiteStore } from '@/store/site'
const siteStore = useSiteStore()
</script>

<style lang="scss" scoped>
.agreement-page {
  padding: 32rpx;
  background: #f1f5f9;
  min-height: 100vh;
}
.section {
  margin-bottom: 32rpx;
}
.title {
  display: block;
  font-size: 36rpx;
  font-weight: bold;
  color: #ff4544;
  text-align: center;
  margin-bottom: 8rpx;
}
.update-time {
  display: block;
  font-size: 24rpx;
  color: #94a3b8;
  text-align: center;
  margin-bottom: 16rpx;
}
.heading {
  display: block;
  font-size: 30rpx;
  font-weight: bold;
  color: #0f172a;
  margin-bottom: 12rpx;
}
.content {
  display: block;
  font-size: 28rpx;
  color: #64748b;
  line-height: 1.8;
  margin-bottom: 8rpx;
}
</style>
