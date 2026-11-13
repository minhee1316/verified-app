package com.verified.incar.ui

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
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DirectoryTitle("어떤 상황인가요?", Modifier.weight(1f))
                if (compact) IconButton(onClick = { go("SEARCH") }, modifier = Modifier.size(48.dp).testTag("directory_search")) {
                    Icon(Icons.Outlined.Search, "기능·증상 검색", tint = C.title)
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                maxItemsInEachRow = if (compact) 2 else Int.MAX_VALUE,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Situation.entries.forEach { item ->
                    Chip(item.label, selected = item == situation,
                        modifier = (if (compact) Modifier.weight(1f) else Modifier)
                            .heightIn(min = 48.dp).testTag("situation_${item.name}")) { situation = item }
                }
            }
            if (compact) {
                DirectoryRow(
                    "마이현대와 어떻게 다른가요?",
                    modifier = Modifier.testTag("directory_comparison").semantics {
                        stateDescription = if (showComparison) "펼침" else "접힘"
                    },
                    icon = if (showComparison) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                ) { showComparison = !showComparison }
                if (showComparison) ComparisonDetails(u, situation)
                HorizontalDivider(color = C.line)
            }
            FeatureRows(FeatureIndex.ALL.filter { it.situation == situation }, "feature", go)
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
    })
}

@Composable
private fun ComparisonDetails(u: UiState, situation: Situation) {
    ComparisonLine("마이현대", situation.myHyundai)
    HorizontalDivider(color = C.line)
    ComparisonLine("확인됨", situation.verified)
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
    val focus = LocalFocusManager.current
    val results = remember(query, showMock) {
        // 샵·월렛은 예시 화면이라 시연·체험 모드에서만 찾아 준다
        (if (query.isBlank()) FeatureIndex.ALL else FeatureIndex.search(query)).filter { showMock || it.route !in MOCK_ROUTES }
    }
    TwoUp(28.dp, 20.dp, 20.dp, 14.dp, leftWeight = 0.85f, left = {
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            DirectoryTitle("기능·증상 찾기")
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("search_box"),
                label = { Text("기능 이름 또는 증상") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = C.sub) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "검색어 지우기", tint = C.ink) }
                }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.title, unfocusedBorderColor = C.sub,
                    focusedLabelColor = C.title, unfocusedLabelColor = C.sub, cursorColor = C.title,
                ),
                textStyle = LocalTextStyle.current.copy(color = C.ink, fontSize = 18.sp),
            )
            if (query.isBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("주차 완료", "와이퍼", "공기압").forEach { example ->
                    Chip(example, null, Modifier.heightIn(min = 48.dp)) { query = example; focus.clearFocus() }
                }
            }
        }
    }, right = {
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            DirectoryTitle(if (query.isBlank()) "전체 기능" else "검색 결과 ${results.size}개")
            if (results.isEmpty()) {
                Text("일치하는 기능이 없어요. 더 짧은 단어로 검색하거나 상황별로 찾아보세요.", fontSize = 18.sp, color = C.sub)
                DirectoryRow("상황별로 찾기") { focus.clearFocus(); go("VERIFIED") }
            } else FeatureRows(results, "search") { route -> focus.clearFocus(); go(route) }
        }
    })
}

private val MOCK_ROUTES = setOf("SHOP", "WALLET")

@Composable
private fun DirectoryTitle(title: String, modifier: Modifier = Modifier) = Text(
    title, modifier.semantics { heading() }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.ink,
)

@Composable
private fun FeatureRows(features: List<Feature>, tagPrefix: String, go: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        features.forEachIndexed { i, feature ->
            if (i > 0) HorizontalDivider(color = C.line)
            DirectoryRow(feature.title, feature.detail, Modifier.testTag("${tagPrefix}_${feature.route}")) { go(feature.route) }
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
