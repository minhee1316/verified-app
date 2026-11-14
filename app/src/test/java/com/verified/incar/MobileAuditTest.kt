package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import com.verified.incar.core.CarModel
import com.verified.incar.core.Consent
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.Module
import com.verified.incar.core.Persona
import com.verified.incar.core.Place
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 폰 점검: 여러 폰 조건(작은 폭·다크·큰 글씨·가로)에서 모든 화면과 주요 상태를 그려
 * build/mobile_audit/<조건>/ 에 PNG를, report.txt 에 자동 검사 결과(화면 밖·글자 잘림·쪼개짐·작은 터치)를 남긴다.
 * 판정 실패로 테스트를 깨지 않는다(점검 자료용).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MobileAuditTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: VerifiedRuntime
    private val issues = mutableListOf<String>()
    /** 화면, 실제 글자 크기(dp, mdpi라 px), 굵기, 글자: 폰 글자 크기 기준과 비교용 */
    private val sizes = mutableListOf<String>()

    @Config(qualifiers = "w360dp-h2400dp-port-mdpi") @Test fun small360() = audit("p360")
    @Config(qualifiers = "w411dp-h2400dp-port-mdpi") @Test fun phone411() = audit("p411")
    @Config(qualifiers = "w411dp-h2400dp-port-night-mdpi") @Test fun phone411Dark() = audit("p411dark")
    @Config(qualifiers = "w360dp-h2400dp-port-mdpi") @Test fun small360BigFont() { RuntimeEnvironment.setFontScale(1.3f); audit("p360font130") }
    @Config(qualifiers = "w800dp-h360dp-land-mdpi") @Test fun landscape800() = audit("l800")
    // 폴드 접은 화면처럼 가장 좁은 폰, 그리고 큰 글씨(접근성 150%)
    @Config(qualifiers = "w320dp-h2400dp-port-mdpi") @Test fun fold320() = audit("p320")
    @Config(qualifiers = "w360dp-h2400dp-port-mdpi") @Test fun small360Font150() { RuntimeEnvironment.setFontScale(1.5f); audit("p360font150") }

    private fun start(personas: Set<Persona> = Persona.entries.toSet()) {
        vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        vm.settings { it.copy(personas = personas, flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
    }

    private val roots = setOf("HOME", "CONTROL", "HISTORY", "MYPAGE")

    private fun tab(id: String) {
        if (id in roots) rule.onNodeWithTag("nav_$id").performClick()
        else {
            rule.onNodeWithTag("nav_HOME").performClick(); rule.waitForIdle()
            rule.onNodeWithTag("nav_MYPAGE").performClick(); rule.waitForIdle()
            // 기능 10개는 더보기에서 "상황별 기능" 한 줄로 합쳐져 있어 화면 경로로 바로 연다
            if (rule.onAllNodesWithTag("tab_$id").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("tab_$id").performScrollTo().performClick()
            else vm.navigate.value = id
        }
        rule.waitForIdle()
    }

    private fun capture(dir: File, name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        inspect(name)
    }

    /** 의미 트리를 훑어 폰에서 깨지는 흔한 모양을 찾는다. mdpi라 px = dp. */
    private fun inspect(screen: String) {
        val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val w = root.size.width
        fun label(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
            ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("") ?: n.config.getOrNull(SemanticsProperties.TestTag) ?: "?").replace("\n", "⏎").take(40)
        fun walk(n: SemanticsNode, inHScroll: Boolean) {
            val hs = inHScroll || n.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
            val b = n.boundsInRoot
            if (!hs && b.width > 0 && (b.right > w + 2 || b.left < -2)) issues += "$screen | 화면 밖 | ${label(n)} | x ${b.left.toInt()}~${b.right.toInt()} / 폭 $w"
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { act ->
                val l = mutableListOf<TextLayoutResult>()
                if (act(l)) l.firstOrNull()?.let { r ->
                    val len = r.layoutInput.text.length
                    val st = r.layoutInput.style
                    if (st.fontSize.isSp) sizes += screen + "	" + "%.1f".format(java.util.Locale.ROOT, st.fontSize.value * r.layoutInput.density.fontScale * r.layoutInput.density.density) + "	" + (st.fontWeight?.weight ?: 400) + "	" + label(n)
                    if (r.lineCount > 0 && r.isLineEllipsized(r.lineCount - 1)) issues += "$screen | 말줄임(…) | ${label(n)}"
                    // 측정 폭은 정수 픽셀, 글리프 위치는 실수다. 실제 줄이 오른쪽이나 왼쪽으로 벗어나는지 검사한다.
                    if ((0 until r.lineCount).any { line -> r.getLineRight(line) > r.size.width + 1f || r.getLineLeft(line) < -1f })
                        issues += "$screen | 글자 폭 넘침 | ${label(n)}"
                    if (r.lineCount >= 3 && len.toFloat() / r.lineCount < 4f) issues += "$screen | 글자 쪼개짐(${r.lineCount}줄·한 줄 ${len / r.lineCount}자) | ${label(n)}"
                }
            }
            if (n.config.contains(SemanticsActions.OnClick) && b.width > 0 && (b.width < 44f || b.height < 44f))
                issues += "$screen | 작은 터치(${b.width.toInt()}×${b.height.toInt()}dp) | ${label(n)}"
            n.children.forEach { walk(it, hs) }
        }
        walk(root, false)
    }

    private fun audit(tag: String) {
        val dir = File("build/mobile_audit/$tag").apply { mkdirs() }
        // 처음 화면(생활 유형 고르기)
        start(emptySet())
        capture(dir, "00_ONBOARD")
        vm.settings { it.copy(personas = Persona.entries.toSet()) }
        rule.waitForIdle()
        val ids = roots.toList() + listOf("CARLIFE", "WALLET", "SERVICE", "INSIGHT", "KEY", "NOTICE", "SETTINGS", "HISTORY") + Module.entries.map { it.name }
        ids.forEachIndexed { i, id ->
            runCatching { tab(id) }.onFailure { issues += "$id | 열기 실패 | ${it.message?.take(60)}" }
            capture(dir, "%02d_%s".format(i + 1, id))
        }
        // 주요 상태
        runCatching {
            rule.onNodeWithTag("nav_HOME").performClick(); rule.waitForIdle()
            rule.onNodeWithTag("home_search", useUnmergedTree = true).performScrollTo().performClick(); rule.waitForIdle()
            rule.onNodeWithTag("search_box").performTextInput("와이퍼")
            capture(dir, "40_SEARCH")
        }.onFailure { issues += "SEARCH | 열기 실패 | ${it.message?.take(60)}" }
        runCatching {
            vm.act { _, _ -> startValet(); emptyList() }
            tab(Module.VALET.name)
            capture(dir, "41_VALET_CONSENT")
            vm.act { s, now -> confirmValetConsent(s, now) }
            vm.sim { it.copy(gear = Gear.D, speedKmh = 35f, place = Place.OTHER) }
            vm.sim { it.copy(odometerKm = it.odometerKm + 4.8, socPct = it.socPct - 1) }
            vm.sim { it.copy(gear = Gear.D, speedKmh = 30f) }
            rule.waitForIdle()
            capture(dir, "42_DRIVING_RESTRICTED")
            vm.sim { it.copy(place = Place.HOME, speedKmh = 10f) }
            vm.sim { it.copy(gear = Gear.P, speedKmh = 0f) }
            vm.sim { it.copy(driverSeated = false, driverDoorOpen = true) }
            vm.skipSeconds(11)
            rule.waitForIdle()
            capture(dir, "43_VALET_DONE")
            tab("NOTICE")
            capture(dir, "44_NOTICE_LIST")
        }.onFailure { issues += "VALET | 상태 실패 | ${it.message?.take(60)}" }
        runCatching {
            tab("CONTROL")
            rule.onAllNodesWithText("시뮬레이터", substring = true).onFirst().performClick()
            capture(dir, "45_SIM_PANEL")
        }.onFailure { issues += "SIM | 열기 실패 | ${it.message?.take(60)}" }
        File(dir, "report.txt").writeText(issues.joinToString("\n"))
        File(dir, "sizes.tsv").writeText(sizes.joinToString("\n"))
    }
}
