# SSUTable Firebase 실시간 동기화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 폰 한 대 시연 앱 SSUTable에 Firebase Realtime Database를 붙여 두 폰이 같은 좌석·시연 시계를 공유하게 하고, GitHub 공개 저장소로 올린다.

**Architecture:** `SeatRepository` 인터페이스 뒤에 `FirebaseSeatRepository`를 추가하고, `google-services.json`이 없으면 기존 `LocalSeatRepository`로 돈다. 좌석 합치기·만료 판정·공유 시계 계산은 `core/`의 순수 Java로 빼서 JUnit으로 검증하고, Firebase 클래스는 그 결정을 DB 읽기/쓰기에 옮기기만 한다. 화면은 0.25초 렌더 루프가 동기 캐시를 읽는 지금 구조를 유지한다.

**Tech Stack:** Android Java 17, minSdk 26, AGP 9.5.1, Gradle 9.7.1, Firebase BoM(Realtime Database, Auth), JUnit 4.13.2

**Spec:** `docs/superpowers/specs/2026-11-17-firebase-sync-design.md`

## Global Constraints

- 모든 경로는 `SSUTable/` 기준. 패키지 `com.g1j2.ssutable`. 순수 로직은 `core/`(Android import 금지), Firebase 코드는 `data/`.
- `ReservationEngine`의 규칙(10분 도착, 45분/2시간, 1회 연장, 30분 이탈)은 바꾸지 않는다.
- 0.25초 렌더 루프(`App.TICK_MS = 250`)는 메모리 캐시만 읽는다. 렌더 경로에서 Firebase 호출 금지.
- `google-services.json`이 없어도 `./gradlew :app:assembleDebug`와 `:app:testDebugUnitTest`가 성공하고 앱은 로컬 모드로 돈다.
- 예약 응답 대기 상한 10초, 넘으면 `ReserveResult.ERROR`.
- Firebase 디스크 캐시(`setPersistenceEnabled`) 끈다.
- 사용자 문구는 기존 말투(해요체) 유지. 정해진 문구:
  - ERROR: "연결이 불안정해요. 다시 시도해 주세요"
  - RESET 종료: 라벨 "시연 초기화", 헤드라인 "시연 데이터가 초기화돼 예약이 끝났어요"
- 기존 테스트 18개는 계속 통과.

## Review Focus

1. 시연 시계를 과거로 옮긴 뒤 남은 옛 `/seats` 항목 → 빈자리로 보여야 한다 (Task 2 `ignoresClaimFromTheFuture`).
2. 주인 폰이 꺼진 채 마감이 지난 좌석 → 다른 폰에 빈자리로 보이고 예약 가능해야 한다 (Task 2 `expiredClaimIsTakeable`).
3. 예약 응답을 기다리는 중 예약 버튼 연타 → 요청은 한 번만 가야 한다 (Task 4 Step 5, 수동 확인 항목).
4. 다른 폰이 "모든 좌석 예약 초기화" → 예약 중이던 폰은 RESET 종료 화면을 보고 좌석을 다시 쓰지 않아야 한다 (Task 2 `lostSeatWhenClaimRemovedOrReplaced`, `notLostWhenOwnClaim`).
5. 앱을 껐다 켰을 때 진행 중 예약 복원, 그사이 지난 마감은 첫 tick에서 처리 (Task 7 수동 체크리스트).

---

