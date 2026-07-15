/**
 * 表单校验工具
 */

export function isPhone(val) {
  return /^1[3-9]\d{9}$/.test(val)
}

export function isNotEmpty(val) {
  return val !== null && val !== undefined && String(val).trim() !== ''
}

export function isPrice(val) {
  return /^\d+(\.\d{1,2})?$/.test(val) && Number(val) > 0
}

export function isIdCard(val) {
  return /^\d{17}[\dXx]$/.test(val)
}

/**
 * 通用校验函数，返回第一条错误信息，全部通过返回空字符串
 * rules: [{ value, message, validator? }]
 */
export function validate(rules) {
  for (const rule of rules) {
    const { value, message, validator } = rule
    if (validator) {
      if (!validator(value)) return message
    } else {
      if (!isNotEmpty(value)) return message
    }
  }
  return ''
}
