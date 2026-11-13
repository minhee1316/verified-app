# AAOS Car API는 기기에 있는 공유 라이브러리라 앱에 포함하지 않는다
-dontwarn android.car.**
# Pleos SDK는 Gson·kotlinx.serialization으로 모델을 주고받는데 Vehicle 모델·NaviHelper에는 보존 규칙이 없다(필드 이름이 바뀌면 값이 비어 온다)
-keep class ai.pleos.playground.** { *; }
