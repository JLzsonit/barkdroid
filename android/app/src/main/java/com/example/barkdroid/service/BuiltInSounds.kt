package com.example.barkdroid.service

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.example.barkdroid.R

/**
 * 内置通知铃声。
 *
 * 音源是构建期合成的 20 段短提示音，放在 res/raw/（tone_*.wav），
 * 引用走 android.resource:// 协议 —— 不落缓存、不怕系统清理，也没有 FileUri 暴露问题。
 */
object BuiltInSounds {

    data class Preset(val id: String, val name: String)

    val PRESETS = listOf(
        Preset("default", "系统默认"),
        Preset("beep", "清脆单响"),
        Preset("double", "轻快双响"),
        Preset("triple", "三连短音"),
        Preset("chime", "门铃叮咚"),
        Preset("siren", "急促提示"),
        Preset("bird", "清亮鸟鸣"),
        Preset("drop", "水滴轻响"),
        Preset("glass", "玻璃清音"),
        Preset("marimba", "木琴上扬"),
        Preset("pulse", "沉稳三连"),
        Preset("bell_low", "低沉钟声"),
        Preset("lingering", "悠长余韵"),
        Preset("chord", "明亮和弦"),
        Preset("ladder", "阶梯上行"),
        Preset("cascade", "逐级下落"),
        Preset("quad", "四连短音"),
        Preset("code", "短促电码"),
        Preset("wood", "木头轻叩"),
        Preset("horn", "汽笛长鸣"),
        Preset("echo", "回声轻响"),
    )

    /** id → raw 资源 */
    private val RES = mapOf(
        "beep" to R.raw.tone_beep,
        "double" to R.raw.tone_double,
        "triple" to R.raw.tone_triple,
        "chime" to R.raw.tone_chime,
        "siren" to R.raw.tone_siren,
        "bird" to R.raw.tone_bird,
        "drop" to R.raw.tone_drop,
        "glass" to R.raw.tone_glass,
        "marimba" to R.raw.tone_marimba,
        "pulse" to R.raw.tone_pulse,
        "bell_low" to R.raw.tone_bell_low,
        "lingering" to R.raw.tone_lingering,
        "chord" to R.raw.tone_chord,
        "ladder" to R.raw.tone_ladder,
        "cascade" to R.raw.tone_cascade,
        "quad" to R.raw.tone_quad,
        "code" to R.raw.tone_code,
        "wood" to R.raw.tone_wood,
        "horn" to R.raw.tone_horn,
        "echo" to R.raw.tone_echo,
    )

    fun byId(id: String): Preset? = PRESETS.firstOrNull { it.id == id }

    fun nameOf(id: String): String = byId(id)?.name ?: "系统默认"

    /** 铃声 Uri；未知 id 一律回落到系统默认，避免渠道静音 */
    fun uriFor(ctx: Context, id: String): Uri {
        val resId = RES[id] ?: return systemDefault()
        return Uri.parse("android.resource://${ctx.packageName}/$resId")
    }

    fun systemDefault(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
}
