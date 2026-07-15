/**
 * 微信审核期间展示的静态内容（无交易、无代练相关文案）
 */

import { AUDIT_ARTICLES } from './auditArticles'

export { AUDIT_ARTICLES }

export const AUDIT_CATEGORIES = [
  { id: 'guide', name: '游戏百科' },
  { id: 'newbie', name: '新手指南' },
  { id: 'news', name: '官方资讯' },
  { id: 'club', name: '俱乐部动态' },
  { id: 'help', name: '帮助中心' }
]

/** 栏目 id → 名称 */
export const AUDIT_CATEGORY_MAP = Object.fromEntries(
  AUDIT_CATEGORIES.map((c) => [c.id, c.name])
)

/** 根据文章 id 查找全文 */
export function getAuditArticleById(id) {
  if (!id) return null
  for (const list of Object.values(AUDIT_ARTICLES)) {
    const found = list.find((a) => a.id === id)
    if (found) return found
  }
  return null
}

/** 全部资讯（扁平列表，按日期倒序） */
export function getAllAuditArticles() {
  return Object.values(AUDIT_ARTICLES)
    .flat()
    .slice()
    .sort((a, b) => (b.publishDate || '').localeCompare(a.publishDate || ''))
}

/** 同栏目推荐（排除当前 id，最多 3 条） */
export function getRelatedAuditArticles(article, limit = 3) {
  if (!article?.categoryId) return []
  const list = AUDIT_ARTICLES[article.categoryId] || []
  return list.filter((a) => a.id !== article.id).slice(0, limit)
}

/** 首页审核期展示的精选资讯 */
export const AUDIT_HOME_ARTICLES = [
  AUDIT_ARTICLES.news[0],
  AUDIT_ARTICLES.guide[0],
  AUDIT_ARTICLES.guide[1],
  AUDIT_ARTICLES.newbie[0],
  AUDIT_ARTICLES.newbie[1],
  AUDIT_ARTICLES.club[0],
  AUDIT_ARTICLES.news[1],
  AUDIT_ARTICLES.help[0]
]
