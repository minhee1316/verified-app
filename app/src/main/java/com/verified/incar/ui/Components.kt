package com.verified.incar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.core.Level

/** 앱 글꼴. Pretendard에서 앱에 쓰는 글자만 남긴 수정본(OFL, 예약 이름을 피해 이름을 바꿈). */
val VerifiedSans = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_extrabold, FontWeight.ExtraBold),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_black, FontWeight.Black),
)

/**
 * 폰에서도 제목·상태의 굵기를 유지한다. 크기만 PhoneText로 조정해 시각적 위계를 보존한다.
 */
val VerifiedSansPhone = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_extrabold, FontWeight.ExtraBold),
    androidx.compose.ui.text.font.Font(com.verified.incar.R.font.verified_sans_bold, FontWeight.Black),
)

/** 색 토큰. [dark]는 시스템 낮·밤(AAOS uiMode)을 따르고, 시연용으로 [forceDark]로 고정할 수 있다. */
object C {
    var dark by mutableStateOf(false)
    var forceDark by mutableStateOf<Boolean?>(null)
    private fun pick(day: Long, night: Long) = Color(if (dark) night else day)

    /** 채움(버튼·배지) 브랜드색 */
    val navy get() = pick(0xFF002C5F, 0xFF174C80)
    /** 글자 강조색. 밤에는 어두운 바탕에서 읽히도록 밝힌다 */
    val title get() = pick(0xFF002C5F, 0xFF9CC8FF)
    val deep = Color(0xFF001E42)
    val blue = Color(0xFF00AAD2)
    val action get() = pick(0xFF195FAB, 0xFF9BC6FF)
    /** 고른 칩 바탕 */
    val ice get() = pick(0xFFE6EEF8, 0xFF1E3354)
    val bg get() = pick(0xFFF2F3F6, 0xFF191C24)
    val card get() = pick(0xFFFFFFFF, 0xFF30333E)
    val chip get() = pick(0xFFEEF1F5, 0xFF303C4A)
    val ink get() = pick(0xFF101923, 0xFFF3F5F8)
    val sub get() = pick(0xFF606975, 0xFFB7BDC7)
    val line get() = pick(0xFFDDE0E6, 0xFF434A57)
    val surface get() = Brush.linearGradient(if (dark) listOf(Color(0xFF3C404C), Color(0xFF272A34)) else listOf(Color.White, Color(0xFFFEFEFF)))
    val backdrop get() = Brush.verticalGradient(if (dark) listOf(Color(0xFF2B2E37), Color(0xFF13161D)) else listOf(Color(0xFFF5F6F8), Color(0xFFEBEDF1)))
    /** 머리말(kicker)·보강 표시에 쓰는 청록 */
    val teal get() = pick(0xFF007FA8, 0xFF5BC0E0)
    val ok get() = pick(0xFF126647, 0xFF88DEBD)
    val mint = Color(0xFFD9F5EC)
    val warn get() = pick(0xFF854300, 0xFFFFC27E)
    val danger get() = pick(0xFFB02241, 0xFFFFA4B4)
    val violet = Color(0xFF5B5BF0)
    val night = Brush.verticalGradient(listOf(Color(0xFF101B33), Color(0xFF1C2842)))
}

fun levelColor(l: Level?) = when (l) {
    Level.OK -> C.ok
    Level.WARN -> C.warn
    Level.DANGER -> C.danger
    Level.INFO -> C.title
    null -> C.sub
}

private val cardShape = RoundedCornerShape(20.dp)

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 28.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.referenceShadow(if (C.dark) 6.dp else 12.dp)
            .clip(cardShape).background(C.surface)
            .border(1.dp, if (C.dark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.9f), cardShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            // 폰 세로에서는 카드 안 여백을 18dp까지만(차 화면 값 28~36dp는 360dp 폰에서 글 칸을 너무 좁힌다)
            .padding(if (isCompact()) minOf(padding, 18.dp) else padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(modifier, 22.dp, content = content)

/** 네이비 둥근 사각 번호 배지. */
@Composable
fun NumberBadge(no: String, color: Color = C.navy, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color), contentAlignment = Alignment.Center) {
        Text(no, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * (if (no.length > 1) 0.4f else 0.5f)).sp)
    }
}

/** 작은 대문자 청록 머리말 (예: MODULE 1 · 추가 기능). */
@Composable
fun Kicker(text: String, dark: Boolean = false) =
    Text(text.uppercase(), color = if (dark) C.blue else C.teal, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.sp)

