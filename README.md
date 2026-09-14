# 볼프 (Volp)

여행 경비를 미리 예측하고, 실제로 쓴 돈을 기록하는 안드로이드 앱.

## 무엇을 하는 앱인가

- 목적지·기간·인원·여행 스타일로 **항목별 예상 경비**를 계산한다.
- 실제 지출을 입력하고 **예측 대비 실제**를 항목별로 비교한다.
- 카드사 결제 문자와 알림을 읽어 **지출을 자동으로 모은다**(삼성·신한·하나, 국내/해외).
- 기록을 **Google Drive의 `볼프` 폴더**에 백업한다.
- 새 빌드가 올라오면 앱이 알려 주고, 확인을 받아 **직접 내려받아 설치**한다.

## 빌드

GitHub Actions(`.github/workflows/android-release.yml`)가 푸시마다 릴리스 APK를 만들어
GitHub 릴리스로 올린다. 태그는 `v<versionName>` 형식이고 마지막 자리가 버전 코드다.

### 필요한 저장소 시크릿

| 이름 | 설명 |
| --- | --- |
| `KEYSTORE_BASE64` | 릴리스 서명 키스토어(.jks)를 base64로 인코딩한 값 |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 |
| `KEY_PASSWORD` | 키 비밀번호 |

시크릿이 없으면 디버그 키로 서명되며, 기존 설치본 위에 업데이트되지 않는다.

### 로컬 빌드

```bash
./gradlew assembleDebug
```

릴리스 키로 로컬에서 빌드하려면 `keystore.properties`에 `storeFile`, `storePassword`,
`keyAlias`, `keyPassword`를 적는다. 이 파일은 저장소에 올리지 않는다.

## 구조

```
domain/      순수 코틀린. 예산 계산, 카드 문자 파서, 집계 로직 (안드로이드 의존성 없음)
data/        Room 저장소, 설정(DataStore), 업데이트 확인
ui/          Compose 화면과 ViewModel
```

`domain`은 안드로이드에 기대지 않으므로 단위 테스트로 전부 검증한다.

```bash
./gradlew testReleaseUnitTest
```
