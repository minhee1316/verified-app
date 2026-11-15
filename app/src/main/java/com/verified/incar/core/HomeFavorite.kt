package com.verified.incar.core

enum class HomeFavorite(val label: String, val route: String) {
    CONTROL("차량 제어", "CONTROL"),
    CONTROL_HISTORY("최근 제어 결과", "CONTROL_HISTORY"),
    VALET("대리 맡기기", "VALET"),
    HAND_SERVICE("정비 맡기기", "HAND_SERVICE"),
    HAND_LEND("빌려주기", "HAND_LEND"),
    HISTORY("맡김 이력서", "HISTORY"),
    TRUNK("하차 3초 점검", "TRUNK"),
    SEARCH("기능·증상 검색", "SEARCH"),
}
