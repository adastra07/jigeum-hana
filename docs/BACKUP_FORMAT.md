# 지금 하나 백업 DB — 형식 1

설정 → 백업 → 파일로 내보내기에서 생성하는 독립 SQLite 3 파일이다. 베이스노트 호환 DB가 아니며 앱 내부 저장소의 파일 복사도 아니다. 현재 모델을 읽어 정규화된 전용 DB를 만든다. SQLite 도구나 AI로 구조를 읽어 별도 데이터 변환 작업을 할 수 있도록 열과 단위를 명시한다.

## 식별과 호환성

- 기본 파일명: `jigeum-hana-backup-yyyyMMdd-HHmmss.db` (기기 현지 시각).
- `PRAGMA application_id = 0x4a484e41`, `PRAGMA user_version = 1`.
- `backup_info`: `format=jigeum-hana`, `schema_version=1`, 생성 앱 버전, 생성 시각.
- 앞으로 구조가 바뀌면 형식 버전을 올리고 이전 버전의 빠진 값은 명시한 기본값으로 해석한다. 새 기능에 필요한 열·테이블은 그때 추가한다.
- 전체 DDL의 기준은 [backup-schema-v1.sql](../app/src/main/assets/backup-schema-v1.sql)이다.

## 테이블

| 테이블 | 저장 내용 및 규칙 |
|---|---|
| `backup_info` | `id=1` 한 행. `format`, `schema_version`, `app_version`, `created_at_epoch_ms` |
| `task_categories` | 목록 `id`, `name`, `position`. 기본 목록 ID는 `default`. 목록 순서대로 position=0부터 |
| `tasks` | `id`, `title`, `category_id`, `position`, `completed`, `focus_mode`, `focus_minutes`, `stopwatch_elapsed_ms`, `deleted_at_epoch_ms` |
| `calendar_categories` | `id`, `name`, `color_argb`, `position`. 미분류 ID는 `uncategorized`. 분류 순서 보존 |
| `calendar_entries` | `id`, `title`, `body`, `category_id`, `start_date`, `end_date`, `start_minute`, `end_minute`, `deleted_at_epoch_ms`, `created_at_epoch_ms`, `updated_at_epoch_ms`, `body_format` |
| `app_settings` | `id=1`. `default_focus_mode`, `font_step`, `last_minutes`, `last_stopwatch`, `wallpaper_enabled`, `wallpaper_color_argb` |
| `focus_presets` | `position` 0·1·2의 세 행과 각 `minutes`. 저장된 세 프리셋 순서 유지 |
| `focus_state` | `id=1`. `selected_task_id`, `selected_category_id`, `timer_task_id`, `title`, `mode`, `status`, `was_running`, `remaining_ms`, `elapsed_ms` |

## 값 해석

- ID는 문자열이며 동일 파일 안의 연결을 보존한다. 항목 소속은 별도 분류 테이블의 ID를 참조한다.
- `tasks.position`은 활성/휴지통을 포함한 원본 전체 배열 순서이다. 활성 목록은 해당 category_id, deleted_at_epoch_ms=0을 고르고 position순으로 읽는다. 휴지통 표시 순서는 deleted_at_epoch_ms 내림차순이다.
- 완료/설정 불리언은 정수 0 또는 1. 삭제 시각은 0이면 활성, 양수이면 휴지통이다. 복원 시 해당 값을 0으로 바꾸는 의미다. 별도 중복 휴지통 테이블은 두지 않는다.
- `focus_mode`: `unassigned`(미지정/0분), `timer`, `stopwatch`. `focus_minutes`는 원래 저장값 0~180을 보존한다. 스탑워치에서는 이 숫자를 카운트다운으로 사용하지 않는다.
- `stopwatch_elapsed_ms`는 항목별 누적 밀리초다. 현재 실행 중인 스탑워치 항목도 백업 시점까지 계산하여 기록한다.
- 날짜는 ISO `YYYY-MM-DD`, 기간의 양 끝 날짜를 모두 포함한다. 시작/종료 분은 현지 시각의 자정 이후 0~1439분이며 둘 다 NULL이면 종일이다. 여러 날 일정은 시작일의 start_minute에서 종료일의 end_minute까지다.
- `_epoch_ms`는 UTC Unix epoch 밀리초. 백업 파일명 현지 시각과 구분한다.
- `color_argb`는 Android의 부호 있는 32비트 ARGB 정수다. 베이스노트의 팔레트 번호와 다르다.
- `font_step`은 0~8, 기본은 4. `default_focus_mode`는 `last`(최근 설정), `unassigned`, `stopwatch`.
- 캘린더 본문은 현재 일반 텍스트이며 `body_format=plain`. 날짜·시간과 자유 제목을 별도 열로 보존한다.
- 실행 중인 타이머/스탑워치는 **백업 안에서만** `paused`로 기록하고 `was_running=1`로 원래 상태를 남긴다. 만료된 카운트다운은 finished다. 원본 실행은 중단하지 않는다. `remaining_ms`·`elapsed_ms`는 백업 순간의 값이며 복원 시 백업 이후 시간을 가산하거나 자동 시작하지 않는 것이 형식 1의 규칙이다. 기기 부팅 번호·elapsedRealtime·과거 종료 시각은 옮기지 않는다.
- UI 선택 ID가 더 이상 존재하지 않으면 task 참조는 NULL, 선택 목록은 기본 목록으로 기록한다.

