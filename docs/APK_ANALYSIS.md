# 기존 APK 분석 및 재구성 근거

분석일: 2026-11-16. 입력: `jigeum-hana-debug (2).apk`, 846,440 bytes.
SHA-256: `c27b96f6747cecb63eca6657b55bb2527fcf8d47eeb868b3d8e0935c4a9bcee6`.
AndroidManifest/DEX를 Androguard와 JADX 1.5.3으로 정적 분석했습니다.
입력 APK와 디컴파일 중간 산출물은 저장소에 넣지 않았습니다.

## 확인된 사실

| 항목 | 확인 내용 |
|---|---|
| 앱 | 지금 하나, versionName 1.2, versionCode 3, debug |
| 패키지 | com.adastra.focuswallpaper |
| SDK | min 26 / target 34 / compile 35 |
| 코드 | DEX 3개, Kotlin metadata, Activity 기반 기본 Android Views |
| 앱 클래스 | MainActivity, Task, Store, TimerState, TimerEngine, TimerReceiver, WallpaperRenderer |
| 권한 | SET_WALLPAPER, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED |
| 화면 | 연한 회녹색 바탕, 짙은 녹색 집중 카드, 흰 체크리스트 카드, 편집 대화상자 |
| 할 일 | UUID id, text, done, minutes(기본 25, 1~180), 제목 최대 200자 |
| 편집 | 저장/삭제, 집중 선택/집중 시작, 시간 프리셋 5/25/60 |
| 전환 | 실행 중 다른 할 일 선택/새 시작 시 기존 타이머 종료 확인 |
| 완료 체크 | 체크만 변경, 타이머 자동 종료·자동 완료 없음 |
| 타이머 | idle/running/paused/finished, 종료 wall-clock 시각, pause 잔여 ms |
| 백그라운드 | 서비스 없이 AlarmManager RTC_WAKEUP + PendingIntent, 정확 알람 거부 시 비정확 알람 |
| 알림 | 진행 id 100, 완료 id 101, 시스템 카운트다운, pause/resume/stop action |
| 수신 | BOOT_COMPLETED, MY_PACKAGE_REPLACED, 정확 알람 권한 변경 시 재예약 |
| 배경화면 | 선택/시작 시 자동 적용, 홈/잠금 동시, 검정 배경 흰 굵은 중앙 텍스트, 긴 글은 축소 |
| 미리보기 | 두 화면 적용 버튼, 별도 스레드에서 적용 |

## 기존 저장 형식

SharedPreferences 파일 `focus`:
- `tasks`: JSON 배열, 각 객체 `id`, `text`, `done`, `minutes`.
- `selected`: 선택된 UUID.
- `status`, `end`(epoch ms), `remaining`(pause ms), `title`, `timerTaskId`.

소스 복구 대신 이 형식과 확인된 동작을 Kotlin으로 다시 작성했습니다.
원본 서명 키가 없어 기존 설치 데이터에 직접 접근하거나 업데이트 호환성을 검증하지 못했습니다.

## 복원 범위와 불확실성

MainActivity.updateFocus는 JADX에서 완전한 Java 코드로 복원되지 않았습니다.
따라서 상태별 안내 문구·시계·버튼 가시성은 확인된 모델과 주변 코드를 근거로 동등하게 재작성했습니다.
에뮬레이터/실제 기기에서 원본 앱의 화면을 실행해 비교하지는 않았으므로 픽셀 단위 복제는 아닙니다.
아이콘은 APK 리소스에서 복원했습니다. 화면 색상·크기와 배경 렌더링 수치는 코드에서 확인했습니다.

기능 확장 없이 재구성하면서 적용한 소규모 보완:
- 삭제 확인, 잘못된 JSON 방어, 시간 변경 시 알람 재예약.
- 미리보기 비트맵 크기 제한 및 배경 적용 중 중복 요청 방지.

## 기기 점검표 (미검증)

- [ ] 추가/편집/삭제/완료 후 앱 재실행 시 목록·선택 유지
- [ ] 1분 시작 → 화면 잠금/홈 이동 → 알림 카운트다운 및 완료 소리
- [ ] 앱/알림 각각에서 pause/resume/stop, 빠른 연속 조작
- [ ] 최근 앱에서 밀어 종료 / 백그라운드 프로세스 제거 후 복원
- [ ] 재부팅 전후 진행/일시정지 상태 유지, 기한 지난 타이머 완료
- [ ] 알림 거부, 정확 알람 거부/허용 후 재진입, 잠금화면 내용 숨김
- [ ] 다른 작업 전환 확인에서 취소하면 원래 타이머 유지
- [ ] 현재 작업 제목 수정 시 남은 시간 유지, 작업 삭제 시 타이머 종료
- [ ] 긴 제목·큰 글꼴·작은 화면·키보드 표시 중 대화상자 스크롤
- [ ] 홈/잠금 배경 자동 적용 및 미리보기, 적용 불가 기기 오류 안내
- [ ] 기존 APK와 새 APK의 서명 및 업데이트 설치 여부(기존 키 확보 필요)

빌드/단위 테스트/lint 통과는 위 기기 테스트의 통과를 의미하지 않습니다.
