// 生成 barkdroid 内置通知铃声（16-bit 单声道 PCM WAV）
// 每段 = 若干 (频率Hz, 时长ms, 可选滑音目标频率, 音量系数, 可选衰减时间常数ms) 片段，
// 8ms 起音 / 45ms 收音防爆音
import fs from 'node:fs';
import path from 'node:path';

const SR = 44100;

function tone(freq, ms, glideTo = null, gain = 0.62, decayMs = null) {
  const n = Math.max(1, Math.round((ms * SR) / 1000));
  const out = new Float64Array(n);
  const attack = Math.max(1, Math.round(0.008 * SR));
  const release = Math.max(1, Math.round(0.045 * SR));
  // 指数衰减的时间常数（样本数）；null = 保持恒定音量（原来所有铃声的行为）
  const tau = decayMs == null ? null : Math.max(1, (decayMs * SR) / 1000);
  let phase = 0;
  for (let i = 0; i < n; i++) {
    const f0 = freq;
    const f1 = glideTo ?? freq;
    const f = f0 + (f1 - f0) * (i / n);
    phase += (2 * Math.PI * f) / SR;
    let env = 1;
    if (i < attack) env = i / attack;
    if (tau != null) env *= Math.exp(-i / tau);
    const rem = n - i;
    if (rem < release) env = Math.min(env, rem / release);
    out[i] = Math.max(-1, Math.min(1, Math.sin(phase) * gain * env));
  }
  return out;
}

/** 多层叠加（同一时间轴上的和弦 / 泛音），长度取最长层，总和限幅到 ±1 */
function mix(layers) {
  const n = Math.max(...layers.map((l) => l.length));
  const out = new Float64Array(n);
  for (const l of layers) {
    for (let i = 0; i < l.length; i++) out[i] += l[i];
  }
  for (let i = 0; i < n; i++) out[i] = Math.max(-1, Math.min(1, out[i]));
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
  // 水滴轻响（快速上滑后回落，像一滴水落进水面）
  tone_drop: concat([tone(620, 80, 1560), tone(1560, 120, 1180)]),
  // 木琴上扬（E5 → A5 → C#6 三音上行，叠八度泛音出木质明亮感）
  tone_marimba: concat([
    mix([tone(659.25, 150, null, 0.5), tone(1318.5, 150, null, 0.16)]),
    silence(45),
    mix([tone(880, 150, null, 0.5), tone(1760, 150, null, 0.16)]),
    silence(45),
    mix([tone(1108.73, 280, null, 0.5), tone(2217.46, 280, null, 0.16)]),
  ]),
  // 沉稳三连（低音 G4 三声，适合安静场合）
  tone_pulse: concat([
    tone(392, 90, null, 0.7),
    silence(60),
    tone(392, 90, null, 0.7),
    silence(60),
    tone(392, 120, null, 0.7),
  ]),
  // 玻璃清音（C7 + G7 叠成带金属光泽的「叮」）
  tone_glass: mix([tone(2093, 300, null, 0.42), tone(3136, 300, null, 0.2)]),

  /* ---- 第 3 批：带包络的旋律/音色型提示音 ---- */
  // 低沉钟声（C4 + G4 + C5 谐波列，指数衰减出钟体共鸣）
  tone_bell_low: mix([
    tone(261.63, 620, null, 0.62, 260),
    tone(392.0, 620, null, 0.26, 220),
    tone(523.25, 620, null, 0.14, 180),
  ]),
  // 悠长余韵（C6 长音，慢衰减）
  tone_lingering: mix([tone(1046.5, 700, null, 0.6, 420), tone(2093, 700, null, 0.15, 300)]),
  // 明亮和弦（C6 + E6 + G6 同时响，大三和弦）
  tone_chord: mix([
    tone(1046.5, 430, null, 0.34, 300),
    tone(1318.5, 430, null, 0.3, 300),
    tone(1568.0, 430, null, 0.26, 300),
  ]),
  // 阶梯上行（五声音阶五连）
  tone_ladder: concat([
    tone(523.25, 90, null, 0.5), silence(40),
    tone(587.33, 90, null, 0.5), silence(40),
    tone(659.25, 90, null, 0.5), silence(40),
    tone(783.99, 90, null, 0.5), silence(40),
    tone(880.0, 180, null, 0.5),
  ]),
  // 逐级下落（G6 → E6 → C6，每音带衰减）
  tone_cascade: concat([
    tone(1568.0, 200, null, 0.58, 120), silence(30),
    tone(1318.5, 200, null, 0.58, 120), silence(30),
    tone(1046.5, 420, null, 0.58, 220),
  ]),
  // 四连短音（快速上行四声）
  tone_quad: concat([
    tone(523.25, 60, null, 0.55), silence(45),
    tone(659.25, 60, null, 0.55), silence(45),
    tone(783.99, 60, null, 0.55), silence(45),
    tone(1046.5, 90, null, 0.55),
  ]),
  // 短促电码（短-短-长，电报式）
  tone_code: concat([
    tone(1760, 70, null, 0.55), silence(60),
    tone(1760, 70, null, 0.55), silence(60),
    tone(1760, 220, null, 0.55, 400),
  ]),
  // 木头轻叩（低音快速衰减，像敲木鱼）
  tone_wood: mix([tone(420, 110, null, 0.7, 55), tone(630, 110, null, 0.25, 40)]),
  // 汽笛长鸣（低音缓慢上滑）
  tone_horn: tone(440, 520, 494, 0.55, null),
  // 回声轻响（同音两声，第二声更轻更长）
  tone_echo: concat([
    tone(1174.66, 140, null, 0.62),
    silence(150),
    tone(1174.66, 200, null, 0.32, 160),
  ]),
};

const outDir = process.argv[2];
fs.mkdirSync(outDir, { recursive: true });
for (const [name, samples] of Object.entries(tones)) {
  const file = path.join(outDir, `${name}.wav`);
  fs.writeFileSync(file, wav(samples));
  console.log(`${file}  ${(samples.length / SR).toFixed(2)}s  ${fs.statSync(file).size}B`);
}
