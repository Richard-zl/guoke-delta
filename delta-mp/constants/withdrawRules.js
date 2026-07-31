/**
 * 打手提现规则（与后台 sys_config、WithdrawController 保持一致，前端写死）
 *
 * sys_config:
 * - withdraw.min_amount = 20
 * - withdraw.max_daily_count = 1
 *
 * 业务流程：申请后状态 PENDING，客服/管理员人工审核打款后 COMPLETED，拒绝则 REJECTED 并退回余额
 */

/** 最低提现金额（元），对应 withdraw.min_amount */
export const WITHDRAW_MIN_AMOUNT = 20

/** 每日提现次数上限，对应 withdraw.max_daily_count */
export const WITHDRAW_MAX_DAILY_COUNT = 1

/** 支持的提现账户类型 */
export const WITHDRAW_ACCOUNT_TYPES = ['支付宝', '微信', '银行卡']

/** 提现规则说明（展示在提现页） */
export const WITHDRAW_RULE_ITEMS = [
  {
    label: '可提现额度',
    text: `当前账户「可提现金额」为全部可用余额；单次提现不得低于 ¥${WITHDRAW_MIN_AMOUNT}，且不得超过页面展示的可提现金额。`
  },
  {
    label: '每日提现次数',
    text: `每天最多可申请提现 ${WITHDRAW_MAX_DAILY_COUNT} 次（按自然日 0:00–24:00 计算）。`
  },
  {
    label: '最低提现金额',
    text: `单次最低提现 ¥${WITHDRAW_MIN_AMOUNT}。`
  },
  {
    label: '提现账户',
    text: `须先绑定 ${WITHDRAW_ACCOUNT_TYPES.join('、')} 收款账户，且账户姓名与实名信息一致。`
  },
  {
    label: '提现时间',
    text: '每周二 12:00–周三 12:00、周六 12:00–周日 12:00 可提交（以服务端校验为准）。'
  },
  {
    label: '到账时间',
    text: '提交后进入「审核中」，审核通过并人工打款成功后状态变为「已到账」；通常审核通过后 1–3 个工作日内到账，具体以实际到账为准。'
  },
  {
    label: '其他说明',
    text: '账号被冻结期间无法提现；申请提交后对应金额将冻结，审核拒绝后自动退回可提现余额；请勿频繁重复提交。'
  }
]
