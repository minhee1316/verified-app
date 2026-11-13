plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.verified.incar"
    compileSdk = 36
    // AAOS CarPropertyManager (android.car.jar, SDK optional library)
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.verified.incar"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "DEMO", "false")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // shortcut: 공모전 제출용 APK는 debug 키로 서명, 마켓 등록 시 정식 키로 교체
            signingConfig = signingConfigs.getByName("debug")
        }
        // PT·성능 측정용: 제출본처럼 R8·비디버그로 만들고 시뮬레이터만 연다(assembleDemo). release 뒤에 둬야 그 설정을 복사한다
        create("demo") {
            initWith(getByName("release"))
            buildConfigField("boolean", "DEMO", "true")
            versionNameSuffix = "-demo"
            // 3D(Filament) 네이티브 라이브러리: 휴대폰·차(arm64)와 AAOS 에뮬레이터(x86_64)만 넣어 APK 크기를 줄인다
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // 마이현대 앱처럼 선 아이콘을 쓰기 위해(release에서는 R8이 안 쓰는 아이콘을 지운다)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("com.google.zxing:core:3.5.3")
    // 현대 Pleos 차 신호 읽기(문·창문·좌석·경고·주행거리·배터리)와 현대 내비 목적지·남은 거리. Pleos 서비스가 없는 차·폰에서는 쓰지 않는다
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // 체험·시연용 3D 차 모델(.glb) 360도 보기. 모델 파일은 src/demo/assets에만 있어 다른 빌드에는 들어가지 않는다
    implementation("com.google.android.filament:filament-android:1.75.0")
    implementation("com.google.android.filament:gltfio-android:1.75.0")
    implementation("com.google.android.filament:filament-utils-android:1.75.0")
    testImplementation("junit:junit:4.13.2")
    // 화면 검증: PC JVM에서 실제 Compose 화면을 그려 PNG로 남긴다
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
