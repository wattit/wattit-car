# 와트잇 (Watt it)

Pleos Connect(Android Automotive) 차량용 전기차 충전 도우미 앱입니다. Kotlin과 Jetpack Compose로 만든 네이티브 앱이며 WebView를 쓰지 않습니다.

## 기능

### 충전 계획 화면

- 차량에서 배터리 잔량과 배터리 용량을 읽어 서버에 충전 계획을 요청합니다. 서버는 계절·요일·시간대 요금을 따져 가장 싼 충전 구간을 계산합니다.
- 화면에 보여 주는 것
  - 언제 꽂을지 한 줄로: "오늘은 밤 10시에 꽂으세요."
  - 예약 충전 구간과 목표 충전량(80%)
  - 오늘과 내일의 시간대별 요금 그래프(좌우로 넘겨 보기). 경부하 구간, 할인 시간(있는 날), 예약 구간을 함께 표시합니다.
  - 바로 충전할 때보다 아끼는 금액
- 잔량이 이미 목표(80%) 이상이면 서버에 묻지 않고 "충전하지 않아도 돼요"를 보여 줍니다. 그래프 아래에는 앞으로 24시간 중 가장 싼 시간과 그 단가를 서버 시간별 단가에서 골라 보여 줍니다. 봄·가을 주말·공휴일에는 경부하보다 낮 할인 시간이 더 쌀 수 있어서, 경부하를 그대로 "가장 싼 시간"이라고 부르지 않습니다.
- 계획을 처음 받으면 서버가 만든 안내 문장을 Pleos TextToSpeech로 한 번 읽어 줍니다.
- 출발 시각은 다음 오전 8시를 기준으로 계산하고, 화면에 "내일 오전 8시 출발 기준"처럼 표시합니다.

### 숫자는 서버 계산값

시각, 금액, 잔량 %, 단가는 모두 서버가 계산하거나 차량이 알려 준 값을 그대로 표시합니다. 앱은 요금을 계산하지 않습니다. 값을 읽지 못하면 기본값이나 예시 숫자를 쓰지 않고, 무엇을 읽지 못했는지와 "다시 시도" 버튼을 보여 줍니다.

- 차량 잔량을 읽지 못함: "차량 정보를 읽지 못했어요."
- 배터리 용량이 0이거나 없음: 차량 칩에 "용량 확인 필요"를 표시하고 서버에 요청하지 않습니다.
- 서버에 충전 계획 기능이 아직 없음(404): "서버 준비 중이에요."
- 연결 실패, 시간 초과(연결 8초, 응답 15초), 서버 오류: 각각 다른 안내와 "다시 시도"

## 개발 환경

- 에뮬레이터: Pleos Connect 2.1.0 시스템 이미지 (Android 14, API 34)
- minSdk 28, targetSdk 34, compileSdk 34
- Gradle 8.11.1 (wrapper 포함), Android Gradle Plugin 8.7.3, Kotlin 2.0.21, JDK 17

## 화면 규격

Pleos 문서 [폼 팩터 테스트 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide)의 FormFactorConsole 가상 디스플레이를 기준으로 맞췄습니다. 아래 dp·DPI·px 값은 그 문서의 화면 캡처에 적힌 값입니다.

- **기준: 가로형 차량 디스플레이 (14.6인치 Landscape 폼팩터)**
  - 프리셋 "Connect-S · 14.6" · 200 DPI · 2048 × 1152", 테스트 화면 크기 1360 × 948 dp (200 DPI, 1700 × 1185 px)
  - 충전 계획과 요금 그래프를 좌우로 나란히 놓습니다.
- **세로 (12.9인치 Portrait 폼팩터)**
  - 프리셋 "Connect-S · 12.9" · 160 DPI · 1920 × 1080", 테스트 화면 크기 628 × 855 dp (160 DPI, 628 × 855 px)
  - 충전 계획을 위에, 요금 그래프를 아래에 쌓고 세로로 스크롤합니다. 상단 칩 4개는 제목 아래 한 줄에 같은 폭으로 놓습니다.
- **배치 기준:** 화면 폭이 1000 dp 이상이고 가로가 세로보다 길면 좌우 배치, 그 밖에는 위아래 배치입니다(`WideLayoutMinWidth`). 앱은 방향을 고정하지 않습니다.
- **렌더 확인:** 에뮬레이터가 없는 빌드 서버에서 Paparazzi(layoutlib)로 위 두 크기를 그대로 그려 확인했습니다. 가로 1700 × 1185 px @200 DPI, 세로 628 × 855 px @160 DPI입니다.
- **확인 필요:** 에뮬레이터 기본 화면(가상 디스플레이를 만들기 전 메인 디스플레이)의 해상도와 DPI는 문서에서 찾지 못했습니다. 실제 에뮬레이터에서 FormFactorConsole로 두 폼팩터를 띄워 본 적도 아직 없습니다.

## 사용 SDK와 권한

| SDK / API | 용도 | 권한 |
| --- | --- | --- |
| Pleos Vehicle 2.3.2 `EvBattery.getEvBatteryStateOfCharge` | 배터리 잔량(%) | `pleos.car.permission.CAR_ENERGY` |
| Android Automotive `CarPropertyManager` `INFO_EV_BATTERY_CAPACITY` | 배터리 용량(Wh) | `android.car.permission.CAR_INFO` |
| Android Automotive `CarPropertyManager` `INFO_MODEL` | 차량 칩에 표시할 차종 이름 | `android.car.permission.CAR_INFO` |
| Pleos TextToSpeech 3.3.1 (`Mode.ON_DEVICE`) | 계획 안내 문장 읽기. 앱 등록이 필요 없는 온디바이스 모드 | 없음 |
| 서버 API | 충전 계획(`POST /api/car/plan`), 시간대별 요금(`GET /api/timeline`) | `android.permission.INTERNET` |

Pleos Vehicle의 배터리 용량 함수(`getEvBatteryCapacity`)는 SDK 소스에서 `@hide`이고 폐기 예정으로 표시되어 있습니다. 그래서 SDK 주석이 안내하는 Android 표준 속성(`INFO_EV_BATTERY_CAPACITY`)을 씁니다. 숨김 처리되었거나 폐기 예정인 Pleos API는 쓰지 않습니다.

Pleos SDK는 공개 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 받습니다(`settings.gradle.kts`).

## 빌드

```bash
./gradlew assembleDebug testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### 서버 주소 설정

서버 주소는 빌드할 때 `BuildConfig.API_BASE_URL`로 들어가며, 기본값은 운영 서버입니다. 다른 서버를 쓰려면 빌드 때 지정합니다.

```bash
./gradlew assembleDebug -PwattitApiBaseUrl=https://example.test
```

Android는 기본 설정에서 `http://` 주소를 막으므로 HTTPS 주소를 지정해 주세요.

앱과 저장소에는 비밀값(API 키, 토큰)이 들어 있지 않습니다.

## 구조

- `app/src/main/java/app/wattit/car/car`: 차량 값(잔량, 용량, 차종)과 TTS
- `data`: 서버 요청과 응답 모델
- `domain`: 화면 상태, 한국어 시각 표기("23:00" → "밤 11시"), 요금 그래프 라벨
- `ui`: Compose 화면

단위 테스트(`app/src/test`)는 순수 Kotlin입니다. 응답 해석, 시각 표기, 오류 상태를 확인하고, 차량 값이 없을 때 서버에 요청을 보내지 않는지도 확인합니다.

## 글꼴

Pretendard(SIL Open Font License 1.1)를 앱에 포함했습니다. 라이선스 전문은 `third_party/pretendard/LICENSE.txt`에 있습니다.
