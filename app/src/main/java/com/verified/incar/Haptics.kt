package com.verified.incar

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * 원격 시동(미리 공조)이 켜졌을 때의 진동. 엔진음 대신 조용한 촉각으로 알린다.
 * 내연기관은 시동 걸림 뒤 공회전 떨림, 전기차는 한 번 부드럽게 차오르는 느낌 [제안값].
 * 진동 장치가 없는 기기(대부분의 차량 화면)에서는 아무것도 하지 않는다.
 */
object StartHaptic {
    /** (켜는 시간 ms, 세기 0~255) 쌍. 첫 칸은 0ms 대기. */
    fun pattern(ev: Boolean): List<Pair<Long, Int>> =
        if (ev) listOf(0L to 0, 60L to 40, 60L to 90, 80L to 130, 60L to 70, 60L to 25)
        else listOf(0L to 0, 90L to 220, 50L to 0, 60L to 150, 40L to 0, 60L to 120, 40L to 0, 60L to 100, 40L to 0, 60L to 90)

    fun play(context: Context, ev: Boolean) {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        val p = pattern(ev)
        v.vibrate(
            if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(p.map { it.first }.toLongArray(), p.map { it.second }.toIntArray(), -1)
            else VibrationEffect.createOneShot(if (ev) 120 else 200, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }
}
