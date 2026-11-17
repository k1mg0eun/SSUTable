# SSUTable Firebase 실시간 동기화 설계

- 작성: 2026-11-17
- 대상 코드: `SSUTable/` (Android, Java 17, minSdk 26, AGP 9.5.1, Gradle 9.7.1)

## 1. 왜 하는가

지금 SSUTable은 폰 한 대에서만 도는 시연용 앱이다. 와이어프레임 S-01~S-09 화면과 시간대별 규칙(아침 45분, 점심, 스터디 2시간+1회 연장, 10분 미도착 취소, 30분 이탈 반납)은 다 들어 있고 JUnit 18개가 통과한다.

제안서 11쪽과 13쪽에서 발표한 약속은 아직 못 지킨다.

- 한 폰에서 예약하면 다른 폰에 2초 안에 반영된다.
- 같은 좌석을 동시에 누르면 트랜잭션으로 한 명만 성공한다.
- 결과 발표 때 시연 모드(시간대 이동, 10배속)로 자동 반납까지 폰 두 대로 보여준다.

이번 작업은 이 약속을 지키는 1차 코드를 완성해서 GitHub에 올리고, 팀원(이준호: 백엔드, 박준호: 프론트)이 이어받아 작업할 수 있게 하는 것이다. 목표는 "최대한 완벽한 상태로 올리기"다.

## 2. 목표 / 비목표 / 실패 조건

**목표**
- 두 폰이 같은 340석 상태를 본다. 한쪽 예약·도착·체크아웃·자동 반납이 다른 쪽에 2초 안에 보인다.
- 같은 좌석 동시 예약은 Realtime Database 트랜잭션으로 한 명만 성공하고, 진 쪽은 기존 "방금 다른 학생이 먼저 예약했어요" 화면을 본다.
- 시연 설정(시각 옮기기, 배속, 모드 지정, 시험기간)은 한 폰에서 바꾸면 모든 폰이 같이 따라간다.
- 가짜 학생(`OccupancySimulator`) 위에 진짜 예약을 덮어 보여준다. 혼잡도 화면은 지금처럼 붐벼 보인다.
- 기존 테스트 18개는 그대로 통과하고, 새 순수 Java 로직(덮어쓰기, 만료 판정)에 테스트를 붙인다.
- GitHub 저장소에 올리고, README에 실행법·Firebase 설정법·담당별 안내를 적는다.

**비목표**
- Cloud Functions 등 서버 코드 (무료 Spark 요금제 유지, 발표 내용과 일치)
- 실제 GPS 판정 (지금처럼 시연용 위치 스위치 유지)
- 실제 도면 반영, 식권 결제 연동
- ViewModel/LiveData 같은 구조 재작성

**실패 조건 (동작은 해도 실패로 본다)**
- 화면이 Firebase 응답을 기다리며 멈추거나 깜빡인다. 지금처럼 0.25초 렌더 루프는 캐시만 읽어야 한다.
- 두 폰의 시연 시계가 어긋나서 한 폰은 점심, 다른 폰은 스터디 규칙으로 움직인다.
- 가짜 학생 좌석과 진짜 예약이 같은 자리를 둘 다 차지한 것처럼 보이거나, 진짜 예약이 가짜 상태에 가려진다.
- 네트워크가 없거나 `google-services.json`이 없을 때 앱이 아예 안 뜬다.

## 3. 제약

- Firebase 프로젝트 생성, Android 앱 등록, `google-services.json` 다운로드, 익명 로그인·Realtime Database 활성화는 사용자(김고은)가 Firebase 콘솔에서 직접 한다. 코드는 그 전에 먼저 작성한다.
- 사용자 Mac은 Intel 8GB라 에뮬레이터 한 대도 버겁고, 사용자는 안드로이드 폰이 없다. 두 대 실기기 확인은 팀원 폰으로 한다.
- 화면 코드(`ui/`)도 이번 1차에서 필요한 만큼 직접 고친다(사용자가 완성본을 올린 뒤 팀원이 이어받는 순서라 충돌 걱정이 없다). 다만 기존 화면 구조와 디자인은 유지한다.

## 4. 고려한 방식 (Approaches)