/** 카드 머리: 머리말 · 번호 배지 + 제목 + 오른쪽 상태(알약·버튼·스위치) · 설명. */
/** 큰 글씨의 짧은 가로폰도 한 단으로 배치해 두 열 안에서 단어가 쪼개지지 않게 한다. */
@Composable
fun isCompact(): Boolean {
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return width < 600 || (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f && width < 900)
}

/** 차 디스플레이(AAOS의 UI 모드가 car)인지. 휴대폰을 가로로 돌린 넓은 화면과 구분한다. */
@Composable
fun isCarScreen() = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
    android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_CAR

/**
 * 두 단 화면 틀. 넓은 화면(차 화면·가로)에서는 좌우 두 단, 좁은 화면(폰 세로)에서는 위아래 한 단으로 쌓는다.
 * 좁은 화면에서는 바깥 여백을 16dp로 줄인다.
 */
/** 화면 바깥 좌우 여백: 넓은 화면은 디자인 값 그대로, 좁은 화면(폰 세로)은 16dp. */
@Composable
fun hPad(wide: Dp) = if (isCompact()) 16.dp else wide

/**
 * 나란히 두는 두 칸(카드 두 장, 글+그림 등). 좁은 화면(폰 세로)에서는 위아래로 쌓는다.
 * 각 칸에는 넓으면 weight(0이면 제 크기), 좁으면 전체 폭 Modifier를 넘긴다.
 */
@Composable
fun Split(
    gap: Dp, w1: Float = 1f, w2: Float = 1f, center: Boolean = false,
    first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit,
) {
    // TwoUp과 같이: 회전으로 Column↔Row가 바뀌어도 두 칸을 다시 만들지 않고 옮겨, 입력 중 메모·촬영 대상을 유지한다
    val cellState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val latestFirst = androidx.compose.runtime.rememberUpdatedState(first)
    val latestSecond = androidx.compose.runtime.rememberUpdatedState(second)
    val keptFirst = androidx.compose.runtime.remember(cellState) {
        androidx.compose.runtime.movableContentOf { m: Modifier -> cellState.SaveableStateProvider("first") { latestFirst.value(m) } }
    }
    val keptSecond = androidx.compose.runtime.remember(cellState) {
        androidx.compose.runtime.movableContentOf { m: Modifier -> cellState.SaveableStateProvider("second") { latestSecond.value(m) } }
    }
    if (isCompact()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        keptFirst(Modifier.fillMaxWidth())
        keptSecond(Modifier.fillMaxWidth())
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = if (center) Alignment.CenterVertically else Alignment.Top) {
        // 무게 0은 제 크기 그대로(그림·점수 원처럼)
        keptFirst(if (w1 > 0f) Modifier.weight(w1) else Modifier)
        keptSecond(if (w2 > 0f) Modifier.weight(w2) else Modifier)
    }
}

/** 아주 좁거나(폴드 접은 화면 등 340dp 미만) 큰 글씨(130% 이상)인 폰: 이름 옆에 버튼을 두면 이름이 쪼개진다. */
@Composable
fun isCramped() = isCompact() && (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 340 ||
    androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f)

