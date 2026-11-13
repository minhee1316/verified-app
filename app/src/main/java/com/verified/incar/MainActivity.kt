package com.verified.incar

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.verified.incar.ui.BottomNav
import com.verified.incar.ui.CarLifeMH
import com.verified.incar.ui.ControlMH
import com.verified.incar.ui.HomeMH
import com.verified.incar.ui.InsightMH
import com.verified.incar.ui.KeyMH
import com.verified.incar.ui.MyPageMH
import com.verified.incar.ui.ShopMH
import com.verified.incar.ui.VerifiedMH
import com.verified.incar.ui.MyCarMH
import com.verified.incar.ui.Root
import com.verified.incar.ui.ServiceMH
import com.verified.incar.ui.WalletMH
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import com.verified.incar.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.core.Module
import com.verified.incar.ui.C
import com.verified.incar.ui.VerifiedSans
import com.verified.incar.ui.ChauffeurScreen
import com.verified.incar.ui.ComfortScreen
import com.verified.incar.ui.DiagScreen
import com.verified.incar.ui.DriverScreen
import com.verified.incar.ui.EscortScreen
import com.verified.incar.ui.MissionScreen
import com.verified.incar.ui.NoticeScreen
import com.verified.incar.ui.Onboarding
import com.verified.incar.ui.Restricted
import com.verified.incar.ui.SchoolScreen
import com.verified.incar.ui.SettingsScreen
import com.verified.incar.ui.SimPanel
import com.verified.incar.ui.OwnerPinGate
import com.verified.incar.ui.TripScreen
import com.verified.incar.ui.TrunkScreen
import com.verified.incar.ui.ValetScreen
import com.verified.incar.ui.levelColor

class MainActivity : ComponentActivity() {
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        (application as VerifiedApp).runtime.onPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 차속·배터리 등 차 신호 위험 권한과, 장소 판정(집·학교)용 위치를 실행 중 요청한다. 거부하면 시뮬레이터 값으로 동작한다
        val wanted = listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION") +
            if (packageManager.hasSystemFeature("android.hardware.type.automotive")) com.verified.incar.vehicle.CarBridge.RUNTIME_PERMISSIONS else emptyList()
        askPermissions.launch(wanted.toTypedArray())
        // Pleos는 방향을 정하지 않으면 세로 창 하나로 띄운다. 차 화면은 가로 2단 기준이라 차에서만 가로로 고정한다
        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE)) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent { MaterialTheme { App((application as VerifiedApp).runtime) } }
        // 화면을 다시 만들거나 최근 앱에서 다시 열 때 지난 음성 명령을 다시 실행하지 않는다
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) handleGleo(intent)
    }

    override fun onResume() {
        super.onResume()
        // 판정이 화면 밖에서도 계속되도록 감시 서비스를 띄운다. 화면이 보일 때 불러야 내비가 앞에 와도 위치가 이어진다
        // (권한 창은 화면을 멈추기만 해서 허용 직후에는 onResume만 불린다)
        MonitorService.start(this, ui = true)
        // 설정 앱에서 나중에 허용한 차 권한도 반영한다(바뀐 게 없으면 아무것도 하지 않는다)
        (application as VerifiedApp).runtime.onPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGleo(intent)
    }

    /** Gleo AI가 component=activity 함수로 앱을 열었을 때. */
    private fun handleGleo(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Gleo.START_VALET && action != Gleo.START_ESCORT) return
        val rt = (application as VerifiedApp).runtime
        val r = Gleo.handle(rt, action, intent.getStringExtra(Gleo.PARAM))
        r.openTab?.let { rt.navigate.value = it }
        Gleo.reply(this, r)
    }
}