| 방식 | 내용 | 결정 |
| --- | --- | --- |
| A. Realtime Database + 각 폰이 시각 비교로 자동 반납 | 제안서 11쪽 그대로. 예약에 마감 시각을 저장하고 각 폰이 서버 시각 기준으로 판단 | **선택** |
| B. Firestore | 트랜잭션·실시간 반영 동일. 340석을 통째로 듣는 구조라 쿼리 장점이 없고 발표 내용과 다름 | 제외 |
| C. Cloud Functions로 서버 타이머 | 폰이 꺼져도 정확하지만 유료 Blaze 요금제 필요, 발표("무료라 서버 타이머 없음")와 충돌 | 제외 |

인증은 **익명 인증**을 쓴다. 이름·학번 입력(S-01)은 지금처럼 로컬에 두고 DB에도 표시용으로 저장한다.

## 5. 설계

### 5.1 저장소 인터페이스 변경

`SeatRepository`에서 바뀌는 것은 `reserve` 하나다.

```java
interface ReserveCallback { void done(ReserveResult result); }
void reserve(String seatId, ReserveCallback cb);   // 메인 스레드에서 cb 호출
```

- `snapshot()`, `current()`, `history()`, `tick()`은 지금처럼 **동기**로 둔다. Firebase 구현은 리스너가 채워 둔 메모리 캐시를 돌려준다. 그래서 0.25초 렌더 루프와 화면 코드는 바뀌지 않는다.
- `arrive`, `extend`, `checkout`, `cancel`, `acknowledgeEnded`는 동기 그대로 둔다. 로컬 캐시를 먼저 바꾸고(낙관적 반영) Firebase에 쓰기를 보낸다. 이 동작들은 내 예약에 대한 것이라 경쟁이 없다.
- `ReserveResult`에 `ERROR`(네트워크 실패·시간 초과)를 추가하고, 예약 시트에 "연결이 불안정해요. 다시 시도해 주세요" 문구를 낸다.
- `LocalSeatRepository`도 새 `reserve` 모양을 구현해서(바로 콜백 호출) Firebase 없이도 그대로 돈다.

화면 쪽 수정 범위: `ReserveSheet.reserve()`를 콜백 방식으로 바꾸고 기다리는 동안 버튼을 비활성화한다. 그 밖에 홈의 오프라인 표시(5.7), 설정 화면 항목(5.9), 새 종료 이유 문구(5.6)를 위한 수정만 한다.

### 5.2 DB 구조 (Realtime Database)

```
/seats/{seatId}            현재 진짜 예약이 잡고 있는 좌석. 없으면 키 없음
    uid, resId, state (WAITING|IN_USE|AWAY), since, deadline   // 둘 다 가상 시각 ms. since = 예약한 시각(createdAt)
/reservations/{uid}/current   내 진행 중 예약 (Reservation 전체 필드, 기존 toJson과 같은 키)
/users/{uid}                  name, studentId (표시용)
/demo                         공유 시연 설정
    anchorServer, anchorVirtual, speed, forcedMode, exam, updatedAt
```

- `deadline`은 상태별로 그 예약이 저절로 끝나는 시각이다. WAITING이면 `arriveBy`, IN_USE면 `endsAt`, AWAY면 `min(awaySince+30분, endsAt)`.
- 이력(`history`)은 지금처럼 각 폰의 SharedPreferences에 둔다. 익명 uid는 재설치하면 바뀌므로 서버에 둘 이점이 적다.

### 5.3 시각: 공유 시연 시계

- 모든 폰은 `.info/serverTimeOffset`으로 서버 시각을 구한다: `serverNow = System.currentTimeMillis() + offset`.
- 가상 시각 = `anchorVirtual + (serverNow - anchorServer) * speed`. 기존 `DemoClock`이 실제 시각 공급자로 `serverNow`를 쓰게 바꾼다.
- 설정 화면에서 시각 옮기기·배속·모드 지정·시험기간을 바꾸면 `/demo`에 쓴다. 각 폰은 `/demo`를 듣다가 바뀌면 `DemoClock`을 다시 맞춘다.
- `/demo`가 비어 있으면 실제 시각, 배속 1, 자동 모드다.
- 위치 스위치(`inside`)와 "다음 예약 동시 예약 실패" 토글은 **폰마다 따로** 둔다. 둘 다 그 폰 사람의 상황이기 때문이다.

### 5.4 좌석 상태 합치기

새 순수 Java 클래스(예: `core/FloorMerger`)가 340석 상태를 만든다.