/** 이름 + 오른쪽 버튼·알약 한 줄. 비좁은 폰([isCramped])에서는 버튼을 이름 아래 줄로 내린다. */
@Composable
fun LabelRow(label: @Composable (Modifier) -> Unit, alignBottom: Boolean = false, trailing: @Composable RowScope.() -> Unit) {
    if (isCramped()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label(Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    } else Row(Modifier.fillMaxWidth(), verticalAlignment = if (alignBottom) Alignment.Bottom else Alignment.CenterVertically) {
        label(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun TwoUp(
    hPad: Dp, vPad: Dp, gap: Dp, inner: Dp, leftWeight: Float = 1f, top: Boolean = true, rightTop: Dp = 0.dp,
    /** 한 단으로 쌓을 때 오른쪽(판정·조작)을 먼저 보여 줄지. */
    rightFirst: Boolean = false,
    /** 폰 세로에서 두 단을 위아래로 길게 쌓는 대신 위쪽 탭 두 개(왼쪽 단 이름, 오른쪽 단 이름)로 나눈다. null이면 쌓는다. */
    tabs: Pair<String, String>? = null,
    left: @Composable () -> Unit, right: @Composable () -> Unit,
) {
    // 회전은 같은 작업의 배치만 바꾼다. Row/Column 분기 사이로 내용을 옮겨도
    // 입력·선택 상태를 유지하고, 세로 탭에서 숨긴 칸은 다시 열 때 복원한다.
    val paneState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val latestLeft = androidx.compose.runtime.rememberUpdatedState(left)
    val latestRight = androidx.compose.runtime.rememberUpdatedState(right)
    val keptLeft = androidx.compose.runtime.remember(paneState) {
        androidx.compose.runtime.movableContentOf { paneState.SaveableStateProvider("left") { latestLeft.value() } }
    }
    val keptRight = androidx.compose.runtime.remember(paneState) {
        androidx.compose.runtime.movableContentOf { paneState.SaveableStateProvider("right") { latestRight.value() } }
    }
    var sel by androidx.compose.runtime.saveable.rememberSaveable(tabs) { androidx.compose.runtime.mutableIntStateOf(if (rightFirst) 1 else 0) }
    if (isCompact() && tabs != null) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(inner)) {
        // 0 = 왼쪽 단, 1 = 오른쪽 단. 처음에는 먼저 보여 주기로 한 단
        val order = if (rightFirst) listOf(1 to tabs.second, 0 to tabs.first) else listOf(0 to tabs.first, 1 to tabs.second)
        Row(Modifier.fillMaxWidth().clip(btnShape).background(C.chip).padding(3.dp)) {
            order.forEach { (i, label) ->
                val on = sel == i
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) C.card else Color.Transparent)
                        .testTag("pane_$i").selectable(selected = on, role = Role.Tab) { sel = i }
                        .heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (on) C.title else C.sub, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center) }
            }
        }
        if (sel == 0) keptLeft() else keptRight()
    } else if (isCompact()) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(inner)) {
        if (rightFirst) { keptRight(); keptLeft() } else { keptLeft(); keptRight() }
    } else Row(
        Modifier.fillMaxWidth().padding(horizontal = hPad, vertical = vPad), horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = if (top) Alignment.Top else Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(leftWeight), verticalArrangement = Arrangement.spacedBy(inner)) { keptLeft() }
        Column(Modifier.weight(1f).padding(top = rightTop), verticalArrangement = Arrangement.spacedBy(inner)) { keptRight() }
    }
}

@Composable
fun CardHeader(no: String?, title: String, sub: String? = null, kicker: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (kicker != null) Kicker(kicker)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (no != null) {
                NumberBadge(no, size = 36.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = C.ink)
            if (!isCompact()) trailing?.invoke()
        }
        // 폰 세로에서는 버튼·알약을 제목 아래에 둬서 제목이 한두 글자씩 쪼개지지 않게
        if (isCompact() && trailing != null) Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        if (sub != null) Text(sub, fontSize = 16.sp, lineHeight = 23.sp, color = C.sub)
    }
}

/** 화면 머리: 제목은 앱바에 있으므로 머리말과 설명만 둔다. */
@Composable
fun ScreenTitle(no: String, title: String, sub: String? = null, kicker: String? = null, dark: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (kicker != null) Kicker(kicker, dark)
        if (sub != null) Text(sub, color = if (dark) Color(0xFFA9B4C8) else C.sub, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

@Composable
fun Title(no: String, title: String, sub: String? = null) = ScreenTitle(no, title, sub)

@Composable
fun Pill(text: String, level: Level?, big: Boolean = false) {
    val c = levelColor(level)
    Text(
        text, color = c, fontWeight = FontWeight.Bold, fontSize = if (big) 18.sp else 15.sp,
        modifier = Modifier.clip(CircleShape).background(c.copy(alpha = 0.10f))
            .padding(horizontal = if (big) 12.dp else 9.dp, vertical = if (big) 6.dp else 4.dp),
    )
}

/** 체크 원 + 문구 (목업 ①의 4줄 체크, ②의 판정 알약). */
@Composable
fun CheckMark(ok: Boolean?, size: Dp = 32.dp) {
    val color = when (ok) { true -> C.ok; false -> C.warn; null -> C.sub }
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
        Text(when (ok) { true -> "✓"; false -> "!"; null -> "–" }, color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.55f).sp)
    }
}

@Composable
fun CheckLine(ok: Boolean?, label: String, value: String? = null, divider: Boolean = true) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckMark(ok, 26.dp)
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                if (value != null && isCompact()) Text(value, fontSize = 17.sp, color = C.sub)
            }
            if (value != null && !isCompact()) Text(value, fontSize = 17.sp, color = C.sub)
        }
        if (divider) HorizontalDivider(color = C.line)
    }
}