### Task 1: git 저장소와 기준 커밋

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1:** `.gitignore`에 `*.keystore`, `.kotlin/`을 추가한다(`google-services.json`은 무시하지 않는다 — 공개 저장소에 포함하기로 함).
- [ ] **Step 2:** `cd SSUTable && git init -b main`
- [ ] **Step 3:** 기준 확인. Run: `./gradlew :app:testDebugUnitTest` Expected: BUILD SUCCESSFUL, 테스트 18개 통과.
- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: import SSUTable single-phone demo with design and plan docs"
```

---

### Task 2: 좌석 점유 항목과 좌석 합치기 (`core/`)

**Files:**
- Create: `app/src/main/java/com/g1j2/ssutable/core/SeatClaim.java`
- Create: `app/src/main/java/com/g1j2/ssutable/core/FloorMerger.java`
- Test: `app/src/test/java/com/g1j2/ssutable/core/FloorMergerTest.java`

**Interfaces:**
- Produces:
  - `final class SeatClaim { String uid, resId; Reservation.State state; long since, deadline; }` (state는 WAITING/IN_USE/AWAY만)
  - `static SeatClaim SeatClaim.of(String uid, Reservation r)` — since=`r.createdAt`, deadline: WAITING→`arriveBy`, IN_USE→`endsAt`, AWAY→`min(awaySince+AWAY_GRACE, endsAt)`. `r`가 끝났으면 null.
  - `boolean SeatClaim.liveAt(long now)` — `since <= now && now < deadline`
  - `static boolean SeatClaim.takeable(SeatClaim existing, long now)` — `existing == null || !existing.liveAt(now)`
  - `static boolean SeatClaim.lostSeat(SeatClaim remote, Reservation mine)` — mine이 진행 중이고 (`remote == null` 또는 `!remote.resId.equals(mine.id)`)이면 true
  - `final class FloorMerger { FloorMerger(SeatLayout, OccupancySimulator); FloorSnapshot merge(long now, ModeInfo info, boolean exam, Map<String, SeatClaim> claims, String myUid, Reservation mine); }`

- [ ] **Step 1: Write the failing tests** (`ScheduleTest.at(h, m)`로 시각, 좌석은 `SeatLayout.get().seat("B-12")`)

```java
@Test public void liveClaimOverridesSimulator()        // 남의 IN_USE claim → IN_USE, WAITING claim → RESERVED
@Test public void myClaimShowsMine()                    // claim.uid == myUid → MINE
@Test public void expiredClaimIsTakeable()              // deadline <= now → 시뮬레이터 상태, takeable == true
@Test public void ignoresClaimFromTheFuture()           // since > now → 시뮬레이터 상태, takeable == true
@Test public void liveClaimIsNotTakeable()              // takeable == false
@Test public void claimOnClosedSeatStillShows()         // 점심 closedAtLunch 좌석 + live claim → CLOSED 아님
@Test public void localReservationWithoutClaimsShowsMine() // claims 비어 있고 mine 진행 중 → 그 좌석 MINE (로컬 모드 동작 유지)
@Test public void claimDeadlineFollowsState()           // SeatClaim.of: WAITING=arriveBy, IN_USE=endsAt, AWAY=min(awaySince+30분, endsAt)
@Test public void lostSeatWhenClaimRemovedOrReplaced()  // remote null → true, 다른 resId → true
@Test public void notLostWhenOwnClaim()                 // 같은 resId → false, mine 끝났으면 false
```

- [ ] **Step 2:** Run: `./gradlew :app:testDebugUnitTest --tests '*FloorMergerTest'` Expected: 컴파일 실패(클래스 없음)
- [ ] **Step 3:** `SeatClaim`, `FloorMerger` 구현. `merge` 순서: 좌석마다 claim이 `liveAt(now)`이면 claim 상태(내 uid→MINE, WAITING→RESERVED, IN_USE/AWAY→IN_USE), 아니면 `mine`이 진행 중이고 좌석이 같으면 MINE, 아니면 `simulator.stateOf(seat, now, info.mode, exam, minuteOfDay)`.
- [ ] **Step 4:** 같은 명령으로 통과 확인. 전체 `:app:testDebugUnitTest`도 통과.
- [ ] **Step 5: Commit** `feat(core): add SeatClaim and FloorMerger for real-over-simulated seats`

---

### Task 3: 공유 시연 시계 상태 (`core/`)

**Files:**
- Create: `app/src/main/java/com/g1j2/ssutable/core/DemoState.java`
- Modify: `app/src/main/java/com/g1j2/ssutable/core/DemoClock.java`
- Test: `app/src/test/java/com/g1j2/ssutable/core/DemoClockTest.java`

**Interfaces:**
- Produces:
  - `final class DemoState { long anchorServer = -1, anchorVirtual = -1; int speed = 1; Mode forcedMode /* null=자동 */; boolean exam; }` — anchor가 -1이면 실제 시각
  - `void DemoClock.apply(long anchorReal, long anchorVirtual, int speed)` — 세 값을 그대로 덮어쓴다(원격 상태 반영용). anchor가 -1이면 `real.now()`로 둘 다 맞춘다.
  - 기존 `DemoClock(RealTime, ...)`의 `RealTime`에 서버 보정 시각을 넣는 방식으로 공유한다(새 클래스 없음).

- [ ] **Step 1: Write the failing tests**

```java
@Test public void serverOffsetShiftsVirtualTime()   // RealTime = () -> fakeNow + offset; apply(anchor, at(11,15), 10); fakeNow += 30_000 → now() == at(11,15) + 300_000
@Test public void twoClocksWithSameStateAgree()     // 서로 다른 로컬 시각·오프셋을 가진 두 시계가 같은 서버 앵커로 같은 now()
@Test public void applyMinusOneMeansRealTime()      // apply(-1, -1, 1) → now() == real.now()
```

- [ ] **Step 2:** Run `--tests '*DemoClockTest'` Expected: FAIL
- [ ] **Step 3:** `DemoState`, `DemoClock.apply` 구현
- [ ] **Step 4:** 테스트 통과 확인
- [ ] **Step 5: Commit** `feat(core): shared demo clock state`

---

### Task 4: 비동기 예약 인터페이스와 로컬 구현·예약 시트

**Files:**
- Modify: `core/SeatRepository.java`, `data/LocalSeatRepository.java`, `ui/ReserveSheet.java`
- Modify: `core/Reservation.java` (EndReason.RESET), `core/ReservationEngine.java` (공개 종료 메서드)
- Test: `app/src/test/java/com/g1j2/ssutable/core/ReservationEngineTest.java`

**Interfaces:**
- Consumes: Task 2 `FloorMerger`
- Produces:
  - `SeatRepository.ReserveResult` 에 `ERROR` 추가
  - `interface SeatRepository.ReserveCallback { void done(ReserveResult result); }`
  - `void reserve(String seatId, ReserveCallback cb)` — 기존 동기 `reserve(String)` 제거, cb는 메인 스레드
  - `enum SeatRepository.Connection { LOCAL, CONNECTING, ONLINE, OFFLINE }`, `Connection connection()`
  - `void resetAllSeats()` — 로컬은 아무것도 안 함
  - `Reservation.EndReason.RESET("시연 초기화", "시연 데이터가 초기화돼 예약이 끝났어요")`
  - `void ReservationEngine.endBy(Reservation r, long now, Reservation.EndReason reason)` — 기존 private `end`를 그대로 부른다

- [ ] **Step 1: Failing test** `ReservationEngineTest.endByResetCancelsUnusedTicket` — 점심 예약(WAITING) 후 `endBy(r, now, RESET)` → state ENDED, endReason RESET, ticket CANCELLED.
- [ ] **Step 2:** 실패 확인
- [ ] **Step 3:** `EndReason.RESET`, `endBy` 추가. `SeatRepository` 변경. `LocalSeatRepository`: `compute()`를 `FloorMerger.merge(..., Collections.emptyMap(), null, current)`로 바꾸고, `reserve(seatId, cb)`는 기존 로직 결과를 `cb.done(...)`로 바로 넘긴다. `connection()`은 `LOCAL`.
- [ ] **Step 4:** 모든 테스트 통과 확인
- [ ] **Step 5:** `ReserveSheet.reserve()`를 콜백으로: 누르면 `confirm.setEnabled(false)`와 문구 "예약하는 중…", 결과가 오면 `isAdded()` 확인 후 기존 OK/TAKEN 분기, `ERROR`면 "연결이 불안정해요. 다시 시도해 주세요"를 `showMessage`로 보이고 버튼 다시 켬. 대기 중 다시 눌러도 요청을 보내지 않는다.
- [ ] **Step 6:** Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest` Expected: BUILD SUCCESSFUL
- [ ] **Step 7: Commit** `refactor: async reserve, RESET end reason, merger-backed local repo`