1. 좌석마다 `OccupancySimulator`로 가짜 상태를 구한다(같은 가상 시각이면 모든 폰이 같은 결과).
2. `/seats`에 그 좌석 항목이 있고 `deadline > 가상 now`이면 진짜 예약이 이긴다: 내 uid면 MINE, 아니면 WAITING→RESERVED, IN_USE·AWAY→IN_USE.
3. `since - 5분 <= 가상 now < deadline`이 아닌 항목은 무시한다(가짜 상태로 돌아감). `deadline`이 지났으면 만료, `since`가 5분 넘게 미래면 시연 시계를 과거로 옮긴 뒤 남은 옛 항목이다. 5분(가상 시각) 여유는 폰끼리 시계가 조금 어긋나도 막 잡힌 다른 폰의 점유를 덮어쓰지 않게 하려는 것이다.
4. 모드상 미개방(CLOSED) 좌석은 진짜 예약이 있어도 CLOSED가 아니라 진짜 예약 상태를 보인다. 예약은 개방일 때만 생기므로 드물고, 사용 중인 자리를 갑자기 숨기지 않기 위해서다.

가짜 학생이 진짜 예약 좌석을 덮는 일은 없다(2번이 항상 이김). 진짜 사용자가 예약할 수 있는 좌석은 합친 결과가 FREE인 좌석뿐이다.

### 5.5 예약 트랜잭션

`reserve(seatId)` 순서:

1. 로컬 확인: 이미 진행 중 예약 있음 → `ALREADY_HAVE`, 좌석 없음 → `NOT_FOUND`, 운영 종료 → `CLOSED`.
2. 시연 토글(`takeConflictOnce`)이 켜져 있으면 → `TAKEN`.
3. 합친 상태에서 그 좌석이 FREE가 아니면 → `TAKEN`.
4. `/seats/{seatId}`에 트랜잭션: 비어 있거나 5.4의 3번 기준으로 무효인 항목이면 내 항목을 쓰고 성공, 아니면 중단.
5. 성공하면 `/reservations/{uid}/current`에 예약을 쓰고 `OK`. 실패하면 `TAKEN`. 10초 안에 응답이 없거나 오류면 `ERROR`.

### 5.6 자동 반납과 상태 변경

- 예약 주인 폰의 `tick()`이 지금과 같은 `ReservationEngine.tick()`을 돌린다. 상태가 바뀌면 `/reservations/{uid}/current`와 `/seats/{seatId}`(state, deadline)를 갱신하고, 끝났으면 `/seats/{seatId}`를 지운다.
- 주인 폰이 꺼져 있어도 다른 폰은 `deadline`을 보고 그 좌석을 빈자리로 보인다(5.4의 3번). 다음 사람이 예약하면 트랜잭션이 만료 항목을 덮어쓴다.
- 앱을 다시 켜면 `/reservations/{uid}/current`를 읽어 내 예약을 복원하고, 그사이 지난 마감은 첫 `tick()`에서 처리된다.
- **내 좌석을 남이 지웠을 때:** 주인 폰은 자기 `/seats/{seatId}`를 듣는다. 항목이 사라지거나 다른 uid로 바뀌면(다른 폰의 "모든 좌석 예약 초기화" 등) 내 예약을 새 종료 이유 `RESET`("시연 초기화", "시연 데이터가 초기화돼 예약이 끝났어요")으로 끝내고, 내 `/reservations/{uid}/current`를 지우고, 로컬 이력에 남긴다. 다시 `/seats`에 쓰지 않는다. 내가 방금 쓴 변경이 돌아오는 경우와 구분하기 위해 resId로 비교한다.
- `RESET`은 `Reservation.EndReason`에 추가하는 값이다. `ReservationEngine`의 규칙은 바꾸지 않고, 종료 처리는 엔진의 기존 종료 경로(예: `cancel`과 같은 방식)에 이유만 다르게 넘기는 작은 공개 메서드를 하나 둔다.

### 5.7 연결 실패와 대체 동작

