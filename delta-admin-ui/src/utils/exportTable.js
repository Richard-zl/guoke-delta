/**
 * 导出 UTF-8 CSV（带 BOM，Excel 可直接打开中文）
 * @param {string} filename 不含扩展名
 * @param {string[]} headers 表头
 * @param {Array<Array<string|number>>} rows 数据行
 */
export function exportCsv(filename, headers, rows) {
  const lines = [headers, ...rows].map((cols) =>
    cols.map((cell) => escapeCsvCell(cell)).join(',')
  )
  const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${filename}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function escapeCsvCell(value) {
  if (value == null || value === '') return ''
  const text = String(value)
  if (/[",\n\r]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`
  }
  return text
}

/**
 * 按当前筛选分页拉取全部记录
 * @param {(params: object) => Promise<{ data: { records?: any[], total?: number } }>} fetchPage
 * @param {object} query 查询条件（会覆盖 pageNum / pageSize）
 * @param {number} pageSize
 * @param {number} maxRows 上限，避免一次性拉爆
 */
export async function fetchAllRecords(fetchPage, query, pageSize = 200, maxRows = 10000) {
  const records = []
  let pageNum = 1
  let total = Infinity
  while (records.length < maxRows && records.length < total) {
    const res = await fetchPage({ ...query, pageNum, pageSize })
    const pageRecords = res.data?.records || []
    total = Number(res.data?.total || 0)
    records.push(...pageRecords)
    if (pageRecords.length < pageSize) break
    pageNum += 1
  }
  return { records: records.slice(0, maxRows), truncated: total > maxRows }
}