@Composable
fun CheckRow(ok: Boolean, label: String, value: String) = CheckLine(ok, label, value)

private val btnShape = RoundedCornerShape(16.dp)

/** SOFT_OK·SOFT_DANGER: 옅은 초록·빨강 바탕의 보조 버튼(완료 확인·종료처럼 결과가 갈리는 동작). */
enum class BtnStyle { FILLED, OUTLINE, TONAL, CYAN, ACTION, SOFT_OK, SOFT_DANGER }

/** 알약 버튼. 차량 화면 터치 목표에 맞춰 높이를 넉넉히 둔다. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BigButton(
    text: String, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.FILLED, enabled: Boolean = true,
    fontSize: TextUnit = 22.sp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val (bg, fg) = when {
        !enabled -> C.line to C.sub
        style == BtnStyle.FILLED -> C.navy to Color.White
        style == BtnStyle.CYAN -> C.blue to C.deep
        style == BtnStyle.ACTION -> C.action to if (C.dark) C.deep else Color.White
        style == BtnStyle.TONAL -> C.bg to C.ink
        style == BtnStyle.SOFT_OK -> C.ok.copy(alpha = 0.08f) to C.ok
        style == BtnStyle.SOFT_DANGER -> C.danger.copy(alpha = 0.07f) to C.danger
        else -> C.card to C.ink
    }
    val edge = when (style) {
        BtnStyle.OUTLINE -> C.line
        BtnStyle.SOFT_OK -> C.ok.copy(alpha = 0.3f)
        BtnStyle.SOFT_DANGER -> C.danger.copy(alpha = 0.3f)
        else -> null
    }
    Box(
        modifier
            .shadow(if (enabled && style == BtnStyle.FILLED) 5.dp else 0.dp, btnShape, clip = false)
            .clip(btnShape).background(if (enabled && style == BtnStyle.FILLED) Tone.navy else androidx.compose.ui.graphics.SolidColor(bg))
            .then(if (edge != null && enabled) Modifier.border(1.dp, edge, btnShape) else Modifier)
            // 되돌릴 수 없는 확정(대리 끝내기·반납)은 길게 눌러야 한다(Toyota 앱의 길게 눌러 실행)
            .then(
                if (onLongClick != null) Modifier.combinedClickable(enabled = enabled, role = Role.Button, onLongClickLabel = "확정", onLongClick = onLongClick, onClick = onClick)
                else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            )
            .widthIn(min = 48.dp).heightIn(min = if (isCompact()) 54.dp else 60.dp)
            .padding(horizontal = if (isCompact()) 15.dp else 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = fontSize, textAlign = TextAlign.Center) }
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) =
    BigButton(text, modifier, if (primary) BtnStyle.FILLED else BtnStyle.TONAL, enabled, 18.sp, onClick = onClick)

@Composable
fun Chip(text: String, selected: Boolean?, modifier: Modifier = Modifier, big: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    // selected가 null이면 동작 칩(버튼으로만 읽힘), 아니면 고르는 칩(선택 상태까지 읽힘)
    val on = selected == true
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape).background(if (on) C.ice else C.card)
            .border(1.dp, if (on) C.title else C.line, shape)
            .then(if (selected == null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier.selectable(selected, enabled = enabled, role = Role.Button, onClick = onClick))
            .widthIn(min = 48.dp).heightIn(min = 48.dp)
            .padding(horizontal = if (big) 18.dp else 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (!enabled) C.sub else if (on) C.title else C.ink, fontSize = if (big) 20.sp else 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}

@Composable
fun Note(text: String, level: Level? = Level.INFO) {
    val c = levelColor(level)
    Text(
        text, color = c, fontSize = 16.sp, lineHeight = 23.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
fun KeyValue(k: String, v: String) {
    if (isCramped()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(k, color = C.sub, fontSize = 16.sp)
        Text(v, color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(k, Modifier.weight(1f), color = C.sub, fontSize = 16.sp)
        Text(v, Modifier.weight(1.3f), color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
fun NavySwitch(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Box(Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)
        .semantics { contentDescription = label }.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center) {
        Switch(checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = C.navy, checkedThumbColor = Color.White, uncheckedTrackColor = C.line))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Wrap(spacing: Dp = 8.dp, content: @Composable RowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) { content() }
}

/** ⑤ 재출발 속도 게이지. 호·눈금·제한 표시가 같은 정사각형 좌표계를 쓴다. */
@Composable
fun SpeedGauge(speed: Float, limit: Float, size: Dp = 300.dp, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.widthIn(max = size).fillMaxWidth().testTag("school_gauge")) {
        val gaugeStroke = maxWidth * 0.055f
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                GaugeTrack(
                    fraction = speed / 50f, marker = limit / 50f, ticks = 10,
                    modifier = Modifier.fillMaxSize(), stroke = gaugeStroke,
                    color = Brush.linearGradient(if (speed <= limit) listOf(Color(0xFF26BC94), Color(0xFF087B66))
                        else listOf(Color(0xFFF6A03C), Color(0xFFD15A1C))),
                )
                Column(Modifier.fillMaxWidth(0.76f), horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
    }
}

