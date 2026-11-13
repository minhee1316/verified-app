package com.verified.incar.core

// 마이현대의 제공 범위는 현대자동차 공식 '마이현대 소개', '시작하기 > 차량 공유' 기준.
// https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/myhyundai-information
// https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/car-share
enum class Situation(val label: String, val myHyundai: String, val verified: String) {
    HANDOVER("차 맡기기",
        "대리운전을 호출하고, 가족과 차량을 공유하거나 정비를 예약할 수 있어요.",
        "기사님이 동의하면 집 도착과 P단이 확인된 시각을 남겨요. 차를 빌려주거나 정비에 맡겼을 때는 반납 전후 달라진 항목을 모아 봐요."),
    PASSENGERS("함께 타기",
        "드라이빙 인사이트에서 운전 습관과 연비 등 주행 통계를 볼 수 있어요.",
        "동승자가 차에 타고 내리는 순간을 살펴요. 등교 하차 후 재출발 속도와 뒷좌석 승객이 있을 때의 급감속을 확인할 수 있어요."),
    PARKING("출발·하차",
        "휴대폰에서 문 잠금이나 공조를 요청하고 차량 상태를 조회할 수 있어요.",
        "제어를 요청한 시각과 차에서 응답한 시각을 구분해요. 잠금 신호가 도착하지 않으면 결과를 ‘미확인’으로 표시해요."),
    RECORDS("기록 보기",
        "주행 패턴을 비교하고 정비 이력과 소모품 교체 정보를 조회할 수 있어요.",
        "업무·출퇴근·개인 운행을 나누고, 대리·정비·대여 기록은 맡긴 건별로 모아요. 두 기록 모두 CSV로 내보낼 수 있어요."),
}

data class Feature(
    val title: String,
    val detail: String,
    val route: String,
    val keywords: List<String>,
    val situation: Situation? = null,
)