---

### Task 5: 시연 설정을 App 한 곳으로 모으기

**Files:**
- Create: `app/src/main/java/com/g1j2/ssutable/core/DemoSettings.java` (인터페이스)
- Create: `app/src/main/java/com/g1j2/ssutable/data/LocalDemoSettings.java`
- Modify: `App.java`, `ui/SettingsActivity.java`, `data/Prefs.java`

**Interfaces:**
- Consumes: Task 3 `DemoState`, `DemoClock.apply`
- Produces:
  - `interface DemoSettings { DemoState current(); void write(DemoState next); void setListener(Runnable onChange); }`
  - `LocalDemoSettings(Prefs)` — 기존 Prefs 키(`anchorReal`, `anchorVirtual`, `speed`, `forcedMode`, `exam`)에 저장, 로컬에서는 `anchorServer`를 `anchorReal`로 저장
  - `App`: `setForcedMode(Mode)`, `setExam(boolean)` 추가. `setSpeed`, `jumpToMinute`, `resetClock`, 새 두 메서드 모두 `DemoState`를 만들어 `demo.write()`; 리스너가 `clock.apply(...)` 후 `tickNow()`. `mode()`·`exam()`은 `demo.current()`에서 읽는다.
  - `App.resetAll()`: `repo.clearAll()` + 프로필·권한 안내·알림 설정 등 **내 폰 것만** 지우고, 시연 설정(시계·배속·모드·시험)은 그대로 둔다. `Prefs.clearAll()`을 시연 키를 남기는 `clearPersonal()`로 바꾼다.

