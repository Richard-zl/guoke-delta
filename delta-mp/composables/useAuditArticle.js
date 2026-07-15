/**
 * 审核期资讯列表卡片样式与跳转（仅审核模块使用）
 */

/** 跳转资讯详情 */
export function openAuditArticleDetail(article) {
  if (!article?.id) return
  uni.navigateTo({ url: `/pages/audit/article-detail?id=${article.id}` })
}

/** 格式化发布日期为 MM-DD */
export function formatAuditDate(dateStr) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length >= 3) return `${parts[1]}-${parts[2]}`
  return dateStr
}