- `google-services.json`이 없으면 빌드가 실패하지 않도록 Google Services 플러그인은 파일이 있을 때만 적용한다. 파일이 없으면 `App`이 `LocalSeatRepository`를 쓴다.
- 파일은 있지만 익명 로그인이나 첫 데이터 로드가 안 되면, 화면은 가짜 학생 상태로 그려지고 예약 시 `ERROR`를 낸다. 홈 상단에 작게 "오프라인" 표시를 낸다.
- Firebase 디스크 캐시(`setPersistenceEnabled`)는 끈다. 예전 좌석 상태가 남아 시연을 헷갈리게 하지 않기 위해서다.

### 5.8 보안 규칙

`database.rules.json`을 저장소에 두고 README에 콘솔에 붙여 넣는 법을 적는다.

- 모든 읽기·쓰기는 로그인(`auth != null`) 필요.
- `/reservations/{uid}`, `/users/{uid}`: 본인 uid만 쓰기.
- `/seats/{seatId}`: 로그인한 사용자면 쓰기·삭제 허용(만료 판정이 가상 시각 기준이라 규칙에서 검증할 수 없음, 초기화 버튼도 필요). 대신 쓸 때는 `uid == auth.uid`여야 하고, 필드는 uid·resId·state·since·deadline만, 타입·값 범위(state는 세 값 중 하나, 숫자 필드는 숫자)를 검사한다. `seatId` 키는 좌석 ID 형식만 허용.
- `/reservations`: 본인 uid만 쓰기. 초기화 버튼은 `/reservations`를 지우지 않는다(각 폰이 5.6 규칙으로 스스로 지움).
- `/demo`: 로그인한 사용자 누구나 쓰기, 필드·타입 검사.
- **감수하는 위험:** 저장소가 공개이고 `google-services.json`을 함께 올리므로, 누구나 우리 DB에 익명 로그인해서 좌석·시연 설정을 바꿀 수 있다. 수업 시연용이라 받아들이고, README에 적는다. 장난이 생기면 Firebase 콘솔에서 익명 로그인을 잠시 끄거나 프로젝트를 새로 만들어 대응한다.
- `/demo`: 로그인한 사용자 누구나 쓰기.

### 5.9 설정 화면 추가 항목

- "모든 데이터 지우고 처음부터": 지금 동작 + 내 `/seats`·`/reservations` 항목 삭제.
- "모든 데이터 지우고 처음부터"는 **내 폰 것만** 지운다. 공유 시연 설정(`/demo`: 시계, 배속, 모드 지정, 시험기간)은 건드리지 않는다. 시계를 실제 시각으로 돌리는 건 기존 "실제 시각으로" 버튼이 `/demo`를 바꾸는 방식으로 한다.
- 새 시연 버튼 "모든 좌석 예약 초기화": `/seats` 전체 삭제. 예약 중이던 폰들은 5.6 규칙으로 스스로 예약을 끝낸다. 리허설 후 정리용.
- 연결 상태 한 줄(연결됨 / 오프라인 / 로컬 모드).

## 6. 테스트

- 기존 JUnit 18개 유지.
- 새 순수 Java 테스트: 좌석 합치기(진짜 예약 우선, 만료 무시, 내 좌석 MINE), 공유 시계 계산(앵커·배속·서버 오프셋), 트랜잭션 판단 함수(비었음/만료/남의 유효 예약).
- Firebase 연동은 자동 테스트하지 않는다. README에 두 폰 확인 체크리스트(예약 반영, 동시 예약, 시각 공유, 자동 반납, 오프라인)를 적고 팀원 폰으로 확인한다.

## 7. GitHub 공유

- `SSUTable/`을 git 저장소로 만들고 GitHub(`k1mg0eun` 계정)에 **공개** 저장소로 만들어 올린다. `google-services.json`은 저장소에 포함한다(팀원이 받자마자 같은 Firebase에 붙도록). 위험은 5.8 참고.
- README에 추가할 것: Firebase 설정 순서, `google-services.json` 위치, 보안 규칙 적용법, 두 폰 시연 순서, 담당별 안내(이준호: `data/FirebaseSeatRepository`·규칙, 박준호: `ui/`).

## 8. 빌더에게 맡기는 것

- 클래스·파일 이름, 패키지 내 위치 (`data/` 아래 Firebase 구현, `core/` 아래 순수 로직이라는 원칙만 지킴)
- Firebase BoM 버전과 의존성 표기 방식
- 리스너 등록·해제 시점, 스레드 처리 세부
- 오프라인 표시의 정확한 모양 (기존 디자인 토큰 사용)
- 커밋 단위
