package com.verified.incar.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.core.Feature
import com.verified.incar.core.FeatureIndex
import com.verified.incar.core.Module
import com.verified.incar.core.Situation

@Composable
internal fun SituationDirectory(u: UiState, modules: List<Module>, go: (String) -> Unit) {
    val compact = isCompact()
    var situation by rememberSaveable { mutableStateOf(Situation.HANDOVER) }
    var showComparison by rememberSaveable { mutableStateOf(false) }
    var showPersonal by rememberSaveable { mutableStateOf(false) }
    TwoUp(28.dp, 20.dp, 20.dp, 14.dp, leftWeight = 1.2f, left = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DirectoryTitle("어떤 상황인가요?", Modifier.weight(1f))
                if (compact) IconButton(onClick = { go("SEARCH") }, modifier = Modifier.size(48.dp).testTag("directory_search")) {
                    Icon(Icons.Outlined.Search, "기능·증상 검색", tint = C.title)
                }
            }
            val situationIcons = listOf(Icons.Outlined.DirectionsCar, Icons.Outlined.Group, Icons.Outlined.MeetingRoom, Icons.Outlined.Insights)
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Situation.entries.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { item ->
                            val on = item == situation
                            Column(Modifier.weight(1f).testTag("situation_${item.name}").lift(RoundedCornerShape(20.dp), if (on) 6.dp else 3.dp).clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(if (C.dark) listOf(C.chip, C.card) else if (on) listOf(Color(0xFFE7EDF7), Color(0xFFD9E1EE)) else listOf(Color(0xFFF0F1F5), Color(0xFFE5E7ED))))
                                .selectable(selected = on, role = Role.RadioButton) { situation = item }
                                .heightIn(min = if (compact) 146.dp else 158.dp).padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                                DiscoveryIcon(situationIcons[item.ordinal % situationIcons.size])
                                Text(item.label, color = C.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            DirectoryTitleSmall("기능 목록")
            Card(Modifier.fillMaxWidth(), padding = 18.dp) {
                FeatureRows(FeatureIndex.ALL.filter { it.situation == situation }, "feature", go)
            }
        }
        if (!compact) Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            DirectoryRow("기능·증상 검색", icon = Icons.Outlined.Search) { go("SEARCH") }
        }
    }, right = {
        if (!compact) Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            DirectoryTitle("마이현대와 어떻게 다른가요?")
            ComparisonDetails(u, situation)
        }
        Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            DirectoryRow("내 기능 ${modules.size}개", icon = if (showPersonal) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore) {
                showPersonal = !showPersonal
            }
            if (showPersonal) {
                val routes = modules.mapTo(mutableSetOf()) { it.name }
                FeatureRows(FeatureIndex.ALL.filter { it.route in routes }, "personal", go)
            }
            HorizontalDivider(color = C.line)
            DirectoryRow("내 기능 설정") { go("SETTINGS") }
        }
        if (compact) Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            DirectoryRow(
                "마이현대와 어떻게 다른가요?",
                modifier = Modifier.testTag("directory_comparison").semantics {
                    stateDescription = if (showComparison) "펼침" else "접힘"
                },
                icon = if (showComparison) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            ) { showComparison = !showComparison }
            if (showComparison) ComparisonDetails(u, situation)
        }
        if (u.simAllowed && !u.carConnected) DemoWalkthrough(go)
    })
}

@Composable
private fun DemoWalkthrough(go: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("demo_walkthrough"), padding = 20.dp) {
        DirectoryTitle("처음이라면 이렇게 둘러보세요")
        Text("차량 등록 없이 예시 차량으로 둘러볼 수 있어요.", fontSize = 16.sp, lineHeight = 23.sp, color = C.sub)
        DirectoryRow("1. 차량 제어", "잠금·공조를 누르고 상태 변화를 확인해요.", Modifier.testTag("demo_step_CONTROL")) { go("CONTROL") }
        HorizontalDivider(color = C.line)
        DirectoryRow("2. 대리 안심 주차", "기사 동의부터 운행·주차 확인까지 따라가요.", Modifier.testTag("demo_step_VALET")) { go("VALET") }
        HorizontalDivider(color = C.line)
        DirectoryRow("3. 돌려받은 기록", "체험을 마친 뒤 맡긴 시간과 이동 기록을 살펴봐요.", Modifier.testTag("demo_step_HISTORY")) { go("HISTORY") }
        DirectoryRow("예시 차종 바꾸기", modifier = Modifier.testTag("demo_models"), icon = Icons.Outlined.DirectionsCar) { go("TRIAL") }
    }
}