/** 검색과 상황별 안내가 같은 이름과 목적지를 사용한다. */
object FeatureIndex {
    val ALL = listOf(
        Feature("차량 외관·실내 사진", "문별 잠금과 좌석별 안전벨트를 보고 실내 공조를 체험해요.", "VEHICLE_VIEW",
            listOf("차량 사진", "외관", "실내", "안전벨트", "문 열림", "어느 문", "히터", "에어컨", "바람", "온도"), Situation.PARKING),
        Feature("대리 주차 확인", "기사 동의부터 집 도착과 주차 완료까지 시각과 근거를 확인해요.", Module.VALET.name,
            listOf("대리", "대리운전", "기사", "주차 완료", "발레", "대리 안심 주차", "집 도착", "집에 잘 도착"), Situation.HANDOVER),
        Feature("차 빌려주기·반납", "빌려주기를 시작하고 돌려받을 때 주행거리와 배터리 변화를 봐요.", "HAND_LEND",
            listOf("빌려주기", "차 빌려주기", "반납", "빌려준 차 반납"), Situation.HANDOVER),
        Feature("정비 맡김", "정비소에 차를 맡기고 돌려받을 때 달라진 차 상태를 확인해요.", "HAND_SERVICE",
            listOf("맡기기", "맡김", "정비소", "주차대행"), Situation.HANDOVER),
        Feature("운전자 확인", "운전자와 보험에 등록한 범위를 확인해요.", Module.DRIVER.name,
            listOf("운전자", "보험", "자세", "시트", "운전자 범위 지킴이"), Situation.HANDOVER),
        Feature("차량 관리", "정비 이력과 소모품 교체 시기를 확인해요.", "SERVICE",
            listOf("소모품", "엔진오일", "정비", "블루핸즈", "정비 예약", "타이어", "공기압", "펑크", "배터리", "충전"), Situation.RECORDS),
        Feature("부축 동행", "부모님이 차에 타면 착석과 벨트를 확인하고 하차할 문을 안내해요.", Module.ESCORT.name,
            listOf("부모님", "동행", "보호자", "어르신", "보도", "하차 빛"), Situation.PASSENGERS),
        Feature("스쿨존 재출발", "아이를 내려준 뒤 1분 또는 300m까지 재출발 최고 속도를 확인해요.", Module.SCHOOL.name,
            listOf("스쿨존", "아이", "등교", "학교", "등하교"), Situation.PASSENGERS),
        Feature("뒷좌석 승차감", "동승자가 있을 때 급감속이 있었는지 살펴봐요.", Module.CHAUFFEUR.name,
            listOf("승차감", "뒷좌석", "쇼퍼", "손님", "급감속"), Situation.PASSENGERS),
        Feature("문 잠금·공조 제어", "요청 뒤에 차량 신호가 들어왔는지 확인해요. 제어 실행은 시연용이에요.", "CONTROL",
            listOf("문 잠금", "잠금", "잠그기", "공조", "비상등", "경적", "원격", "원격 제어", "문 잠겼나"), Situation.PARKING),
        Feature("출발 준비", "운전자별 온도와 열선 설정을 저장해요. 원격 공조는 연동 시연용이에요.", Module.COMFORT.name,
            listOf("원격 공조", "히터", "에어컨", "열선", "통풍", "출발 예약", "성에", "겨울"), Situation.PARKING),
        Feature("하차 점검", "내리기 전 트렁크 짐과 열린 창문을 살피고 주차 층을 확인해요.", Module.TRUNK.name,
            listOf("트렁크", "짐", "하차", "주차 위치", "지하", "층", "방전", "전조등", "실내등"), Situation.PARKING),
        Feature("증상 확인 도우미", "와이퍼나 경고음이 평소와 다르면 관련 차량 설정을 살펴봐요.", Module.DIAG.name,
            listOf("이거 고장이야", "고장", "이상", "경고", "와이퍼", "크루즈", "김", "습기", "벨트", "경고음", "조명", "소리", "카플레이", "안드로이드 오토", "끊김", "무선 연결", "프로젝션"), Situation.PARKING),
        Feature("첫 운전·N단 유지", "처음 타는 차의 조작을 익히고 자동세차 전 N단 유지 방법을 확인해요.", Module.MISSION.name,
            listOf("첫 운전", "미션", "n단", "중립", "이중주차", "자동세차", "사용법"), Situation.PARKING),
        Feature("업무 운행기록부", "같은 도착지의 지난 목적을 추천받고 업무 운행을 CSV로 내보내요.", Module.TRIP.name,
            listOf("운행기록", "운행일지", "국세청", "업무", "출퇴근", "csv"), Situation.RECORDS),
        Feature("맡김 이력서", "대리·정비·빌려주기 기록을 건별로 다시 보고 CSV로 내보내요.", "HISTORY",
            listOf("이력서", "맡김 이력서", "중고차", "인증중고차", "매각", "보험", "ubi", "인수인계서", "csv", "과태료", "범칙금", "단속", "공회전"), Situation.RECORDS),
        Feature("드라이빙 인사이트", "최근 7일간 주행거리와 급가속·급감속 횟수를 봐요.", "INSIGHT",
            listOf("주행 기록", "주행거리", "연비", "전비", "인사이트", "급가속"), Situation.RECORDS),
        Feature("보낸 알림", "차주 휴대폰을 연결하고 알림 내용과 전송 상태를 확인해요.", "NOTICE",
            listOf("알림", "보낸", "전송", "보관"), Situation.RECORDS),
        Feature("디지털 키", "공유 기간과 키 회수 과정을 체험해요. 실제 키 발급은 시연 범위 밖이에요.", "KEY",
            listOf("키", "공유", "디지털키")),
        Feature("카라이프", "세차 예약과 주변 충전소 찾기 등 서비스 예시를 살펴봐요.", "CARLIFE",
            listOf("대리 부르기", "세차", "단속", "주정차", "긴급출동", "견인", "사고", "비상등", "대피", "충전소", "주변", "주차장")),
        Feature("월렛·블루멤버스", "시연용 멤버십 포인트와 적립 내역을 확인해요.", "WALLET",
            listOf("포인트", "블루멤버스", "바코드", "멤버십")),
        Feature("샵", "차량용 상품 예시를 살펴봐요.", "SHOP", listOf("쇼핑", "현대샵", "상품", "필터")),
        Feature("설정", "홈에 표시할 기능, 등록 장소와 차주 PIN을 관리해요.", "SETTINGS",
            listOf("설정", "pin", "비밀번호", "장소", "집 등록", "차종", "동의", "생활 유형", "기록 지우기")),
    )

    fun search(query: String): List<Feature> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        fun score(f: Feature) = when {
            norm(f.title) == q -> 4
            norm(f.title).contains(q) -> 3
            f.keywords.any { norm(it) == q } -> 2
            f.keywords.any { norm(it).contains(q) || q.contains(norm(it)) } -> 1
            else -> 0
        }
        return ALL.map { it to score(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
