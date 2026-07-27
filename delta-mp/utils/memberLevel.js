/**
 * 会员等级展示：档位、权益文案、卡片主题色（深底浅字保证对比度）
 */

export const MEMBER_LEVELS = [
  { code: 'BRONZE', name: '青铜伴星', min: 0, max: 9999, desc: '暂无会员折扣', benefit: '暂无会员折扣' },
  { code: 'SILVER', name: '白银伴星', min: 10000, max: 34999, desc: '永久9.8折（任务及定制单除外）', benefit: '永久 9.8 折 · 任务定制除外' },
  { code: 'GOLD', name: '黄金伴星', min: 35000, max: 104999, desc: '永久9.6折（任务及定制单除外）', benefit: '永久 9.6 折 · 任务定制除外' },
  { code: 'PLATINUM', name: '铂金伴星', min: 105000, max: 244999, desc: '永久9.4折（任务及定制单除外）', benefit: '永久 9.4 折 · 任务定制除外' },
  { code: 'DIAMOND', name: '钻石伴星', min: 245000, max: 559999, desc: '永久9.2折（任务及定制单除外）', benefit: '永久 9.2 折 · 任务定制除外' },
  { code: 'KING', name: '王者伴星', min: 560000, max: Number.MAX_SAFE_INTEGER, desc: '永久9.0折（任务及定制单除外）', benefit: '永久 9.0 折 · 任务定制除外' }
]

/** 各等级会员卡主题（深色底 + 浅色字） */
export const MEMBER_THEMES = {
  BRONZE: {
    cardBg: 'linear-gradient(135deg, #2a2118 0%, #4a3424 50%, #8b6914 100%)',
    text: '#f5e6c8',
    title: '#e8c48a',
    bar: '#e8c48a',
    orb: 'rgba(205, 164, 94, 0.18)'
  },
  SILVER: {
    cardBg: 'linear-gradient(135deg, #1e293b 0%, #334155 45%, #94a3b8 100%)',
    text: '#f1f5f9',
    title: '#ffffff',
    bar: '#e2e8f0',
    orb: 'rgba(226, 232, 240, 0.16)'
  },
  GOLD: {
    cardBg: 'linear-gradient(135deg, #1a1a2e 0%, #3d2b1f 45%, #c9a227 100%)',
    text: '#fff8e7',
    title: '#f5e6a8',
    bar: '#f5e6a8',
    orb: 'rgba(245, 230, 168, 0.15)'
  },
  PLATINUM: {
    cardBg: 'linear-gradient(135deg, #0c1a2e 0%, #1e3a5f 50%, #5b8def 100%)',
    text: '#e8f1ff',
    title: '#bfdbfe',
    bar: '#93c5fd',
    orb: 'rgba(147, 197, 253, 0.18)'
  },
  DIAMOND: {
    cardBg: 'linear-gradient(135deg, #1a1030 0%, #312e81 50%, #7c3aed 100%)',
    text: '#f3e8ff',
    title: '#e9d5ff',
    bar: '#d8b4fe',
    orb: 'rgba(216, 180, 254, 0.18)'
  },
  KING: {
    cardBg: 'linear-gradient(135deg, #1a0508 0%, #4a0e18 40%, #b45309 100%)',
    text: '#fff7ed',
    title: '#fde68a',
    bar: '#fbbf24',
    orb: 'rgba(251, 191, 36, 0.16)'
  }
}

export function resolveMemberLevel(totalPoints, levelCode) {
  const points = Number(totalPoints) || 0
  const byPoints = MEMBER_LEVELS.find((l) => points >= l.min && points <= l.max)
  if (byPoints) return byPoints
  return MEMBER_LEVELS.find((l) => l.code === levelCode) || MEMBER_LEVELS[0]
}

export function getMemberTheme(levelCode) {
  return MEMBER_THEMES[levelCode] || MEMBER_THEMES.BRONZE
}

/** 升级进度：percent 0-100，nextName / remainPoints */
export function getMemberProgress(totalPoints) {
  const points = Number(totalPoints) || 0
  let idx = 0
  for (let i = 0; i < MEMBER_LEVELS.length; i++) {
    if (points >= MEMBER_LEVELS[i].min && points <= MEMBER_LEVELS[i].max) {
      idx = i
      break
    }
  }
  if (idx >= MEMBER_LEVELS.length - 1) {
    return { percent: 100, nextName: '', remainPoints: 0, isMax: true }
  }
  const cur = MEMBER_LEVELS[idx]
  const next = MEMBER_LEVELS[idx + 1]
  const span = next.min - cur.min
  const done = points - cur.min
  const percent = Math.min(100, Math.max(0, Math.floor((done / span) * 100)))
  return {
    percent,
    nextName: next.name,
    remainPoints: Math.max(0, next.min - points),
    isMax: false
  }
}

export function formatPoints(n) {
  return Number(n || 0).toLocaleString()
}