@Composable
private fun ComparisonDetails(u: UiState, situation: Situation) {
    ComparisonLine("마이현대", situation.myHyundai)
    HorizontalDivider(color = C.line)
    ComparisonLine("현대 카가드", situation.verified)
    if (u.simAllowed && !u.carConnected)
        Text("지금은 예시 차량 신호로 체험하고 있어요.", color = C.sub, fontSize = 16.sp)
    else if (situation != Situation.RECORDS)
        Text("확인 가능한 항목은 차량 신호와 권한에 따라 달라요.", color = C.sub, fontSize = 16.sp)
}

@Composable
private fun ComparisonLine(label: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = C.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(body, color = C.ink, fontSize = 19.sp, lineHeight = 28.sp)
    }
}

@Composable
internal fun FeatureSearch(go: (String) -> Unit, showMock: Boolean = true) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val results = remember(query, showMock) {
        // 샵·월렛은 예시 화면이라 시연·체험 모드에서만 찾아 준다
        (if (query.isBlank()) FeatureIndex.ALL else FeatureIndex.search(query)).filter { showMock || it.route !in MOCK_ROUTES }
    }
    TwoUp(28.dp, 20.dp, 20.dp, 14.dp, leftWeight = 0.85f, left = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("search_box").lift(RoundedCornerShape(18.dp), 4.dp).background(C.card, RoundedCornerShape(18.dp)),
                placeholder = { Text("기능 이름 또는 증상", color = C.sub) },
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = C.sub) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "검색어 지우기", tint = C.ink) }
                }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.title, unfocusedBorderColor = Color.Transparent,
                    focusedLabelColor = C.title, unfocusedLabelColor = C.sub, cursorColor = C.title,
                ),
                textStyle = LocalTextStyle.current.copy(color = C.ink, fontSize = 18.sp),
            )
            if (query.isBlank()) DirectoryTitleSmall("인기 태그")
            if (query.isBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("주차 완료", "와이퍼", "공기압").forEach { example ->
                    Text("#$example", Modifier.clip(RoundedCornerShape(50)).background(C.ice).clickable(role = Role.Button) { query = example; focus.clearFocus() }
                        .padding(horizontal = 14.dp, vertical = 13.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.title)
                }
            }
        }
    }, right = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val brief = query.isBlank() && !showAll
            DirectoryTitle(if (brief) "자주 찾는 기능" else if (query.isBlank()) "전체 기능" else "검색 결과 ${results.size}개")
            if (brief) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.take(4).chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { f ->
                                Column(Modifier.weight(1f).testTag("search_${f.route}").lift(RoundedCornerShape(20.dp), 8.dp).clip(RoundedCornerShape(20.dp)).background(C.card)
                                    .clickable(role = Role.Button) { focus.clearFocus(); go(f.route) }.heightIn(min = 168.dp).padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                                    DiscoveryIcon(discoveryIcon(f.route))
                                    Text(f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().testTag("search_all").clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(if (C.dark) listOf(C.chip, C.card) else listOf(Color(0xFFE5E8EF), Color(0xFFDADFE8))))
                    .clickable(role = Role.Button) { showAll = true }.heightIn(min = 58.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("전체 기능 ${results.size}개 보기", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        color = C.title, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(24.dp), tint = C.title)
                }
            } else if (results.isEmpty()) {
                Text("일치하는 기능이 없어요. 더 짧은 단어로 검색하거나 상황별로 찾아보세요.", fontSize = 18.sp, color = C.sub)
                DirectoryRow("상황별로 찾기") { focus.clearFocus(); go("VERIFIED") }
            } else Card(Modifier.fillMaxWidth(), padding = 18.dp) {
                FeatureRows(results, "search") { route -> focus.clearFocus(); go(route) }
            }
        }
    })
}

private val MOCK_ROUTES = setOf("SHOP", "WALLET")

@Composable
private fun DirectoryTitle(title: String, modifier: Modifier = Modifier) = Text(
    title, modifier.semantics { heading() }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.ink,
)

@Composable
private fun DirectoryTitleSmall(title: String) = Text(title, Modifier.padding(top = 6.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)

@Composable
private fun FeatureRows(features: List<Feature>, tagPrefix: String, go: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        features.forEachIndexed { i, feature ->
            if (i > 0) HorizontalDivider(color = C.line)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBubble(discoveryIcon(feature.route), 44.dp)
                Box(Modifier.weight(1f)) {
                    DirectoryRow(feature.title, feature.detail, Modifier.testTag("${tagPrefix}_${feature.route}")) { go(feature.route) }
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(
    title: String, detail: String? = null, modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = C.ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, color = C.sub, fontSize = 17.sp, lineHeight = 25.sp)
        }
        Icon(icon, contentDescription = null, Modifier.size(24.dp), tint = C.sub)
    }
}
