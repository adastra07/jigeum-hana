# 지금 하나 · Jigeum Hana

체크리스트에서 지금 할 일 하나를 선택하고 집중하는 가벼운 Kotlin Android 앱.
기존 1.2 APK를 정적 분석하여 Kotlin 소스로 재구성한 첫 클라우드 버전입니다.

## 기능

- 할 일 추가·편집·삭제·완료 체크, 할 일별 1~180분 설정(기본 25분, 5/25/60분 바로 선택)
- 집중 선택·시작, 일시정지·재개·종료, 프로세스 재생성 후 종료 시각으로 복원
- 시스템 알람으로 완료 알림, 잠금화면 진행 알림과 카운트다운·조작 버튼
- 선택/시작 시 할 일을 검정 바탕의 흰 글씨로 홈·잠금 배경화면에 자동 적용, 미리보기 및 재적용
- 로컬 저장만 사용, 인터넷·계정·서버 없음

## 휴대전화에서 APK 받기

1. 저장소 **Actions → Android Debug APK**에서 최신 성공 실행을 엽니다.
2. **Artifacts → jigeum-hana-debug-실행번호** ZIP을 다운로드합니다(GitHub 로그인 필요).
3. 압축을 풀고 `app-debug.apk`를 설치합니다.
4. 처음 집중을 시작할 때 알림을 허용합니다. 절전 중 정확한 완료 알림이 필요하면 앱 **설정 → 정확한 완료 알람 허용**을 켭니다.

소스를 main에 반영하면 자동 빌드됩니다. Actions의 **Run workflow**로 수동 재빌드도 가능합니다. APK는 30일간 보관됩니다.

## 기존 설치본과 서명

패키지명 `com.adastra.focuswallpaper`, SharedPreferences `focus` 및 JSON 필드 이름을 보존했습니다.
그러나 APK에서 기존 개인 서명 키를 복구할 수 없으므로 **기존 설치본 위에 업데이트 설치하거나 데이터를 유지할 수 있다고 보장하지 않습니다.**
서명이 다르면 설치가 거부됩니다. 기존 앱을 삭제하면 목록도 삭제되므로 필요한 항목을 먼저 기록하세요.
기존 Windows 개발 환경의 debug.keystore를 나중에 확보하면 서명 일치 여부를 별도로 확인할 수 있습니다.
CI 디버그 키는 Actions 캐시로 재사용하지만 캐시가 만료/삭제되면 바뀔 수 있습니다. 배포용 안정적인 서명 관리는 후속 작업입니다.
APK와 서명 키는 저장소에 커밋하지 않습니다.

## 빌드

JDK 17, Android SDK platform 35/build-tools 35.0.0, Gradle Wrapper 8.13.
Android Gradle Plugin 8.9.2, Kotlin 2.0.21. minSdk 26, 기존과 같은 targetSdk 34.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Android Studio에서 열거나 `ANDROID_HOME`/`local.properties`에 SDK 경로를 지정합니다.
결과: `app/build/outputs/apk/debug/app-debug.apk`.

## 구조와 제한

기존처럼 기본 Android Views를 사용하며 불필요한 UI/저장 라이브러리를 추가하지 않았습니다.
작은 기존 데이터 형식과의 연속성을 위해 이번에는 Room/DataStore로 바꾸지 않았습니다.
`TimerState`는 순수 Kotlin 상태 전이, `Store`는 저장, `TimerEngine`/`TimerReceiver`는 시스템 알람과 알림, `WallpaperRenderer`는 배경화면을 담당합니다.

강제 중지하면 시스템이 알람/수신을 차단하므로 앱을 다시 열어야 복구됩니다.
정확한 알람 권한을 거부하면 완료 알림이 지연될 수 있지만 다시 열 때 남은 시간은 저장된 종료 시각으로 계산됩니다.
알림 권한 및 잠금화면 내용 표시는 사용자의 시스템 설정에 따릅니다.
재부팅 후에는 BOOT_COMPLETED 수신 시 저장된 종료 시각으로 복원합니다. 기기 시각 변경도 해당 시각 기준으로 재예약합니다.
배경화면은 자동 복원하지 않으며 선택/시작 시 덮어씁니다(기존 동작).
잠금 해제 시 별도 화면을 띄우는 기능은 포함하지 않습니다.

정적 분석 결과와 실기기 점검표: [docs/APK_ANALYSIS.md](docs/APK_ANALYSIS.md).
