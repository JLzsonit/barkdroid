// 生成 barkdroid 内置通知铃声（16-bit 单声道 PCM WAV）
// 每段 = 若干 (频率Hz, 时长ms, 可选滑音目标频率) 片段，8ms 起音 / 45ms 收音防爆音
import fs from 'node:fs';
import path from 'node:path';

const SR = 44100;

function tone(freq, ms, glideTo = null) {
  const n = Math.max(1, Math.round((ms * SR) / 1000));
  const out = new Float64Array(n);
  const attack = Math.max(1, Math.round(0.008 * SR));
  const release = Math.max(1, Math.round(0.045 * SR));
  let phase = 0;
  for (let i = 0; i < n; i++) {
    const f0 = freq;
    const f1 = glideTo ?? freq;
    const f = f0 + (f1 - f0) * (i / n);
    phase += (2 * Math.PI * f) / SR;
    let env = 1;
    if (i < attack) env = i / attack;
    const rem = n - i;
    if (rem < release) env = Math.min(env, rem / release);
    out[i] = Math.max(-1, Math.min(1, Math.sin(phase) * 0.62 * env));
  }
  return out;
}

const silence = (ms) => new Float64Array(Math.max(1, Math.round((ms * SR) / 1000)));

function concat(parts) {
  const total = parts.reduce((s, p) => s + p.length, 0);
  const out = new Float64Array(total);
  let off = 0;
  for (const p of parts) {
    out.set(p, off);
    off += p.length;
  }
  return out;
}

function wav(samples) {
  const dataSize = samples.length * 2;
  const buf = Buffer.alloc(44 + dataSize);
  buf.write('RIFF', 0);
  buf.writeUInt32LE(36 + dataSize, 4);
  buf.write('WAVE', 8);
  buf.write('fmt ', 12);
  buf.writeUInt32LE(16, 16);        // fmt 块大小
  buf.writeUInt16LE(1, 20);         // PCM
  buf.writeUInt16LE(1, 22);         // 单声道
  buf.writeUInt32LE(SR, 24);
  buf.writeUInt32LE(SR * 2, 28);    // 字节率
  buf.writeUInt16LE(2, 32);         // 块对齐
  buf.writeUInt16LE(16, 34);        // 位深
  buf.write('data', 36);
  buf.writeUInt32LE(dataSize, 40);
  for (let i = 0; i < samples.length; i++) {
    buf.writeInt16LE(Math.round(samples[i] * 32767), 44 + i * 2);
  }
  return buf;
}

// 急促提示：1250 ↔ 1600Hz 来回滑 4 轮
function siren() {
  const parts = [];
  for (let k = 0; k < 4; k++) {
    parts.push(tone(1250, 110, 1600));
    parts.push(tone(1600, 110, 1250));
  }
  return concat(parts);
}

const tones = {
  // 清脆单响
  tone_beep: concat([tone(880, 220)]),
  // 轻快双响
  tone_double: concat([tone(880, 160), silence(90), tone(880, 160)]),
  // 三连短音
  tone_triple: concat([tone(660, 110), silence(70), tone(880, 110), silence(70), tone(1100, 130)]),
  // 门铃叮咚
  tone_chime: concat([tone(987.77, 260), silence(60), tone(783.99, 420)]),
  // 急促提示
  tone_siren: siren(),
  // 清亮鸟鸣（两声下滑啁啾）
  tone_bird: concat([tone(2000, 130, 1300), silence(90), tone(1700, 150, 1100)]),
};

const outDir = process.argv[2];
fs.mkdirSync(outDir, { recursive: true });
for (const [name, samples] of Object.entries(tones)) {
  const file = path.join(outDir, `${name}.wav`);
  fs.writeFileSync(file, wav(samples));
  console.log(`${file}  ${(samples.length / SR).toFixed(2)}s  ${fs.statSync(file).size}B`);
}
