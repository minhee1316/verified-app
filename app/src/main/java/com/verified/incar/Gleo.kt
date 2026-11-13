package com.verified.incar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.verified.incar.core.Module
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gleo AI 연동 (Pleos 문서 App Schema / Callback Schema 기준).
 * 함수는 assets/schema.json 에 최대 3개 등록하고, 결과는 APP_ACTION_RESPONSE 브로드캐스트로 돌려준다.
 */
object Gleo {
    const val START_VALET = "START_VALET_MODE"
    const val START_ESCORT = "START_ESCORT"
    const val CHECK_ODDITY = "CHECK_VEHICLE_ODDITY"
    private const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    const val PARAM = "ACTION_PARAM"
    /** 음성 인식 결과보다 긴 입력은 버린다(다른 앱이 큰 값을 넣는 것 방지). */
    private const val MAX_PARAM = 500

    /** Callback Schema. message는 규정상 완전한 영어 문장이어야 한다. */
    data class Reply(val function: String, val status: String, val message: String, val data: JSONArray = JSONArray(), val openTab: String? = null) {
        fun json(): String = JSONObject()
            .put("functionName", function).put("status", status).put("message", message).put("data", data.toString())
            .toString()
    }

    /** shortcut: 답은 지정 수신자 없이 보낸다. Gleo 패키지 이름이 공개되면 setPackage로 Gleo에만 보내도록 바꾼다 [확인 필요]. */
    fun reply(ctx: Context, r: Reply) {
        ctx.sendBroadcast(Intent(RESPONSE_ACTION).putExtra("packageName", ctx.packageName).putExtra("response", r.json()))
    }

    /** 음성 함수 하나를 실행하고 Gleo에 돌려줄 답을 만든다. */
    fun handle(rt: VerifiedRuntime, function: String, param: String?): Reply {
        val u = rt.ui.value
        return when (function) {
            START_VALET -> if (u.signals.uxRestricted) {
                Reply(function, "fail", "Designated driver mode can be started only while the car is in park.")
            } else if (u.engine.valet.phase == com.verified.incar.core.ValetPhase.COMPLETE) {
                // 차주가 아직 끝내지 않은 완료 결과를 음성으로 지우지 않는다
                Reply(function, "fail", "The last designated driver result is still open. Please end it on the screen first.", openTab = Module.VALET.name)
            } else if (u.engine.valet.phase in IN_PROGRESS) {
                // 진행 중인 판정(특히 '미완료' 경고)이나 차주가 아직 보지 않은 완료 결과를 음성 요청으로 지우지 않는다
                Reply(function, "success", "Designated driver mode is already running.", openTab = Module.VALET.name)
            } else if (u.carConnected && com.verified.incar.core.Place.HOME !in u.settings.pins) {
                Reply(function, "fail", "Please register your home location in settings first.")
            } else {
                rt.act { _, _ -> startValet() }
                Reply(function, "success", "Designated driver mode is ready. Please ask the driver to agree on the screen.", openTab = Module.VALET.name)
            }
            START_ESCORT -> if (u.signals.uxRestricted) {
                Reply(function, "fail", "Escort mode can be started only while the car is in park.")
            } else if (u.engine.escort.phase != com.verified.incar.core.EscortPhase.OFF) {
                Reply(function, "success", "Escort mode is already running.", openTab = Module.ESCORT.name)
            } else {
                rt.act { _, now -> startEscort(now); emptyList() }
                Reply(function, "success", "Escort mode started. I will check the rear door, seat and seat belt.", openTab = Module.ESCORT.name)
            }
            CHECK_ODDITY -> {
                val all = u.diag.filter { it.code != "R99" }
                // 사용자가 말한 증상과 맞는 설정만 답한다. 증상이 어느 낱말에도 맞지 않을 때만 읽을 수 있는 설정 전부
                val symptom = param?.take(MAX_PARAM)?.let { runCatching { JSONObject(it).optString("symptom") }.getOrNull() }.orEmpty().lowercase()
                val wanted = SYMPTOM.filterKeys { k -> k in symptom }.values.toSet()
                val found = if (wanted.isEmpty()) all else all.filter { it.code in wanted }
                val data = JSONArray(found.map { JSONObject().put("code", it.code).put("title", it.title) })
                val msg = if (found.isEmpty()) {
                    "I found no related vehicle setting. I cannot read warning lights or fault codes, so please visit a Bluehands service center if it continues."
                } else {
                    "This may be related to ${found.size} vehicle setting${if (found.size > 1) "s" else ""}: ${found.joinToString(", ") { CODE_EN[it.code] ?: it.code }}. " +
                        "I cannot read warning lights or fault codes."
                }
                Reply(function, "success", msg, data, openTab = Module.DIAG.name)
            }
            else -> Reply(function, "fail", "This function is not supported by this app.")
        }
    }

    private val IN_PROGRESS = setOf(
        com.verified.incar.core.ValetPhase.CONSENT, com.verified.incar.core.ValetPhase.DRIVING,
        com.verified.incar.core.ValetPhase.PRELIM, com.verified.incar.core.ValetPhase.INCOMPLETE, com.verified.incar.core.ValetPhase.COMPLETE,
    )

    private val SYMPTOM = mapOf(
        "크루즈" to "R01", "cruise" to "R01", "와이퍼" to "R02", "wiper" to "R02", "김" to "R03", "fog" to "R03", "습기" to "R03",
        // 한 글자 '문'은 '문제'에, 'light'는 'headlight'에도 걸려 좁힌다
        "조명" to "R04", "실내등" to "R04", "ambient" to "R04", "interior light" to "R04",
        "문이" to "R05", "문을" to "R05", "문 열" to "R05", "도어" to "R05", "door" to "R05", "트렁크" to "R05", "창문" to "R05",
        "워셔" to "R06", "washer" to "R06", "벨트" to "R07", "경고음" to "R07", "belt" to "R07",
    )

    private val CODE_EN = mapOf(
        "R01" to "cruise control was released", "R02" to "the wiper is in AUTO mode", "R03" to "only air recirculation is on",
        "R04" to "the ambient light follows the drive mode", "R05" to "a door, the trunk or a window is open",
        "R06" to "washer fluid is low", "R07" to "a seat belt is unfastened",
    )
}

/** component = service 함수(화면 없이 처리)를 받는 수신기. */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // 수신기는 화면 없는 함수(고장 확인)만 받는다. 다른 앱이 이 수신기를 직접 지정해 대리·동행을 시작하지 못하게 한다
        if (action != Gleo.CHECK_ODDITY) return
        // 다른 앱이 차 상태를 연달아 캐묻지 못하게 1초에 한 번만 답한다
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastMs < 1_000) return
        lastMs = now
        val rt = (context.applicationContext as VerifiedApp).runtime
        Gleo.reply(context, Gleo.handle(rt, action, intent.getStringExtra(Gleo.PARAM)))
    }

    private companion object {
        var lastMs = 0L
    }
}