- [ ] **Step 1:** 위 클래스·메서드 구현. `SettingsActivity`의 모드 그룹·시험 스위치가 `prefs.setForcedMode`/`prefs.setExam` 대신 `App.get().setForcedMode`/`setExam`을 부르게 바꾼다.
- [ ] **Step 2:** Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest` Expected: BUILD SUCCESSFUL, 테스트 통과
- [ ] **Step 3: Commit** `refactor: route demo settings through App and DemoSettings`

---

### Task 6: Firebase 의존성과 조건부 플러그인

**Files:**
- Modify: `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`(필요 시)

- [ ] **Step 1:** 루트 `plugins`에 `id("com.google.gms.google-services") version "<AGP 9와 호환되는 최신 4.4.x>" apply false`. app에 Firebase BoM + `firebase-database` + `firebase-auth`.
- [ ] **Step 2:** `app/build.gradle.kts`: `if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")`
- [ ] **Step 3:** Run: `./gradlew :app:assembleDebug` (파일 없이) Expected: BUILD SUCCESSFUL
- [ ] **Step 4: Commit** `build: add Firebase deps, apply google-services only when config exists`

---

### Task 7: FirebaseSeatRepository와 FirebaseDemoSettings

**Files:**
- Create: `app/src/main/java/com/g1j2/ssutable/data/FirebaseSeatRepository.java`
- Create: `app/src/main/java/com/g1j2/ssutable/data/FirebaseDemoSettings.java`
- Create: `app/src/main/java/com/g1j2/ssutable/data/ReservationJson.java` (기존 `LocalSeatRepository.toJson/fromJson`을 옮겨 둘이 함께 쓴다; Firebase에는 `Map<String,Object>`로 변환)
- Modify: `App.java`, `data/LocalSeatRepository.java`

**Interfaces:**
- Consumes: Task 2 (`SeatClaim.of/takeable/lostSeat`, `FloorMerger`), Task 4 (`SeatRepository` 전체), Task 5 (`DemoSettings`)
- Produces:
  - `FirebaseSeatRepository(Context, Env, FirebaseDatabase, String uid)` implements `SeatRepository`
  - `FirebaseDemoSettings(FirebaseDatabase)` implements `DemoSettings` — `/demo`를 듣고 쓴다. 서버 오프셋은 `/.info/serverTimeOffset`.
  - `App.onCreate`: `FirebaseApp.initializeApp(this)`가 null이면 로컬 구성. 아니면 `setPersistenceEnabled(false)` → 익명 로그인 → 성공 시 Firebase 저장소·설정으로 교체(그 전까지 로컬 캐시 없는 상태로 `CONNECTING`). App의 `RealTime`은 `System.currentTimeMillis() + serverOffset`.

DB 경로와 필드(스펙 5.2): `/seats/{seatId}` = {uid, resId, state, since, deadline}, `/reservations/{uid}/current` = ReservationJson, `/users/{uid}` = {name, studentId}, `/demo` = {anchorServer, anchorVirtual, speed, forcedMode, exam, updatedAt}.

동작:
- 리스너: `/seats` 전체 → `Map<String, SeatClaim>` 캐시, `/reservations/{uid}/current` 첫 로드로 `current` 복원, `/.info/connected` → `connection()`.
- 로그인 직후 `/users/{uid}`에 `Prefs`의 이름·학번을 쓴다(프로필 수정 시에도).
- `snapshot()`은 `FloorMerger.merge(now, mode, exam, claims, uid, current)`.
- `reserve`: 스펙 5.5 순서. 트랜잭션은 `SeatClaim.takeable(existing, env.now())`이면 `SeatClaim.of(uid, newReservation)` 기록. 성공 시 `/reservations/{uid}/current` 쓰기 후 OK. `Handler.postDelayed` 10초 타임아웃으로 ERROR(늦게 온 결과는 무시하되, 늦게 성공한 트랜잭션이면 내 claim을 지운다).
- `arrive/extend/checkout/cancel/tick`: 로컬 `ReservationEngine` 적용 후 바뀌었으면 `current`와 `/seats/{seatId}`(진행 중이면 `SeatClaim.of`, 끝났으면 삭제) 쓰기. 끝난 예약은 기존처럼 로컬 이력(SharedPreferences)에 남기고 `/reservations/{uid}/current` 삭제는 `acknowledgeEnded`에서.
- `/seats` 변경마다 `SeatClaim.lostSeat(claims.get(current.seatId), current)`이면 `engine.endBy(current, now, RESET)`, `/seats`는 쓰지 않고 `/reservations/{uid}/current` 삭제, 알림 없이 다음 렌더에서 종료 화면.
- `clearAll`: 내 claim·`/reservations/{uid}` 삭제 + 로컬 이력 삭제. `resetAllSeats`: `/seats` 삭제.

