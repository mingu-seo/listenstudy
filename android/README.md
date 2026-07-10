# ListenStudy Android

Phase 1 로컬 MVP Android 프로젝트 골격입니다.

- 패키지명: `com.codro.listenstudy` (임시)
- UI: Kotlin + Jetpack Compose
- TTS: Android `TextToSpeech` 온디바이스 래퍼
- 재생 상태: `PlaybackController`
- 데이터 초안: Room Entity/DAO/Database
- 백그라운드 재생 골격: Foreground Service + MediaSessionService 선언

## 빌드/검증

```bash
gradle tasks
# Android SDK와 AGP 의존성 해석이 가능한 환경에서는:
gradle test
```

현재 에이전트 환경에 Android SDK가 없으면 완전 빌드는 실패할 수 있습니다. 이 경우 문장 분리 검증 스크립트와 정적 구조 검증으로 대체합니다.
