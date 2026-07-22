# ListenStudy Android

이동 중 학습 자료를 들을 수 있도록 TXT 문서를 문장 단위로 재생하는 Kotlin/Jetpack Compose Android 앱입니다.

## 현재 버전

- `versionCode`: 39
- `versionName`: `0.14.0-supporter-billing`
- 패키지: `com.codro.listenstudy`
- 최소 SDK: 26
- 대상 SDK: 35

사용자 노출 앱명은 `소리노트(SoriNote)`로 확정되었으나, 런처 라벨·아이콘 등 브랜드 마감은 이후 범위(Task 9)에서 진행합니다.

## 구현 기능

- TXT 파일 선택과 UTF-8/EUC-KR 디코딩
- 한국어 규칙 기반 문장 분리
- 온디바이스 Android TTS 연속 재생
- 휴대폰 기본 TTS 엔진·음성 설정 사용
- Google Cloud TTS Standard/WaveNet 시험 기능과 문장별 로컬 캐시
- BYOK(사용자 키) 단계형 설정 마법사: 전송·비용 고지 확인 후에만 키 저장, 미리듣기, 키/캐시 분리 삭제
- 문장 하이라이트와 자동 스크롤
- 재생·일시정지, 이전·다음 문장, 문장 선택 이동
- 0.5x~3.0x 배속
- Room 기반 문서·문장·진도 영속화
- 서재 문서 목록, 이어듣기, 문서 삭제
- Foreground Service 소유 TTS/재생 세션과 StateFlow 기반 UI 상태 구독
- 알림·MediaSession·화면이 하나의 서비스 재생 세션을 제어
- sticky 재시작 시 최근 문서/위치를 일시정지 상태로 안전 복원
- 앱 정보(About) 화면: 운영 주체·버전·패키지, 데이터 처리 고지(로그인/백엔드 없음, 로컬 저장, Cloud TTS 선택 전송, 사용자 소유 키·비용, 데이터 삭제, 광고·분석 없음), 개인정보·이용안내 안내, 문의 이메일, 오픈소스 라이선스
- 2,000원 일회성 Supporter 인앱 결제(Play Billing 9.1.0): 설정 화면 후원 카드, Play 제공 현지화 상품명·가격 표시, 구매·복원·보류(pending)·오류·미지원 상태 처리, 구매 승인(acknowledge), 재설치 복원, 후원자 전용 세피아 테마(선택형) — 무료 핵심 기능·BYOK 기능은 결제 여부와 무관하게 유지

## 프로젝트 구조

```text
android/
├── app/       # Android 애플리케이션
├── logic/     # Android 비의존 재생·문장 분리 로직
├── gradle/    # Gradle Wrapper
└── gradlew
```

## 빌드 및 테스트

JDK와 Android SDK가 설정된 환경에서 실행합니다.

```bash
cd android
./gradlew clean test assembleDebug --console=plain
```

Debug APK 생성 위치:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`build/`, `.gradle/`, APK 및 로컬 설정·인증정보는 Git으로 추적하지 않습니다.

## 출시 로드맵 잔여 작업 (BYOK 무료 출시 계획 기준)

Task 1~5(작업트리 기준선, 마지막 문장 완료 크래시 수정, BYOK 키 암호화, 설정 마법사·고지, Cloud TTS 안전장치), **Task 6(앱 정보·개인정보·오픈소스 화면과 정책)**, **Task 7(2,000원 Supporter 일회성 인앱 결제)** 은 구현 완료 상태입니다. Task 7의 실결제 검증(Play Console 상품 등록, 라이선스 테스터, internal 트랙 서명 빌드)은 Task 8~12 범위의 외부 게이트로 남아 있습니다. 남은 작업은 다음과 같습니다.

- **Task 8**: Release 서명·재현 가능한 AAB 빌드와 키 백업 — 미구현
- **Task 9**: 스토어 등록 자료와 브랜드 마감(앱명/아이콘/스플래시, 그래픽 자산) — 미구현
- **Task 10**: Release 품질 게이트(`clean test lintRelease bundleRelease`, AAB SHA-256, secret scan) — 부분
- **Task 11**: Galaxy 실기기 전체 매트릭스(장시간·잠금화면·프로세스 재생성·오프라인 캐시 등) — 부분
- **Task 12**: Internal → Closed test 운영 — 미착수
- **Task 13**: Production 단계적 출시와 모니터링 — 미착수

정책 문서: `docs/05_policy/privacy-policy-ko.md`, `docs/05_policy/terms-of-use-ko.md` (2026-07-22 시행, Supporter 결제 반영). 공개 정책 페이지 `https://codro.it/apps/sorinote/privacy`·`https://codro.it/apps/sorinote/terms` 는 게시·운영 중이며(2026-07-21 접근 확인), 앱의 앱 정보 화면에서 외부 링크로 바로 열 수 있습니다.

첫 출시 범위에서는 로그인·자체 백엔드(Spring Boot)·광고·학습 통계·기기 간 동기화를 포함하지 않습니다.

## 보안

Google Cloud API 키, OAuth 비밀키, 서명 keystore, `.env`, `local.properties`는 저장소에 커밋하지 않습니다. 개발용 설정 예시는 실제 값을 제거한 예제 파일로만 관리합니다.
