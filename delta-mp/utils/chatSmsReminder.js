export const USER_CHAT_SMS_REMINDERS = [
  { code: 'CS_MESSAGE_REMINDER', label: '消息提醒' }
]

export const PLAYER_CHAT_SMS_REMINDERS = [
  { code: 'CS_MESSAGE_REMINDER', label: '消息提醒' },
  { code: 'PLAYER_FINISH_ORDER', label: '通知老板结单' }
]

export const CS_CHAT_SMS_REMINDERS = [
  { code: 'CS_MESSAGE_REMINDER', label: '消息提醒' }
]

export function chooseChatSmsReminder(options) {
  return new Promise((resolve, reject) => {
    if (!Array.isArray(options) || options.length === 0) {
      reject(new Error('暂无可用提醒方式'))
      return
    }

    uni.showActionSheet({
      itemList: options.map(item => item.label),
      success(res) {
        resolve(options[res.tapIndex])
      },
      fail(err) {
        reject(err)
      }
    })
  })
}
