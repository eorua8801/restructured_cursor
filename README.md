# Eyedid SDK 안드로이드 앱 개발 가이드

## 목차
1. [프로젝트 소개](#1-프로젝트-소개)
2. [설치 및 설정](#2-설치-및-설정)
3. [앱 구조 개요](#3-앱-구조-개요)
4. [주요 컴포넌트 상세 설명](#4-주요-컴포넌트-상세-설명)
5. [설정 파라미터 가이드](#5-설정-파라미터-가이드)
6. [기능 수정 및 확장 가이드](#6-기능-수정-및-확장-가이드)
7. [문제 해결 및 디버깅](#7-문제-해결-및-디버깅)
8. [향후 개발 로드맵](#8-향후-개발-로드맵)
9. [참고 자료](#9-참고-자료)

---

## 1. 프로젝트 소개

### 1.1 개요
본 프로젝트는 Eyedid(이전 SeeSo) SDK를 활용한 안드로이드 시선 추적 애플리케이션입니다. 사용자가 손으로 기기를 조작하지 않고도 시선만으로 스마트폰을 제어할 수 있습니다. 주요 기능으로는 시선 고정 클릭, 화면 가장자리 스크롤 등이 있으며, 시선 추적 성능 향상을 위한 다양한 최적화 기법이 적용되어 있습니다.

### 1.2 주요 기능
- **시선 고정 클릭**: 특정 위치를 일정 시간 응시하면 해당 위치를 클릭
- **화면 가장자리 스크롤**: 화면 상단 또는 하단을 일정 시간 응시하면 자동 스크롤
- **캘리브레이션**: 사용자 시선 정확도 향상을 위한 보정 기능
- **설정 화면**: 사용자별 최적화를 위한 다양한 파라미터 조정 기능
- **시각적 피드백**: 시선 위치, 진행 상태 등을 표시하는 커서 및 UI

### 1.3 아키텍처 특징
- **모듈화된 구조**: 역할별로 분리된 컴포넌트 구조로 확장성 및 유지보수성 향상
- **다중 레이어 아키텍처**: 데이터, 도메인, UI 레이어 분리를 통한 관심사 분리
- **설정 관리**: 사용자 설정을 효율적으로 관리하는 저장소 패턴 적용
- **서비스 기반 구현**: 백그라운드에서도 지속적인 시선 추적이 가능한 서비스 구조

---

## 2. 설치 및 설정

### 2.1 시스템 요구사항
- Android 10.0 (API 레벨 29) 이상
- 전면 카메라가 있는 안드로이드 기기
- Android Studio Arctic Fox (2020.3.1) 이상

### 2.2 필요 권한
- `CAMERA`: 시선 추적을 위한 카메라 사용
- `SYSTEM_ALERT_WINDOW`: 다른 앱 위에 오버레이 표시
- `BIND_ACCESSIBILITY_SERVICE`: 시스템 제어(클릭, 스크롤) 기능
- `FOREGROUND_SERVICE`: 백그라운드 실행을 위한 포그라운드 서비스

### 2.3 프로젝트 설정 방법

1. **저장소 클론하기**
   ```bash
   git clone https://github.com/eorua8801/neweyecursor.git
   cd neweyecursor
   ```

2. **Android Studio에서 프로젝트 열기**
   - Android Studio 실행 > Open an existing Android Studio project > 프로젝트 폴더 선택

3. **Eyedid SDK 키 설정**
   - `EyedidTrackingRepository.java` 파일에서 LICENSE_KEY 값을 본인의 라이센스 키로 변경:
   ```java
   private static final String LICENSE_KEY = "your_license_key_here";
   ```

4. **빌드 및 실행**
   - Android Studio에서 'Run' 버튼 클릭 또는 `Shift+F10` 단축키 사용

### 2.4 앱 설정 방법 (최초 실행 시)

1. **오버레이 권한 허용**
   - 앱 최초 실행 시 '다른 앱 위에 표시' 권한 요청 대화상자가 나타납니다.
   - '허용'을 선택하여 권한을 부여합니다.

2. **접근성 서비스 활성화**
   - 앱 실행 후 접근성 서비스 설정 화면으로 이동합니다.
   - 목록에서 'EyedidSampleApp'을 찾아 활성화합니다.

3. **카메라 권한 허용**
   - 앱 최초 실행 시 카메라 접근 권한을 허용합니다.

---

## 3. 앱 구조 개요

### 3.1 프로젝트 디렉토리 구조
```
app/src/main/java/camp/visual/android/sdk/sample/
├── data/                           # 데이터 레이어
│   ├── repository/                 # 데이터 접근 저장소
│   │   ├── EyeTrackingRepository.java    # 시선 추적 저장소 인터페이스
│   │   └── EyedidTrackingRepository.java # Eyedid SDK 구현체
│   └── settings/                   # 설정 관리
│       ├── SettingsRepository.java       # 설정 저장소 인터페이스
│       └── SharedPrefsSettingsRepository.java # SharedPreference 구현체
├── domain/                         # 비즈니스 로직 레이어
│   ├── model/                      # 도메인 모델
│   │   ├── BlinkData.java               # 눈 깜빡임 데이터 모델 (미구현)
│   │   └── UserSettings.java            # 사용자 설정 모델
│   └── interaction/                # 상호작용 로직
│       ├── ClickDetector.java           # 시선 고정 클릭 감지기
│       └── EdgeScrollDetector.java      # 가장자리 스크롤 감지기
├── service/                        # 안드로이드 서비스
│   ├── tracking/                   # 시선 추적 서비스
│   │   └── GazeTrackingService.java     # 핵심 시선 추적 서비스
│   └── accessibility/              # 접근성 서비스
│       └── MyAccessibilityService.java  # 시스템 제어 서비스
├── ui/                             # 프레젠테이션 레이어
│   ├── main/                       # 메인 화면
│   │   └── MainActivity.java            # 메인 액티비티
│   ├── settings/                   # 설정 화면
│   │   └── SettingsActivity.java        # 설정 액티비티
│   └── views/                      # 커스텀 뷰
│       ├── CalibrationViewer.java       # 캘리브레이션 화면
│       ├── OverlayCursorView.java       # 시선 커서 오버레이
│       └── PointView.java               # 시선 포인트 표시 뷰
└── AndroidManifest.xml             # 앱 매니페스트
```

### 3.2 주요 파일 설명

#### 3.2.1 데이터 레이어
- **EyeTrackingRepository.java**: 시선 추적 기능에 대한 인터페이스 정의
- **EyedidTrackingRepository.java**: Eyedid SDK를 사용하여 구현한 시선 추적 저장소
- **SettingsRepository.java**: 사용자 설정 관리 인터페이스
- **SharedPrefsSettingsRepository.java**: SharedPreferences를 사용한 설정 저장소 구현

#### 3.2.2 도메인 레이어
- **UserSettings.java**: 사용자 설정 정보를 담는 데이터 클래스 (빌더 패턴 적용)
- **ClickDetector.java**: 시선 고정을 통한 클릭 감지 로직 구현
- **EdgeScrollDetector.java**: 화면 가장자리 응시를 통한 스크롤 감지 로직 구현

#### 3.2.3 서비스 레이어
- **GazeTrackingService.java**: 시선 추적 핵심 서비스, SDK와의 통합 및 상호작용 처리
- **MyAccessibilityService.java**: 접근성 서비스를 통한 클릭, 스크롤 등 시스템 제어 기능 구현

#### 3.2.4 UI 레이어
- **MainActivity.java**: 앱 시작점, 권한 요청 및 서비스 시작 처리
- **SettingsActivity.java**: 사용자 설정 화면 구현
- **CalibrationViewer.java**: 캘리브레이션 화면 및 로직 구현
- **OverlayCursorView.java**: 시선 위치 표시 및 진행 상태 표시 오버레이
- **PointView.java**: 시선 포인트 표시용 커스텀 뷰

---

## 4. 주요 컴포넌트 상세 설명

### 4.1 GazeTrackingService

이 서비스는 앱의 핵심으로, Eyedid SDK를 이용한 시선 추적, 시선 데이터 처리, 제스처 감지 등을 담당합니다.

#### 주요 역할
1. Eyedid SDK 초기화 및 시선 추적 시작/중지
2. 시선 데이터 필터링 및 처리
3. 시선 기반 제스처 감지 및 이벤트 처리
4. 오버레이 UI 관리 (커서, 캘리브레이션)
5. 진동 피드백 제공

#### 주요 메서드
- **onCreate()**: 서비스 초기화, 컴포넌트 설정
- **initGazeTracker()**: Eyedid SDK 초기화
- **trackingCallback.onMetrics()**: 시선 데이터 수신 및 처리 (핵심 로직)
- **scrollUp(), scrollDown()**: 스크롤 기능 구현
- **performClick()**: 클릭 동작 실행
- **resetAll()**: 상태 초기화
- **triggerCalibration()**: 캘리브레이션 시작

#### 핵심 코드 분석: trackingCallback.onMetrics()
```java
@Override
public void onMetrics(long timestamp, GazeInfo gazeInfo, FaceInfo faceInfo, BlinkInfo blinkInfo, UserStatusInfo userStatusInfo) {
    // 시선 데이터 성공적으로 수신된 경우
    if (gazeInfo.trackingState == TrackingState.SUCCESS) {
        // 원-유로 필터링 적용 (시선 데이터 안정화)
        // 엣지 스크롤 감지
        // 고정 클릭 감지
        // ...
    }
}
```

### 4.2 EdgeScrollDetector

화면 가장자리 응시를 감지하여 스크롤 동작을 트리거하는 클래스입니다.

#### 주요 역할
1. 상단/하단 가장자리 응시 감지
2. 응시 지속 시간 측정 및 피드백 제공
3. 스크롤 동작 트리거

#### 주요 메서드
- **update()**: 현재 시선 위치가 가장자리인지 확인
- **processTopEdge()**: 상단 가장자리 응시 처리
- **processBottomEdge()**: 하단 가장자리 응시 처리
- **getEdgeStateText()**: 현재 응시 상태에 대한 텍스트 반환

#### 핵심 변수 설명
- **EDGE_THRESHOLD_FRAMES**: 연속 프레임 감지 임계값 (기본값: 5)
- **edgeMarginRatio**: 가장자리 인식 영역 비율 (기본값: 0.01, 화면 높이의 1%)
- **edgeTriggerMs**: 스크롤 트리거까지의 응시 시간 (기본값: 3000ms)

### 4.3 ClickDetector

특정 영역을 일정 시간 응시하면 클릭으로 인식하는 클래스입니다.

#### 주요 역할
1. 특정 영역 응시 감지
2. 응시 지속 시간 측정
3. 클릭 동작 트리거

#### 주요 메서드
- **update()**: 현재 시선 위치 업데이트 및 클릭 판단
- **getProgress()**: 현재 응시 진행도 반환 (0.0-1.0)
- **reset()**: 상태 초기화

#### 핵심 변수 설명
- **aoiRadius**: 관심 영역(Area of Interest) 반경 (기본값: 40 픽셀)
- **fixationDurationMs**: 클릭으로 인식할 응시 시간 (기본값: 1000ms)

### 4.4 MyAccessibilityService

시스템 제어(클릭, 스크롤 등)를 담당하는 접근성 서비스입니다.

#### 주요 역할
1. 화면 어디서나 클릭 동작 실행
2. 다양한 스크롤 동작 지원 (방향, 크기, 연속 스크롤)

#### 주요 메서드
- **performClickAt()**: 지정 위치 클릭 실행
- **performScroll()**: 단일 스크롤 실행
- **performContinuousScroll()**: 연속 스크롤 실행

#### 핵심 변수 설명
- **SCROLL_AMOUNT_SMALL/MEDIUM/LARGE**: 스크롤 이동 거리 (화면 높이 대비 비율)
- **Direction**: 스크롤 방향 열거형 (UP, DOWN)
- **ScrollAmount**: 스크롤 양 열거형 (SMALL, MEDIUM, LARGE)

### 4.5 SettingsActivity

사용자 설정을 관리하는 화면입니다.

#### 주요 역할
1. 설정 파라미터 UI 제공
2. 설정값 저장 및 로드
3. 설정 변경 즉시 반영

#### 주요 메서드
- **initViews()**: 설정 UI 요소 초기화
- **loadSettings()**: 저장된 설정값 로드
- **saveSettings()**: 설정값 저장
- **updateXxxText()**: 설정값 변경 시 텍스트 업데이트

### 4.6 UserSettings

사용자 설정 정보를 담는 모델 클래스입니다. 빌더 패턴을 사용하여 구현되었습니다.

#### 주요 속성
- **fixationDurationMs**: 고정 클릭 인식 시간
- **aoiRadius**: 관심 영역 반경
- **scrollEnabled**: 스크롤 기능 활성화 여부
- **edgeMarginRatio**: 가장자리 인식 영역 비율
- **edgeTriggerMs**: 스크롤 트리거 응시 시간
- **continuousScrollCount**: 연속 스크롤 횟수
- **clickEnabled**: 클릭 기능 활성화 여부
- **edgeScrollEnabled**: 가장자리 스크롤 활성화 여부
- **blinkDetectionEnabled**: 눈 깜빡임 감지 활성화 여부 (미구현)

#### 사용 예시
```java
// 기본 설정으로 생성
UserSettings defaultSettings = new UserSettings.Builder().build();

// 사용자 지정 설정 생성
UserSettings customSettings = new UserSettings.Builder()
        .fixationDurationMs(800f)  // 더 빠른 클릭 인식
        .aoiRadius(50f)           // 더 넓은 관심 영역
        .edgeTriggerMs(2000)      // 더 빠른 스크롤 트리거
        .build();
```

---

## 5. 설정 파라미터 가이드

### 5.1 고정 클릭 설정

| 파라미터 | 설명 | 기본값 | 권장 범위 | 영향 |
|---------|------|--------|----------|------|
| fixationDurationMs | 클릭 인식 시간(ms) | 1000 | 500-2000 | 값이 작을수록 빠르게 인식되지만 오탐지 가능성 증가 |
| aoiRadius | 관심 영역 반경(px) | 40 | 20-70 | 값이 클수록 넓은 영역에서 클릭 인식, 정밀도 감소 |
| clickEnabled | 클릭 기능 활성화 | true | - | 기능 자체의 활성화/비활성화 |

#### 수정 방법
```java
// SettingsActivity.java에서 슬라이더 범위 조정
fixationDurationBar.setMax(30);  // 300-3000ms 범위(×100ms)
aoiRadiusBar.setMax(60);         // 10-70px 범위

// 기본값 변경 시 UserSettings.Builder 클래스 수정
private float fixationDurationMs = 800f; // 기본값을 800ms로 변경
```

### 5.2 스크롤 설정

| 파라미터 | 설명 | 기본값 | 권장 범위 | 영향 |
|---------|------|--------|----------|------|
| edgeMarginRatio | 가장자리 인식 영역 비율 | 0.01 | 0.005-0.05 | 값이 클수록 넓은 영역에서 가장자리 인식 |
| edgeTriggerMs | 스크롤 트리거 시간(ms) | 3000 | 1000-5000 | 값이 작을수록 빠르게 스크롤 트리거 |
| continuousScrollCount | 연속 스크롤 횟수 | 2 | 1-5 | 한 번 트리거 시 연속 실행될 스크롤 횟수 |
| scrollEnabled | 스크롤 기능 활성화 | true | - | 스크롤 기능 자체의 활성화/비활성화 |
| edgeScrollEnabled | 가장자리 스크롤 활성화 | true | - | 가장자리 스크롤 기능의 활성화/비활성화 |

#### 수정 방법
```java
// MyAccessibilityService.java에서 스크롤 거리 조정
private static final float SCROLL_AMOUNT_SMALL = 0.1f;  // 화면 높이의 10%로 변경
private static final float SCROLL_AMOUNT_MEDIUM = 0.15f; // 화면 높이의 15%로 변경
private static final float SCROLL_AMOUNT_LARGE = 0.25f;  // 화면 높이의 25%로 변경

// EdgeScrollDetector.java에서 응시 진입 임계값 조정
private static final int EDGE_THRESHOLD_FRAMES = 3; // 더 빠른 가장자리 인식을 위해 3으로 감소
```

### 5.3 UI 및 피드백 설정

| 파라미터 | 설명 | 위치 | 수정 방법 |
|---------|------|------|----------|
| 커서 색상 | 시선 커서 색상 | OverlayCursorView.java | circlePaint.setColor() 수정 |
| 커서 크기 | 시선 커서 반지름 | OverlayCursorView.java | radius 값 수정 |
| 진동 패턴 | 진동 피드백 패턴 | GazeTrackingService.java | vibrator.vibrate() 호출 수정 |
| 진행 표시 색상 | 클릭 진행 게이지 색상 | OverlayCursorView.java | progressPaint.setColor() 수정 |

#### 수정 예시
```java
// OverlayCursorView.java에서 커서 색상 및 크기 변경
circlePaint.setColor(Color.rgb(0x00, 0xaa, 0xff));  // 파란색으로 변경
radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, getResources().getDisplayMetrics()); // 10dp로 변경

// GazeTrackingService.java에서 진동 패턴 변경
vibrator.vibrate(200); // 진동 시간을 200ms로 변경
```

---

## 6. 기능 수정 및 확장 가이드

### 6.1 고급 사용 테크닉: 눈 감기를 통한 정밀 클릭

개발 과정에서 발견된 흥미로운 사용 테크닉으로, 눈을 감아 커서를 고정하는 방법이 있습니다. 이 방법은 정밀한 클릭이 필요한 상황에서 매우 유용합니다.

#### 작동 원리
- 사용자가 눈을 감으면 시선 추적이 일시적으로 중단됩니다.
- 시선 추적이 중단되면 마지막으로 감지된 위치에 커서가 고정됩니다.
- 고정된 커서 위치에서 시간이 경과하면 클릭이 발생합니다.
- 클릭이 발생할 때 진동 피드백이 제공되므로, 사용자는 눈을 감고 있어도 클릭 여부를 감지할 수 있습니다.

#### 사용 방법
1. 시선을 원하는 클릭 위치로 이동시킵니다.
2. 커서가 원하는 위치에 도달하면 눈을 감습니다.
3. 고정 클릭 시간(기본 1초)동안 눈을 감은 상태를 유지합니다.
4. 진동 피드백이 느껴지면 눈을 뜹니다.

#### 개발자 팁
이 기능을 의도적으로 지원하거나 개선하려면:

```java
// 눈 감김 감지 로직 추가
if (userStatusInfo.leftOpenness < 0.2 && userStatusInfo.rightOpenness < 0.2) {
    // 눈을 감았을 때 커서 위치 유지 (마지막 유효한 위치 사용)
    // 현재 위치를 업데이트하지 않음
    
    // 선택적: "눈 감김 모드" UI 표시
    overlayCursorView.setEyeClosedMode(true);
} else {
    // 정상 시선 추적
    overlayCursorView.updatePosition(safeX, safeY);
    overlayCursorView.setEyeClosedMode(false);
}
```

이 테크닉을 더 발전시켜 "정밀 모드" 같은 명시적 기능으로 구현할 수도 있습니다. 예를 들어, 눈을 감았을 때 커서 주변에 "정밀 모드 활성화" 같은 시각적 표시를 추가하는 것이 가능합니다.

### 6.2 새로운 제스처 추가하기

새로운 시선 기반 제스처를 추가하려면 다음 단계를 따르세요:

1. **제스처 감지기 클래스 생성**
   - `domain/interaction` 패키지에 새 감지기 클래스 생성
   - 기존 `ClickDetector` 또는 `EdgeScrollDetector`를 참고

   ```java
   public class NewGestureDetector {
       private final UserSettings settings;
       
       public NewGestureDetector(UserSettings settings) {
           this.settings = settings;
       }
       
       public boolean update(float x, float y) {
           // 제스처 감지 로직 구현
           return gestureDetected;
       }
       
       public void reset() {
           // 상태 초기화
       }
   }
   ```

2. **GazeTrackingService에 통합**
   - `GazeTrackingService` 클래스에 감지기 인스턴스 추가
   - `initDetectors()` 메서드에서 초기화
   - `onMetrics()` 콜백에서 제스처 감지 로직 호출

   ```java
   // 클래스 변수 추가
   private NewGestureDetector newGestureDetector;
   
   // 초기화
   private void initDetectors() {
       // 기존 코드...
       newGestureDetector = new NewGestureDetector(userSettings);
   }
   
   // onMetrics 콜백에서 호출
   if (newGestureDetector.update(safeX, safeY)) {
       // 제스처 감지 시 동작 수행
       performNewGestureAction();
   }
   ```

3. **설정 파라미터 추가 (선택 사항)**
   - `UserSettings` 클래스에 새 설정 파라미터 추가
   - `SettingsActivity`에 UI 요소 추가
   - `SharedPrefsSettingsRepository`에 저장/로드 로직 추가

### 6.2 필터링 최적화하기

시선 추적 데이터 필터링을 최적화하려면 다음을 조정하세요:

1. **원-유로 필터 파라미터 조정**
   - `GazeTrackingService` 클래스의 다음 부분을 찾습니다:
   ```java
   oneEuroFilterManager = new OneEuroFilterManager(2);
   ```
   
   - 필터 파라미터를 조정합니다 (예: minCutoff, beta):
   ```java
   oneEuroFilterManager = new OneEuroFilterManager(2);
   oneEuroFilterManager.setMinCutoff(0.5f);  // 기본값보다 낮게 설정하면 더 부드러움
   oneEuroFilterManager.setBeta(0.05f);      // 낮은 beta는 속도 변화에 덜 민감
   ```

2. **추가 평활화 적용 (선택 사항)**
   - 이동 평균 필터와 같은 추가 필터링 적용:
   ```java
   // 이동 평균 필터 (마지막 N개 샘플의 평균)
   private final int WINDOW_SIZE = 5;
   private float[] xHistory = new float[WINDOW_SIZE];
   private float[] yHistory = new float[WINDOW_SIZE];
   private int historyIndex = 0;
   
   private float[] applyMovingAverage(float x, float y) {
       xHistory[historyIndex] = x;
       yHistory[historyIndex] = y;
       historyIndex = (historyIndex + 1) % WINDOW_SIZE;
       
       float sumX = 0, sumY = 0;
       for (int i = 0; i < WINDOW_SIZE; i++) {
           sumX += xHistory[i];
           sumY += yHistory[i];
       }
       
       return new float[] { sumX / WINDOW_SIZE, sumY / WINDOW_SIZE };
   }
   ```

### 6.3 눈 깜빡임 감지 기능 구현하기

Eyedid SDK의 BlinkInfo 클래스를 활용하여 눈 깜빡임 감지 기능을 구현하려면:

1. **SDK 문서 확인**
   - Eyedid SDK 문서에서 BlinkInfo 클래스 속성 및 메서드 확인
   - 현재 주석 처리된 코드 대신 실제 SDK 구현에 맞는 코드 작성

2. **BlinkDetector.java 구현 예시**
   ```java
   public class BlinkDetector {
       // 클래스 구현...
       
       public BlinkData update(BlinkInfo blinkInfo) {
           // SDK 문서 확인 후 올바른 메서드 사용
           boolean isBlinking = blinkInfo.isBlink(); // 가정: SDK가 이런 메서드 제공
           
           // 나머지 로직 구현...
           return new BlinkData(isBlinking, duration, timestamp);
       }
   }
   ```

3. **GazeTrackingService에 통합**
   - 주석 처리된 코드 활성화 및 수정

### 6.4 새로운 UI 컴포넌트 추가하기

새로운 UI 컴포넌트를 추가하려면:

1. **커스텀 뷰 클래스 생성**
   - `ui/views` 패키지에 새 클래스 생성

2. **GazeTrackingService에 통합**
   - `initViews()` 메서드에서 뷰 초기화 및 윈도우에 추가
   ```java
   private void initViews() {
       // 기존 코드...
       
       // 새 커스텀 뷰 초기화
       myNewView = new MyNewView(this);
       WindowManager.LayoutParams params = new WindowManager.LayoutParams(
               // 레이아웃 파라미터 설정
       );
       windowManager.addView(myNewView, params);
   }
   ```

---

## 7. 문제 해결 및 디버깅

### 7.1 일반적인 문제 및 해결 방법

| 문제 | 가능한 원인 | 해결 방법 |
|-----|-----------|---------|
| 시선 추적이 시작되지 않음 | SDK 라이센스 키 오류 | EyedidTrackingRepository.java의 LICENSE_KEY 확인 |
| 시선 커서가 떨림 | 필터링 부족 | OneEuroFilterManager 파라미터 조정 |
| 클릭이 실행되지 않음 | 접근성 서비스 비활성화 | 접근성 설정에서 앱 활성화 확인 |
| 앱 충돌 발생 | 권한 문제 | Logcat에서 오류 확인 및 권한 설정 확인 |
| 커서와 실제 시선 위치 불일치 | 캘리브레이션 필요 | 캘리브레이션 실행 |

### 7.2 로그 활용 가이드

각 클래스에는 로깅 코드가 포함되어 있습니다. `TAG` 필터를 사용하여 특정 컴포넌트의 로그만 확인할 수 있습니다.

```
// 로그캣에서 다음 태그 필터 사용
GazeTrackingService
ClickDetector
EdgeScrollDetector
MyAccessibilityService
```

### 7.3 성능 프로파일링

앱의 성능을 모니터링하려면:

1. Android Studio의 CPU Profiler 사용
2. 각 메서드에 성능 측정 로그 추가:
   ```java
   long startTime = System.currentTimeMillis();
   // 측정할 코드
   long endTime = System.currentTimeMillis();
   Log.d(TAG, "Method execution time: " + (endTime - startTime) + "ms");
   ```

---

## 8. 향후 개발 로드맵

### 8.1 개선 계획
- **눈 깜빡임 감지 구현**: 현재 주석 처리된 코드 완성
- **다중 제스처 지원**: 새로운 시선 제스처 추가
- **머신러닝 기반 필터링**: 사용자별 움직임 패턴 학습 및 적용
- **다크 모드 지원**: 시각적 요소의 다크 모드 대응

### 8.2 기술 부채 목록
- **SDK 버전 업데이트**: 최신 Eyedid SDK 대응
- **클린 아키텍처 완성**: 일부 클래스의 직접 참조 제거
- **단위 테스트 추가**: 핵심 로직에 대한 테스트 케이스 작성

---

## 9. 참고 자료

### 9.1 Eyedid SDK 문서
- [SDK 개요](https://docs.eyedid.ai/docs/document/eyedid-sdk-overview)
- [안드로이드 퀵 스타트 가이드](https://docs.eyedid.ai/docs/quick-start/android-quick-start)
- [API 문서](https://docs.eyedid.ai/docs/api/android-api-docs/)
- [캘리브레이션 가이드](https://docs.eyedid.ai/docs/document/calibration-overview)

### 9.2 샘플 프로젝트
- [공식 샘플 앱](https://github.com/visualcamp/eyedid-android-sample)
- [Flutter SDK](https://github.com/visualcamp/eyedid-flutter-sdk)

### 9.3 안드로이드 개발 관련
- [안드로이드 접근성 서비스 가이드](https://developer.android.com/guide/topics/ui/accessibility/service)
- [시스템 오버레이 가이드](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)