- [ ] **Step 1:** `ReservationJson` 추출, `LocalSeatRepository`가 쓰게 바꾸고 테스트·빌드 통과 확인 후 커밋 `refactor: extract ReservationJson`
- [ ] **Step 2:** `FirebaseDemoSettings`, `FirebaseSeatRepository`, `App` 연결 구현
- [ ] **Step 3:** Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (설정 파일 없이) Expected: BUILD SUCCESSFUL
- [ ] **Step 4: Commit** `feat(data): Firebase-backed repository and shared demo settings`

---

### Task 8: 연결 상태·초기화 화면 항목, 보안 규칙, README

**Files:**
- Modify: `ui/HomeFragment.java`, `res/layout/fragment_home.xml`, `ui/SettingsActivity.java`, `res/layout/activity_settings.xml`, `README.md`
- Create: `database.rules.json`

- [ ] **Step 1:** 홈 상단 시계 옆에 `connection()`이 `OFFLINE`일 때만 보이는 작은 "오프라인" 태그(기존 `bg_tag` 사용).
- [ ] **Step 2:** 설정 시연 카드: 연결 상태 한 줄("연결됨" / "오프라인" / "연결 중" / "로컬 모드(Firebase 설정 없음)"), "모든 좌석 예약 초기화" 버튼(로컬 모드면 숨김, 확인 대화상자 후 `repo().resetAllSeats()`). "모든 데이터 지우고 처음부터" 확인 문구에 "다른 폰의 시연 설정은 그대로예요"를 덧붙인다.
- [ ] **Step 3:** `database.rules.json` — 스펙 5.8: 전 경로 `auth != null`; `/reservations/$uid`, `/users/$uid`는 `$uid === auth.uid`; `/seats/$seatId`는 키가 `^[A-D]-[0-9]+$`, 쓰기 시 `newData.child('uid').val() === auth.uid` 또는 삭제(`!newData.exists()`), 필드 `uid/resId/state/since/deadline`만, `state`는 WAITING|IN_USE|AWAY, `since/deadline`은 숫자; `/demo`는 필드·타입 검사. (좌석 ID 정규식은 `SeatLayout`의 실제 ID와 맞춘다.)
- [ ] **Step 4:** README 갱신: 폰 두 대 버전 소개, Firebase 콘솔 설정 순서(프로젝트, 앱 `com.g1j2.ssutable`, `app/google-services.json`, 익명 로그인, RTDB, 규칙 붙여넣기), 로컬 모드 설명, 두 폰 시연 순서, 두 폰 확인 체크리스트(예약 2초 반영, 동시 예약 한 명만, 시각 공유, 자동 반납, 초기화 시 RESET 화면, 앱 재시작 복원, 비행기 모드 ERROR/오프라인), 공개 저장소 위험과 대응, 담당 안내(이준호: `data/Firebase*`, 규칙 / 박준호: `ui/`), "지금 버전의 한계" 갱신.
- [ ] **Step 5:** Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest` Expected: BUILD SUCCESSFUL
- [ ] **Step 6: Commit** `feat(ui): connection status, seat reset; docs: rules and README`

---

### Task 9: GitHub 공개 저장소에 올리기

- [ ] **Step 1:** `gh repo create k1mg0eun/SSUTable --public --source=. --push` (이름이 이미 있으면 사용자에게 묻는다)
- [ ] **Step 2:** `gh repo view --web` 대신 `gh repo view k1mg0eun/SSUTable`로 확인, URL을 사용자에게 전달.

### Task 10 (사용자가 Google 로그인한 뒤): Firebase 콘솔 연결

- [ ] 브라우저 패널에서 프로젝트 생성(약관 동의 화면은 사용자 확인 후), Android 앱 `com.g1j2.ssutable` 등록, `google-services.json`을 `app/`에 저장, 익명 로그인 켜기, Realtime Database 생성(지역 asia-southeast1, 잠금 모드) 후 `database.rules.json` 붙여넣기.
- [ ] Run: `./gradlew :app:assembleDebug` Expected: BUILD SUCCESSFUL (플러그인 적용됨)
- [ ] Commit `chore: add Firebase config` 후 push. README의 RTDB URL이 실제 값과 맞는지 확인.
