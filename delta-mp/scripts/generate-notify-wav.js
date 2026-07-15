/**
 * 生成与 PC 端一致的三音阶提示音 (C5→E5→G5)，输出到 static/sounds/notify.wav
 * 与 delta-admin-ui src/stores/chat.js 中 playNotificationSound() 逻辑一致
 */
const fs = require('fs')
const path = require('path')

const SAMPLE_RATE = 44100
const NOTES = [
  { freq: 523, start: 0, dur: 0.12 },      // C5
  { freq: 659, start: 0.13, dur: 0.12 },  // E5
  { freq: 784, start: 0.26, dur: 0.18 }   // G5
]
const DURATION = 0.49 // 最后音符结束时间 + 一点尾

function gainEnvelope(t, start, dur) {
  if (t < start) return 0
  if (t < start + 0.02) return 0.18 * (t - start) / 0.02
  if (t < start + dur) {
    const decay = (t - start - 0.02) / (dur - 0.02)
    return 0.18 * Math.pow(0.001 / 0.18, decay)
  }
  return 0
}

const numSamples = Math.ceil(DURATION * SAMPLE_RATE)
const samples = new Int16Array(numSamples)

for (let i = 0; i < numSamples; i++) {
  const t = i / SAMPLE_RATE
  let v = 0
  for (const { freq, start, dur } of NOTES) {
    const g = gainEnvelope(t, start, dur)
    if (g > 0) v += Math.sin(2 * Math.PI * freq * t) * g
  }
  v = Math.max(-1, Math.min(1, v))
  samples[i] = Math.round(v * 32767)
}

// WAV: 44-byte header + PCM
const dataLen = samples.length * 2
const buf = Buffer.alloc(44 + dataLen)
let off = 0
function write(str) { buf.write(str, off); off += str.length }
function writeU32(n) { buf.writeUInt32LE(n, off); off += 4 }
function writeU16(n) { buf.writeUInt16LE(n, off); off += 2 }

write('RIFF')
writeU32(36 + dataLen)
write('WAVE')
write('fmt ')
writeU32(16)
writeU16(1)   // PCM
writeU16(1)   // mono
writeU32(SAMPLE_RATE)
writeU32(SAMPLE_RATE * 2)
writeU16(2)
writeU16(16)
write('data')
writeU32(dataLen)
for (let i = 0; i < samples.length; i++) buf.writeInt16LE(samples[i], off + i * 2)

const outDir = path.join(__dirname, '../static/sounds')
if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true })
fs.writeFileSync(path.join(outDir, 'notify.wav'), buf)
console.log('Generated static/sounds/notify.wav (PC-style three-note chime)')