/** 하위 화면 제목 (가운데 정렬 앱바). */
private fun titleOf(id: String): String = if (com.verified.incar.core.VehicleFocus.fromRoute(id) != null) "차량 둘러보기" else Module.entries.firstOrNull { it.name == id }?.title ?: when (id) {
    "HOME" -> "확인됨"; "SHOP" -> "샵"; "CONTROL" -> "차량 제어"; "MYCAR" -> "내 차"; "VERIFIED" -> "기능"
    "CARLIFE" -> "카라이프"; "WALLET" -> "월렛"; "MYPAGE" -> "마이페이지"; "SERVICE" -> "차량 관리"; "INSIGHT" -> "드라이빙 인사이트"; "KEY" -> "디지털 키"
    "CAR_SETUP" -> "차량 등록"
    "VEHICLE_VIEW" -> "차량 둘러보기"
    "HAND_SERVICE" -> "정비 맡기기"
    "HAND_LEND" -> "차 빌려주기"
    "PHONE_LINK" -> "차주 휴대폰 알림"
    "TRIAL" -> "예시 차량 체험"
    "EV_ROUTE" -> "전기차 경로"; "VALET_WATCH" -> "발레 감시"; "RADIO_DIAG" -> "무선 연결 진단"; "CAR_3D" -> "3D로 둘러보기"
    "NOTICE" -> "보낸 알림"; "SETTINGS" -> "설정"; "SEARCH" -> "검색"; "HISTORY" -> "맡김 이력서"; else -> id
}

/** 출발지·도착지·알림·정비 이력처럼 차주의 생활이 드러나는 화면. */
private val PRIVATE_SCREENS = setOf(Module.TRIP.name, Module.COMFORT.name, "NOTICE", "INSIGHT", "SERVICE", "HAND_SERVICE", "HAND_LEND", "KEY", "WALLET", "HISTORY", "VEHICLE_VIEW")
private fun needsOwner(id: String, handedOver: Boolean) =
    id == "SETTINGS" || id == "CAR_SETUP" || id == "PHONE_LINK" ||
        (handedOver && (id in PRIVATE_SCREENS || com.verified.incar.core.VehicleFocus.fromRoute(id) != null))

/** 켜져 있어야 엔진이 판정하는 기능(VerdictEngine.update의 on() 검사와 같게 유지). */
private val JUDGED = setOf(Module.DRIVER, Module.TRIP, Module.SCHOOL, Module.CHAUFFEUR, Module.TRUNK)

@Composable
fun App(vm: VerifiedRuntime) {
    // 폰(최소 폭 600dp 미만)에서는 차 화면 크기로 만든 글자를 폰 글자 크기 표(PhoneText)로 바꿔 그린다.
    // dp(여백·터치 영역)와 사용자가 고른 글자 크기 배율은 건드리지 않는다
    val phone = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp < 600
    androidx.compose.runtime.CompositionLocalProvider(com.verified.incar.ui.LocalPhoneText provides phone) { AppContent(vm, phone) }
}