## 생성 및 검증

시스템 문서 생성 창에서 사용자가 저장 위치·파일명을 선택한다. 추가 저장소 권한은 요청하지 않는다. 선택 완료 시 현재 데이터의 스냅샷을 읽고, 백그라운드에서 앱 임시 공간에 트랜잭션으로 DB를 생성한다. 외래키 제약, 단일 파일 journal_mode=DELETE, 닫은 후 재개방한 quick_check·foreign_key_check를 통과한 파일만 선택한 위치로 복사한다. 성공·실패를 구분해 알리고 임시 DB는 정리한다. 취소 시 앱 데이터와 실행 상태를 바꾸지 않는다.

할 일·캘린더·각 분류와 순서, 휴지통, 앱 설정, 시간 프리셋, 항목별 경과 시간과 현재 집중 상태를 포함한다. OS 알림/정확한 알람 허용 상태, 실제 시스템 배경 이미지, 배경 적용을 추적하는 기기 내부 상태는 백업 설정에 포함하지 않는다.

반복 일정·일정 알림·D-DAY·상단 고정·즐겨찾기·보관함·비밀번호 잠금·체크리스트 마감일은 사용자 결정에 따라 필드와 테이블 모두 제외했다. 일반 노트·노트북·폴더·이미지는 후속 작업이다. 베이스노트 원본 DB와 개인 데이터는 소스·테스트·APK에 포함하지 않는다.

v1.26은 백업 생성과 내보내기만 제공하며, 앱에서의 가져오기·복원 UI와 베이스노트 변환은 구현 범위에 포함하지 않는다.

### v1.30 선택적 표시 설정

형식 1의 `app_settings`에는 선택적 확장 열 `calendar_hide_uncategorized`(정수 0/1, 기본 0)를 추가한다. 기존 필수 열·ID·버전과 연결은 바꾸지 않는다. 새 앱은 이 열이 없는 이전 백업을 0(미분류 탭 표시)으로 읽는다. 이전 앱은 추가 열을 무시하여 기존 데이터를 그대로 읽을 수 있다. 새로 내보낸 백업에는 숨김 설정도 저장된다.

완료 항목 표시 설정 `app_settings.completed_tasks_last`(0/1, 기본 1), 완료 순서용 `tasks.completed_at_epoch_ms`(0 이상 정수, 기본 0)도 선택적 확장 열이다. 저장된 tasks.position은 체크 해제 시 복귀할 기준 순서이며, 완료 순서는 추가 열로 분리한다. 이전 백업에서 없는 열은 각각 1과 0으로 읽는다.

v1.31의 선택적 열: `app_settings.checklist_start_mode`(default/first/last, 기본 last), `calendar_start_mode`(같은 값, 기본 first), `last_calendar_category_id`(기본 빈 문자열=모두). 기존 백업에서 누락되면 위 기본값을 사용한다.
