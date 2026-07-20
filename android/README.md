# ListenStudy Android

이동 중 학습 자료를 들을 수 있도록 TXT 문서를 문장 단위로 재생하는 Kotlin/Jetpack Compose Android 앱입니다.

## 현재 버전

- `versionCode`: 37
- `versionName`: `0.12.1-first-focus`
- 패키지: `com.codro.listenstudy`
- 최소 SDK: 26
- 대상 SDK: 35

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

## 남은 핵심 작업

1. 오디오 포커스, 전화 수신, 이어폰 분리 대응
2. 잠금화면·프로세스 재생성·백그라운드 장시간 재생 실기기 검증
3. 문장별 일시정지, 수면 타이머, 북마크, 학습 통계
4. Spring Boot 백엔드 인증·진도 동기화·Cloud TTS 프록시 연동

## 보안

Google Cloud API 키, OAuth 비밀키, 서명 keystore, `.env`, `local.properties`는 저장소에 커밋하지 않습니다. 개발용 설정 예시는 실제 값을 제거한 예제 파일로만 관리합니다.