@Composable
private fun AppContent(vm: VerifiedRuntime, phone: Boolean = false) {
    val u by vm.ui.collectAsState()
    // 마이현대처럼 하단 탭이 뿌리이고, 그 아래로 들어간 화면은 뒤로가기로 돌아온다
    var stack by rememberSaveable { mutableStateOf(arrayListOf("HOME")) }
    val pageState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    fun setPath(path: List<String>) {
        (stack - path.toSet()).forEach { pageState.removeState(it) }
        stack = ArrayList(path)
    }
    val tab = stack.last()
    val vehicleFocus = com.verified.incar.core.VehicleFocus.fromRoute(tab)
    var showSim by remember { mutableStateOf(false) }
    fun navigate(id: String, rootTab: Boolean = false) {
        // 다른 화면으로 가면 기다리던 차주 조작은 버린다(나중에 차주가 PIN을 넣었을 때 엉뚱한 조작이 실행되지 않게)
        vm.cancelOwnerRequest()
        val target = when (id) { "MH_CAR" -> "MYCAR"; "MH_LIFE" -> "CARLIFE"; else -> id }
        val handedOver = u.engine.valet.active || u.engine.lend.active
        val open = {
            // 꺼진 판정 기능을 열면 켠다(판정이 안 도는데 '자동으로 시작해요'로 보이지 않게). 차를 넘긴 동안에는 차주 확인 뒤에만
            if (!handedOver || !vm.ownerLocked()) Module.entries.firstOrNull { it.name == target && it in JUDGED && it !in u.settings.modules }?.let { m ->
                vm.toast("${m.no} ${m.title}을(를) 켰어요 · 설정에서 끌 수 있어요")
                vm.settings { it.copy(extraModules = it.extraModules + m) }
            }
            val previous = stack.indexOf(target)
            setPath(when {
                rootTab -> listOf(target)
                previous >= 0 -> stack.take(previous + 1)
                else -> stack + target
            })
        }
        // 설정은 차주만 연다 (PIN을 정했을 때). 대리·빌려주기 중에는 차주의 이동 기록이 담긴 화면도 차주만 연다
        if (needsOwner(target, handedOver)) vm.ownerOnly(open) else open()
    }
    val go: (String) -> Unit = { navigate(it) }
    val back = { if (stack.size > 1) setPath(stack.dropLast(1)) }
    val ownerRequest by vm.ownerRequest.collectAsState()
    val handedOver = u.engine.valet.active || u.engine.lend.active
    // 잠금이 다시 걸리면(대리·빌려주기 시작, 5분 지남) 이미 열려 있던 차주 전용 화면도 닫는다
    LaunchedEffect(u.ownerUnlocked, handedOver) {
        if (!u.ownerUnlocked) {
            setPath(stack.filterNot { needsOwner(it, handedOver) }.ifEmpty { listOf("HOME") })
            showSim = false
        }
    }
    // 생활 유형을 다시 고르러 가면 처음 화면에서 시작한다
    LaunchedEffect(u.settings.onboarded) { if (!u.settings.onboarded) setPath(listOf("HOME")) }
    // 차량의 뒤로 버튼: 앱을 닫지 않고 한 화면 뒤로(PIN 패드가 떠 있으면 패드를 닫는다)
    androidx.activity.compose.BackHandler(enabled = showSim || ownerRequest != null || (stack.size > 1 && !u.signals.uxRestricted)) {
        when {
            ownerRequest != null -> vm.cancelOwnerRequest()
            showSim -> showSim = false
            else -> back()
        }
    }
    C.dark = C.forceDark ?: isSystemInDarkTheme()
    // 시뮬레이터는 개발(debug) 빌드에서만 버튼으로 보이고, 제출용 빌드에서는 로고를 길게 눌러야 열린다
    val debuggable = BuildConfig.DEMO || (LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val nav by vm.navigate.collectAsState()
    LaunchedEffect(nav) { nav?.let { go(it); vm.navigate.value = null } }
    // ⑩ 출발 준비는 앱 제어 지원 차에서만 보여 준다 (미등록이면 등록 안내를 위해 보여 준다). ⑧은 항상
    val comfortOn = u.settings.carModel == null || u.canControl
    val modules = Module.entries.filter { it in u.settings.modules || it == Module.DIAG || (it == Module.COMFORT && comfortOn) }
    val restricted = u.signals.uxRestricted
    // 주행 중에는 PIN 패드를 띄우지 않고 기다리던 차주 조작도 버린다(P단에서 뒤늦게 튀어나오지 않게)
    LaunchedEffect(restricted, ownerRequest) { if (restricted && ownerRequest != null) { vm.cancelOwnerRequest(); vm.toast("주행 중에는 차주 확인이 필요한 조작을 할 수 없어요") } }
    val selectedRoot = stack.lastOrNull { id -> Root.entries.any { it.name == id } } ?: "HOME"

    // 앱 글꼴: Pretendard 기반 Verified Sans (res/font, 라이선스는 assets/licenses)
    // 폰에서는 큰 제목이 줄을 바꾸므로 줄 높이를 글자 크기에 비례(1.4배)하게 둔다(기본 24sp 고정이면 30sp 넘는 제목의 줄이 겹친다)
    androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = if (phone) com.verified.incar.ui.VerifiedSansPhone else VerifiedSans).let {
        if (phone) it.copy(lineHeight = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Em)) else it
    }) {
    Column(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()) {
        AppBar(u, titleOf(tab), tab == "HOME", stack.size > 1 && !restricted, back, showSim, debuggable, {
            when {
                showSim -> showSim = false
                !u.simAllowed -> vm.toast("실차에 연결된 동안에는 신호 시뮬레이터를 쓸 수 없어요")
                debuggable -> showSim = true
                else -> vm.ownerOnly { showSim = true }
            }
        }, { go("NOTICE") }, { go("MYPAGE") }, { go("SETTINGS") })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (restricted) Restricted(u, u.drivingLines)
            else pageState.SaveableStateProvider(tab) {
              Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when {
                    !u.settings.onboarded -> Onboarding(vm, u)
                    !u.ownerUnlocked && needsOwner(tab, handedOver) -> Unit
                    vehicleFocus != null -> com.verified.incar.ui.VehicleExplorer(vm, u, go, vehicleFocus)
                    else -> when (tab) {
                        Module.VALET.name -> ValetScreen(vm, u)
                        Module.DRIVER.name -> DriverScreen(vm, u)
                        Module.TRIP.name -> TripScreen(vm, u)
                        Module.ESCORT.name -> EscortScreen(vm, u)
                        Module.SCHOOL.name -> SchoolScreen(vm, u)
                        Module.CHAUFFEUR.name -> ChauffeurScreen(vm, u)
                        Module.TRUNK.name -> TrunkScreen(vm, u)
                        Module.DIAG.name -> DiagScreen(vm, u, u.diag, go)
                        Module.MISSION.name -> MissionScreen(vm, u)
                        Module.COMFORT.name -> ComfortScreen(vm, u)
                        "SHOP" -> ShopMH(vm, u, go)
                        "CONTROL" -> ControlMH(vm, u, go)
                        "MYCAR" -> MyCarMH(vm, u, go)
                        "VEHICLE_VIEW" -> com.verified.incar.ui.VehicleExplorer(vm, u, go)
                        "VERIFIED" -> VerifiedMH(u, modules, go)
                        "MYPAGE" -> MyPageMH(u, modules, go)
                        "CARLIFE" -> CarLifeMH(vm, u, go)
                        "WALLET" -> WalletMH(vm, u)
                        "SERVICE" -> ServiceMH(vm, u)
                        "INSIGHT" -> InsightMH(u)
                        "KEY" -> KeyMH(vm, u)
                        "NOTICE" -> NoticeScreen(u) { go("PHONE_LINK") }
                        "PHONE_LINK" -> com.verified.incar.ui.PhoneNotificationSettings(vm, u)
                        "HAND_SERVICE" -> com.verified.incar.ui.HandoverScreen(vm, u, com.verified.incar.core.HandKind.SERVICE)
                        "HAND_LEND" -> com.verified.incar.ui.HandoverScreen(vm, u, com.verified.incar.core.HandKind.LEND)
                        "SETTINGS" -> SettingsScreen(vm, u)
                        "CAR_SETUP" -> com.verified.incar.ui.Card(Modifier.fillMaxWidth().padding(if (phone) 16.dp else 28.dp)) {
                            com.verified.incar.ui.CarModelPicker(vm, u)
                        }
                        "HISTORY" -> com.verified.incar.ui.HistoryScreen(vm, u)
                        "SEARCH" -> com.verified.incar.ui.SearchMH(go, showMock = u.simAllowed)
                        "TRIAL" -> com.verified.incar.ui.TrialScreen(vm, u)
                        "EV_ROUTE" -> com.verified.incar.ui.EvRouteScreen()
                        "VALET_WATCH" -> com.verified.incar.ui.ValetWatchScreen()
                        "RADIO_DIAG" -> com.verified.incar.ui.RadioDiagScreen()
                        "CAR_3D" -> com.verified.incar.ui.Car3DScreen(u)
                        else -> HomeMH(vm, u, (if (comfortOn) listOf(Module.COMFORT) else emptyList()) + modules.filter { it in u.settings.modules && it != Module.COMFORT }, go)
                    }
                }
              }
            }
            Ambient(u)
            // 시뮬레이터는 시연·개발용 서랍: 평소엔 숨겨 실제 화면을 가리지 않는다
            if (showSim) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).clickable { showSim = false })
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) { SimPanel(vm, u) }
            }
            if (ownerRequest != null && !restricted) OwnerPinGate(vm)
        }
        if (u.toast.isNotEmpty() && u.toast != VerifiedRuntime.IDLE_TOAST) Row(
            Modifier.fillMaxWidth().background(C.card).padding(start = if (phone) 16.dp else 28.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(u.toast, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }.padding(vertical = 6.dp), color = C.ink,
                fontSize = 16.sp, maxLines = if (restricted) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            if (!restricted) androidx.compose.material3.IconButton(onClick = { vm.toast(VerifiedRuntime.IDLE_TOAST) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Close, "안내 닫기", tint = C.sub)
            }
        }
        if (!restricted && u.settings.onboarded) BottomNav(selectedRoot, showShop = u.simAllowed) { navigate(it, rootTab = true) }
    }
    }
}

