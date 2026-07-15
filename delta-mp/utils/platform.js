export function isH5() {
  let result = false
  // #ifdef H5
  result = true
  // #endif
  return result
}

export function isMpWeixin() {
  let result = false
  // #ifdef MP-WEIXIN
  result = true
  // #endif
  return result
}
