# SSUTable (시연용, 폰 한 대 버전)

숭실대 학생식당 좌석 예약 앱. 제안서(`docs/SSUTable_제안서및아이디어구체화_G1J2.pptx`)와 와이어프레임 S-01~S-09를 그대로 구현했다.
Android(Java), minSdk 26. 서버 없이 폰 한 대에서 돌아가고, 다른 학생들의 좌석은 앱 안에서 만들어 낸다.

## 실행

Android Studio에서 `SSUTable` 폴더를 열고 Run. 또는:

```bash
cd SSUTable && ./gradlew :app:installDebug
```

규칙 테스트(JUnit 18개):

```bash
cd SSUTable && ./gradlew :app:testDebugUnitTest
```

## 발표 시연 순서 (설정 → 시연 모드)

1. **시간대별 규칙**: 모드 지정 `자동`, 시각 옮기기 `11:15`, 시간 배속 `10배`. 30초쯤 지나면 홈이 스터디에서 점심 식사로 바뀌고, 개방 좌석이 340석에서 328석으로 줄어든다.
2. **식권과 자리 함께 예약**: 좌석 탭에서 빈자리를 누르고 예약하기를 누른다. 예약 완료 화면에서 식권을 누르면 QR이 나온다.
3. **도착 확인**: 내 예약 아래 `시연용 위치`를 `밖`으로 바꾸면 [도착]이 꺼지고, `학생회관 안`으로 바꾸면 켜진다.
4. **자동 반납**: 사용 중일 때 `밖`을 누르면 자리 비움으로 바뀌고 30분 카운트다운과 알림이 뜬다. 10배속이면 3분 뒤 "30분 이탈"로 체크아웃된다.
5. **동시 예약 실패**: `다음 예약을 동시 예약 실패로 만들기`를 켜고 예약하면 "방금 다른 학생이 먼저 예약했어요"가 나온다.
6. **운영 종료**: 모드 지정 `종료`.

`모든 데이터 지우고 처음부터`로 권한 안내 화면부터 다시 시작할 수 있다.

## 구조

| 위치 | 하는 일 |
| --- | --- |
| `core/` | 순수 Java 규칙. 시간표(`Schedule`), 예약 상태 변화(`ReservationEngine`), 340석 배치(`SeatLayout`), 다른 학생 시뮬레이션(`OccupancySimulator`) |
| `core/SeatRepository` | 화면이 데이터에 접근하는 유일한 창구. Firebase로 바꿀 때 이 인터페이스만 새로 구현하면 된다 |
| `data/LocalSeatRepository` | 지금 구현. 내 예약은 SharedPreferences에 JSON으로 저장 |
| `ui/` | 화면. `SeatMapView`가 도면을 직접 그린다 |

## Firebase로 넘어갈 때

- `LocalSeatRepository` 대신 `FirebaseSeatRepository`를 만들고 `App.onCreate`에서 바꿔 끼운다.
- `reserve()`의 "누른 순간 다시 확인" 자리가 Realtime DB 트랜잭션으로 바뀐다.
- 자동 반납은 지금처럼 `endsAt`, `arriveBy`를 저장해 두고 각 폰이 서버 시각과 비교한다.
- `OccupancySimulator`는 빼고, 다른 폰의 예약을 그대로 읽는다.

## 지금 버전의 한계

- 위치는 실제 GPS가 아니라 시연용 스위치로 판정한다(위치 권한은 실제로 요청함).
- 340석 배치는 실제 도면이 아니라 구역 비율만 맞춘 것이다.
- 다른 학생들의 예약·사용은 가짜다. 폰 두 대 동기화는 Firebase 단계에서 한다.