/** 하위 화면에서는 제목 옆의 뒤로 버튼으로 이전 작업을 이어 간다. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AppBar(
    u: UiState, title: String, isHome: Boolean, canBack: Boolean, back: () -> Unit, showSim: Boolean, simButton: Boolean,
    toggleSim: () -> Unit, openNotice: () -> Unit, openMyPage: () -> Unit, openSettings: () -> Unit,
) {
    val compact = com.verified.incar.ui.isCompact()
    val restricted = u.signals.uxRestricted
    Row(
        Modifier.fillMaxWidth().background(C.card).padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canBack) androidx.compose.material3.IconButton(onClick = back, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", tint = C.ink)
        }
        // 체험 모드는 시나리오 버튼으로만 움직인다(신호 패널은 시연·개발용)
        val logo = if (isHome && u.simAllowed && !u.trial && !simButton && !restricted)
            Modifier.combinedClickable(onClick = {}, onLongClickLabel = "신호 시뮬레이터 열기", onLongClick = toggleSim)
        else Modifier
        Text(
            if (restricted) "주행 중" else title, Modifier.weight(1f).then(logo).padding(start = if (canBack) 0.dp else 4.dp),
            color = C.ink, fontWeight = FontWeight.Bold, fontSize = if (compact) 21.sp else 24.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (u.signals.audioFocusLost) Icon(Icons.AutoMirrored.Outlined.VolumeOff, "순정 오디오 우선", tint = C.sub)
        if (u.simAllowed && !u.carConnected && !isHome && (compact || !simButton))
            Text(if (u.trial) "체험" else "시연", Modifier.padding(horizontal = 4.dp), color = C.sub, fontSize = 15.sp)
        if (u.simAllowed && (simButton || showSim)) {
            val label = if (showSim) "시뮬레이터 닫기" else "신호 시뮬레이터"
            if (compact) androidx.compose.material3.IconButton(onClick = toggleSim, modifier = Modifier.size(48.dp)) {
                Icon(if (showSim) Icons.Outlined.Close else Icons.Outlined.Tune, label, tint = C.sub)
            } else androidx.compose.material3.TextButton(onClick = toggleSim, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(label, fontSize = 16.sp, color = C.sub)
            }
        }
        if (canBack) {
            if (title != "설정") androidx.compose.material3.IconButton(onClick = openSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Settings, "설정", tint = C.ink)
            }
        } else if (u.settings.onboarded) {
            androidx.compose.material3.IconButton(onClick = openNotice, enabled = !restricted, modifier = Modifier.size(48.dp).testTag("nav_NOTICE")) {
                Icon(Icons.Outlined.Notifications, "알림", tint = if (restricted) C.sub else C.ink)
            }
            androidx.compose.material3.IconButton(onClick = openMyPage, enabled = !restricted, modifier = Modifier.size(48.dp).testTag("nav_MENU")) {
                Icon(Icons.Outlined.Person, "마이페이지", tint = if (restricted) C.sub else C.ink)
            }
        }
    }
    HorizontalDivider(color = C.line)
}

/** 앰비언트 조명 제어 결과를 화면 양 끝 막대로 보여 준다 (실차에서는 실내 조명이 같은 색으로 켜진다). */
@Composable
private fun Ambient(u: UiState) {
    val a = u.engine.ambient ?: return
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(10.dp).fillMaxHeight().background(if (a.left != null) levelColor(a.left) else Color.Transparent))
        Box(Modifier.weight(1f))
        Box(Modifier.width(10.dp).fillMaxHeight().background(if (a.right != null) levelColor(a.right) else Color.Transparent))
    }
}