/** 진행률은 막대 옆에 표시해 0%에서도 읽을 수 있다. */
@Composable
fun GradientProgress(progress: Float, modifier: Modifier = Modifier) {
    val fraction = progress.coerceIn(0f, 1f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).height(14.dp).clip(CircleShape).background(C.line)) {
            Box(Modifier.fillMaxWidth(fraction).height(14.dp).clip(CircleShape).background(Tone.blueMint))
        }
    }
}

/** 블루멤버스 카드의 바코드 장식. */
@Composable
fun Barcode(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // 막대를 4번 반복해 실제 바코드처럼 촘촘하게 그린다
        val base = listOf(2, 1, 3, 1, 1, 2, 3, 1, 2, 2, 1, 3, 1, 1, 2, 1, 3, 2, 1, 1, 2, 3, 1, 2, 1, 1, 3, 1, 2, 2)
        val pattern = base + base + base + base
        val unit = size.width / pattern.sum()
        var x = 0f
        pattern.forEachIndexed { i, n ->
            if (i % 2 == 0) drawRect(Color(0xFF0F172A), Offset(x, 0f), Size(n * unit, size.height))
            x += n * unit
        }
    }
}

/** 폰에서 실행 중인지(최소 폭 600dp 미만). App()이 정한다. */
val LocalPhoneText = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * 차 화면(멀리서 보는 1408dp 화면)에 맞춘 글자 크기를 폰 크기로 바꾸는 비선형 표.
 * 작은 글자는 조금만, 큰 제목은 많이 줄인다(Android 14 큰 글씨 비선형 배율과 같은 방식).
 * 참고 시안의 위계: 보조 설명 14, 본문 16~20, 카드 제목 22~24, 화면 큰 제목 27~32.
 */
object PhoneText {
    // 차 화면 sp → 폰 sp
    private val CAR = floatArrayOf(10f, 12f, 14f, 16f, 18f, 20f, 23f, 26f, 30f, 40f, 56f, 72f, 120f)
    private val PHONE = floatArrayOf(10f, 12f, 14f, 16f, 18f, 20f, 22f, 24f, 27f, 32f, 42f, 50f, 88f)
    fun phoneSp(car: Float) = lerp(CAR, PHONE, car)
    fun carSp(phone: Float) = lerp(PHONE, CAR, phone)
    private fun lerp(from: FloatArray, to: FloatArray, v: Float): Float {
        if (v <= from.first()) return v * to.first() / from.first()
        for (i in 1 until from.size) if (v <= from[i]) {
            val t = (v - from[i - 1]) / (from[i] - from[i - 1])
            return to[i - 1] + t * (to[i] - to[i - 1])
        }
        return v * to.last() / from.last()
    }

    /** 폰이면 sp 크기를 표대로 바꾼다(em·미지정은 그대로). */
    fun map(size: TextUnit, phone: Boolean): TextUnit =
        if (phone && size.isSp) TextUnit(phoneSp(size.value), androidx.compose.ui.unit.TextUnitType.Sp) else size
}

/**
 * 앱 전체가 쓰는 글자. Material3 Text와 같지만 폰에서는 글자 크기·줄 높이를 [PhoneText] 표로 바꾼다.
 * (Compose 글자 그리기는 Density를 값으로만 복사해 Density 쪽에서 바꿀 수 없어 여기서 바꾼다)
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.ink,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
) {
    val phone = LocalPhoneText.current
    val size = if (fontSize == TextUnit.Unspecified) style.fontSize else fontSize
    M3Text(
        text = text, modifier = modifier, color = color, fontSize = PhoneText.map(size, phone), fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = PhoneText.map(if (lineHeight == TextUnit.Unspecified) style.lineHeight else lineHeight, phone),
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}
