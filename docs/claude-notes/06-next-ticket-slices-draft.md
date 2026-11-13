# 06 · Next ticket slices (DRAFT, nothing created on GitHub)

Drafted 2026-11-09. Deadline 13 Nov 2026. Issue numbers verified against `rusuh-app` (highest existing: #51 issue, #59 PR; new issues will be #60+ so "NEW-n" below are placeholders).
Rules: parents = docs FR-xx (read-only) or own `XXX-00` parent in rusuh-app; M1 component / M2 screen / M3 MVI on stub seam / M4 API (stays open, parked, BE not ready).
Pattern per steering: new code uses UseCases-only VMs, `ScreenContainer`/`NavEffect`; do not refactor old screens inside feature slices (see INFRA-6).
No NIK on the account; "Saya sendiri" prefills name only. Note: PR #55 (Home visual fidelity) is still open and PR #59 (My Queues) was open when checked.

## 1. Prioritised overview

Priority: P0 = needed for a demoable flow by 13 Nov, P1 = should have, P2 = after / if time.
Model: haiku simple, sonnet default, opus advanced (per memory rule).

| # | Proposed title | Parent | Type | Size | Depends on | Start now? | Model | Pri |
|---|---|---|---|---|---|---|---|---|
| 1 | NAV-01: Real routing Home -> Waiting Room -> Detail -> Payment (+ Detail in NavGraph) | FR-01/04/05/06 (wiring) | infra/M3 | M | #56-#59 merged | now | sonnet | P0 |
| 2 | FR-04-M3 #29 Waiting Room MVI (existing, widen scope to 6 states) | #26 | M3 | M | #57 | now | sonnet | P0 |
| 3 | FR-05-M3 #37 Detail MVI (existing, add name+NIK per ticket, login sheet, expiry) | #34 | M3 | L | #58 | now | opus | P0 |
| 4 | FR-APP-01-M3 #49 My Queues MVI (existing) | #46 | M3 | S | #59 | now | haiku | P1 |
| 5 | FR-01-M3 follow-up: Home state (queue strip, coming-soon reminder, event tap routing) | #17 | M3 | S | #1 | now | sonnet | P0 |
| 6 | FR-06-M1 #42 Payment Method component (drop on-hold) | #41 | M1 | S | none | now | haiku | P1 |
| 7 | FR-06-M2 #43 Payment Screen UI (re-scope to Figma states, drop on-hold) | #41 | M2 | M | #6, Q1 decision | now (after Q1) | sonnet | P1 |
| 8 | FR-06-M3 #44 Payment MVI (re-scope: Snap handoff, App Link return, verifying/expired) | #41 | M3 | M | #7 | now | sonnet | P1 |
| 9 | FR-06-M4 #45 Midtrans / payment sessions | #41 | M4 | L | BE | blocked BE | opus | P2 |
| 10 | AUTH-00 parent | - | parent | S | - | now | haiku | P0 |
| 11 | AUTH-00-M1 OTP code input component | AUTH-00 | M1 | S | - | now | haiku | P0 |
| 12 | AUTH-00-M2 Sign-in screens / sheet (5 states) | AUTH-00 | M2 | M | #11 | now | sonnet | P0 |
| 13 | AUTH-00-M3 Auth state + token storage seam | AUTH-00 | M3 | M | #12 | now | opus | P0 |
| 14 | AUTH-00-M4 requestOtp / verifyOtp / refresh / logout | AUTH-00 | M4 | M | BE | blocked BE | sonnet | P2 |
| 15 | TICKET-00 parent | - | parent | S | - | now | haiku | P1 |
| 16 | TICKET-00-M1 Ticket stub + QR component | TICKET-00 | M1 | M | - | now | sonnet | P1 |
| 17 | TICKET-00-M2 Tiket list + E-ticket screens | TICKET-00 | M2 | M | #16 | now | sonnet | P1 |
| 18 | TICKET-00-M3 Tickets state (MVI) | TICKET-00 | M3 | S | #17 | now | sonnet | P1 |
| 19 | TICKET-00-M4 listMyTickets / getTicket / PDF | TICKET-00 | M4 | M | BE | blocked BE | sonnet | P2 |
| 20 | ACCOUNT-00 parent | - | parent | S | - | now | haiku | P2 |
| 21 | ACCOUNT-00-M1 Section/row components | ACCOUNT-00 | M1 | S | - | now | haiku | P2 |
| 22 | ACCOUNT-00-M2 Account screen (no NIK) | ACCOUNT-00 | M2 | M | #21 | now | sonnet | P2 |
| 23 | ACCOUNT-00-M3 Account state (MVI) | ACCOUNT-00 | M3 | S | #22, #13 | now | sonnet | P2 |
| 24 | ACCOUNT-00-M4 getMe / email change (PATCH me, delete have no endpoint) | ACCOUNT-00 | M4 | S | BE | blocked BE | sonnet | P2 |
| 25 | FR-05 add-on: Dukcapil attendee validation UI states (inside #37/#36 follow-up) | #34 | M2/M3 | M | #3 | now | sonnet | P1 |
| 26 | INFRA-M5: Device registration (installId, token store) | EPIC-00 (closed) | infra | M | BE for M4 part | stub now, API blocked | sonnet | P2 |
| 27 | INFRA-M5b: Steering migration (MVIViewModel, ScreenContainer, NavEffect, UseCases) | EPIC-00 | infra | L | agree mapping | after P0 | opus | P2 |
| 28 | INFRA-M8: App attestation (Play Integrity / App Attest) | EPIC-00 | infra | L | BE | blocked BE | opus | P2 |
| 29 | INFRA: Secure token storage (Keychain/Keystore expect/actual) | AUTH-00 / EPIC-00 | infra | M | - | now | sonnet | P1 |
| 30 | INFRA: i18n runtime locale switch (ID/EN toggle becomes real) | EPIC-00-M10 | infra | M | - | now | sonnet | P1 |
| 31 | CHORE: Consolidate duplicate colour tokens + string cleanup | EPIC-00 | chore | S | #56-#59 | now | haiku | P1 |
| 32 | CHORE: Validate enum/mapper inputs instead of `require()` crashes | FR-01 / FR-APP-01 | chore | S | - | now | haiku | P1 |
| 33 | CHORE: Ticking ETA / "updated ago" via injectable clock | FR-04 / FR-APP-01 | chore | S | #2 | now | sonnet | P1 |
| 34 | CHORE: Home card `onActionClick` vs `onClick` semantics | FR-01 | chore | S | - | now | haiku | P2 |
| 35 | TEST: Compose UI tests + EN/ID string key parity test | EPIC-00 | chore | M | - | now | sonnet | P1 |
| 36 | CHORE: Figma visual pass (Home, Waiting Room, My Queues, Detail expired) | FR-01/04/05/APP-01 | M2 rework | M | Figma MCP quota | blocked on quota | sonnet | P1 |
| 37 | Pre-queue / paused / missed / sold-out states checked in MVI + UI (part of #2) | FR-04 | M3 | - | merged into #2 | - | - | - |

## 2. Draft bodies (new issues)

Common footer for every new body: `**Origin:** Team-added feature, no rusuh-documentation FR (decided 2026-11-09). Data layer (M4) PARKED: backend not ready; build UI + MVI on the stub seam.` Figma file `V1VQxA5Ckf82rMppnuMdSw` (use per-state child frame ids with `get_design_context`).

### 2.1 AUTH-00: Sign in (email OTP) [parent]

**Description.** Passwordless sign-in with a 6-digit code sent by email (ADR-0006). Used from three places: Detail login sheet when an admitted guest has `loginRequired`, "Masuk" banner in the Waiting Room, and the Account tab.

**Scope.**
- Email entry, code entry, wrong code (shows attempts left), code expired / locked, new account (asks full name).
- Reply never reveals if an account exists. Resend after 60 s (max 3 per 10 min). 5 attempts per code, code valid 10 min.
- Access token 15 min in memory, refresh token 30 days in secure storage, rotating.
- After login from the Waiting Room, `joinQueue` is called again to attach the account (M4 concern; keep a seam).

**Subtasks.** M1 OTP input component; M2 sign-in screens/sheet; M3 auth state + token storage seam; M4 API integration (PARKED).

**Origin / Documentation Reference.** ADR-0006; openapi `requestOtp`, `verifyOtp`, `refreshSession`, `logout`, `getMe`; hifi-api-review H-6; Figma Sign in section `52:640` (frame `52:336`).

**Out of scope.** Social login, password, biometrics, Turnstile (web only).

### 2.2 AUTH-00-M1: OTP code input component

**Description.** Stateless 6-digit code input (6 boxes, paste support, auto-advance, backspace, numeric keyboard).

**Scope / Figma.** Code `52:390`, wrong code `52:454`, code expired `52:526`. States: empty, filling, filled, error (shake + red outline, "Kode salah. Sisa N percobaan"), disabled.
**Acceptance criteria.**
- [ ] `OtpCodeInput(value, length=6, state, onValueChange, onComplete)` in `ui/components`, no state hoisting inside
- [ ] Paste of 6 digits fills all boxes; non-digits ignored
- [ ] Error and disabled visuals use design tokens
- [ ] Previews for each state; unit test for input filtering
- [ ] All strings via resources (EN + ID)
**Out of scope.** Timers, API.
**Dependencies.** None. Parent: AUTH-00.

### 2.3 AUTH-00-M2: Sign-in screens / sheet

**Description.** Compose screens for the five Figma states, usable full-screen and as a bottom sheet (Detail, Waiting Room).

**Scope / Figma.** Email `52:340`, Code `52:390`, Wrong code `52:454`, Code expired `52:526`, New account `52:596` (section `52:640`).
- Email field + consent text, "Kirim kode"; code screen with resend countdown (60 s); wrong code with attempts left; expired with "Kirim ulang kode"; new account asks full name.
- Placeholder data and local UI state only.
**Acceptance criteria.**
- [ ] `SignInScreen` + `SignInSheet` with the 5 states, previews for each
- [ ] Resend button disabled with visible countdown
- [ ] Generic wording that does not reveal if the account exists
- [ ] EN + ID strings; token-driven styling
**Out of scope.** ViewModel, API, token storage.
**Dependencies.** AUTH-00-M1. Parent: AUTH-00.

### 2.4 AUTH-00-M3: Auth state + token storage seam

**Description.** MVI auth flow on a stub repository plus a `TokenStore` seam.

**Scope.**
- `AuthContract` (steps: Email, Code, NewAccount, Done; error WRONG_CODE with `attemptsLeft`, CHALLENGE_EXPIRED, CHALLENGE_LOCKED; `resendAfterSeconds`), UseCases `RequestOtp`, `VerifyOtp`, `Logout`, `ObserveSession`.
- `SessionStore` (in-memory) + `TokenStore` interface (expect/actual later, see INFRA secure storage).
- `AuthRepository` stub (accepts code `123456`, rejects others, expires on demand).
**Acceptance criteria.**
- [ ] Reducer unit tests: happy path, wrong code x5 -> locked, expiry, resend timing, new account
- [ ] Session exposed as a Flow for Detail / Waiting Room / Account
- [ ] Koin wiring
**Out of scope.** Real HTTP, Keychain/Keystore.
**Dependencies.** AUTH-00-M2. Parent: AUTH-00.

### 2.5 AUTH-00-M4: Auth API integration (PARKED)

Implement `requestOtp`, `verifyOtp`, `refreshSession` (rotating, single-flight refresh on 401), `logout`, `getMe` with Ktor auth plugin; map error codes WRONG_CODE / CHALLENGE_EXPIRED / CHALLENGE_LOCKED / RATE_LIMITED. Send `consentVersion`, `locale`, `fullName` (new only). PARKED: contract draft 0.3.0, BE not ready. Depends on AUTH-00-M3 + secure token storage + device registration.

### 2.6 TICKET-00: My tickets + E-ticket [parent]

**Description.** Tiket tab lists purchased tickets; E-ticket page shows QR, ticket code, holder name, masked NIK, gate, seating, with PDF download and add-to-calendar (calendar is client side from `startsAt`).
**Figma.** E-ticket section `5:1013` (frame `5:859`, state `5:863`). Tiket list has no Figma frame yet (Q4).
**Reference.** openapi `listMyTickets`, `getTicket`, `downloadBookingTickets` (PDF only when PAID); ADR-0007 (NIK masked everywhere); FR PAID-> issued, ADR-0009.
**Subtasks.** M1 stub/QR component; M2 list + e-ticket screens; M3 state; M4 API (PARKED).

### 2.7 TICKET-00-M1: Ticket stub + QR component

**Scope.** `TicketStub` (event, category, seating, gate, holder, `nikMasked`, code) and `QrCode` composable rendering `qrPayload` (pick a KMP-capable QR lib or small own encoder; decision noted in the PR). Perforated-stub look per Figma `5:863`.
**Acceptance criteria.**
- [ ] QR renders deterministically from payload on Android and iOS, minimum 4 modules/quiet zone, high contrast
- [ ] Stub states: valid, used (greyed), expired event
- [ ] NIK shown masked only; strings via resources; previews + tests for QR matrix generation
**Out of scope.** Brightness boost, screen capture protection. **Dependencies.** None.

### 2.8 TICKET-00-M2: Tiket list + E-ticket screens

**Scope.** `TicketsScreen` (grouped by event, empty state "Belum ada tiket" with path to Events) replacing the `Screen.Tickets` stub; `ETicketScreen` with QR, ticket code, details, buttons "Unduh PDF" and "Tambah ke kalender" (no-op seams). Multi-ticket booking: pager or list of stubs.
**Acceptance criteria.**
- [ ] Both screens with placeholder data, previews (list, empty, single, three tickets)
- [ ] Back navigation and bottom bar behaviour correct
- [ ] EN + ID strings
**Dependencies.** TICKET-00-M1; wiring in NAV-01.

### 2.9 TICKET-00-M3: Tickets state (MVI)

`TicketsContract` (Loading/Ready/Empty/Error), `ETicketContract`, UseCases `GetMyTickets`, `GetTicket`; stub repository returning sample tickets; effects: `OpenTicket(id)`, `DownloadPdf` (seam), `AddToCalendar` (seam, platform later). Reducer tests. **Dependencies.** M2.

### 2.10 TICKET-00-M4: Tickets API (PARKED)

`listMyTickets`, `getTicket`, PDF download (409 BOOKING_NOT_PAID handling), map `Ticket` DTO -> domain. Offline cache of last tickets recommended (QR must open without network at the gate; ask product). BE not ready.

### 2.11 ACCOUNT-00: Account without NIK [parent]

**Description.** Account tab (Akun): profile (name + email, no NIK), Masuk section (email, "Ganti email", OTP explainer), "Email dari Rusuh" preferences, ID/EN toggle, Keluar, Hapus akun. Signed-out state shows "Masuk" CTA.
**Figma.** Mobile Profile `60:2806` (section `60:3128`, frame `60:2802`). Frames `60:2910` (edit name & NIK) and `60:3021` (no NIK yet) are NOT built; adapt per notes 05 "Account adaptation".
**Reference.** ADR-0007 (NIK per ticket), openapi `getMe`, `startEmailChange`, `verifyEmailChange`. No endpoint for profile update, delete account, marketing preference.
**Subtasks.** M1 rows/sections; M2 screen; M3 state; M4 API (PARKED).

### 2.12 ACCOUNT-00-M1 / M2 / M3 / M4

- **M1 section/row components.** `AccountSection(numberChip, title)`, `AccountRow(label, value, trailing)`, `AccountToggleRow`, `DangerLink`. Token-driven, previews. Dependencies: none.
- **M2 Account screen.** Signed-in and signed-out variants; section 1 renamed "Profil" with Nama + Email only; Masuk; email preferences (locked "selalu dikirim" + "Presale dan event baru"); language pill; Keluar; Hapus akun (disabled/hidden until endpoint exists). Note "NIK diisi per tiket saat memesan; KTP dicek di pintu masuk". Acceptance: no NIK string/field anywhere, EN + ID. Dependencies: M1.
- **M3 Account state.** `AccountContract`, `GetMe` / `Logout` UseCases, observes the AUTH-00-M3 session, effects `OpenSignIn`, `ConfirmLogout`, `ChangeLanguage`. Dependencies: M2, AUTH-00-M3.
- **M4 Account API (PARKED).** `getMe`, email change two-code flow. Name edit, marketing preference, delete have NO endpoint: raise with BE; keep hidden.

### 2.13 NAV-01: Real routing across the booking flow (wiring)

**Description.** Wire the existing screens into `NavGraph` with real routes and arguments so the demo path works on stubs.
**Scope.**
- Add `Screen.Detail(eventId)`, `Screen.WaitingRoom(eventId)`, `Screen.Payment(bookingId)`; tabs unchanged.
- Home event card tap by event state: coming_soon no nav (reminder only), waiting_room_open/on_sale/almost_gone -> Waiting Room, sold_out/ended disabled.
- Waiting Room `NavigateToDetailPage` effect -> Detail (replace, so back does not return into an admitted queue); My Queues row -> Waiting Room.
- Detail `NavigateToPayment` -> Payment; payment success -> E-ticket (when TICKET-00 exists, else placeholder); Detail/Payment expired -> back to Home with message.
- Back stack rules, bottom bar visibility (hidden on Detail/Payment).
**Acceptance criteria.**
- [ ] Path Home -> Waiting Room -> Detail -> Payment navigable on stubs on Android and iOS
- [ ] Route-argument tests and tab-mapping tests updated
- [ ] No ViewModel creates navigation itself (effects only)
**Dependencies.** #56-#59 merged; #29/#37/#44 for real effects (can start with placeholder buttons).

### 2.14 INFRA follow-ups (bodies, short)

- **INFRA-M5 Device registration.** Generate `installId`, store with Keystore/Keychain, `DeviceRepository` seam + stub, `X-Device-Token` header interceptor (30 d refresh). M4 part (`POST /devices`) PARKED. AC: install id survives restart, interceptor adds header, tests. Depends on secure storage.
- **INFRA: Secure token storage.** `expect/actual SecureStorage` (Android Keystore + EncryptedSharedPreferences/DataStore, iOS Keychain), used by `TokenStore`. AC: round-trip tests, cleared on logout. No BE dependency.
- **INFRA-M8 Attestation.** Play Integrity / App Attest provider behind an interface; produces `attestation` (register device) and `X-App-Attestation` (payment sessions). Stub token in debug. PARKED on BE.
- **EPIC-00-M5b Steering migration.** Agree Rusuh mapping first (wcms -> `stringResource`, `DX.Color` -> `RusuhColors`), then migrate `MviViewModel<S,I,E>` -> `MVIViewModel<Event,State>`, add `ScreenContainer`, shared `NavEffect`/`UiEffect`, UseCases, module layout per screen. Do incrementally AFTER the P0 flow; one screen per PR. AC: agreed mapping doc, Home migrated as the reference, others tracked.
- **EPIC-00 i18n locale switch.** ID/EN toggle currently only visual; implement runtime locale override (Compose resources) + persist in local prefs, send `Accept-Language`. AC: toggle updates all visible strings without restart, persists.
- **EPIC-00 string/token cleanup.** Hardcoded tab labels -> resources; remove duplicate colour tokens (Home `Paper/Ink/Pink` + aliases, Detail `Brand*`, My Queues `RusuhBrandColors`) in favour of `RusuhColors`; bottom bar restyle finished.

### 2.15 Tech-debt tickets surfaced by review of #56-#59 (bodies, short)

(Please re-check each against the actual review comments before filing; this list is from the brief and notes, not from the PR diffs.)
- **Validate in mappers, not `require()`.** `EventStatus` / `QueueListItemUi` must not crash on bad mapper data; return a safe fallback status (or drop item) and log. AC: mapper tests with unknown enum / negative values.
- **Ticking ETA / updatedAgo.** Drive from an injectable `Clock`/ticker in the VM (1 s tick when visible), never from the device clock for countdowns that have `expiresAt`/`serverNow`. AC: virtual-time tests.
- **Home card `onActionClick` vs `onClick`.** Clarify: card body opens event, action button performs CTA; avoid double handling. AC: UI test + doc comment.
- **Test gaps.** Compose UI tests (at least Home, Waiting Room states, Detail steppers, My Queues empty) and a unit test asserting `values/strings.xml`, `values-id/strings.xml`, and `values-in/strings.xml` have identical key sets, plus `values-id/` and `values-in/` have identical content.
- **Figma visual pass.** When the Figma MCP quota resets, redo Home `4:1544`, Waiting Room `5:266` (six states), My Queues (use Waiting Room language, no Figma frame found; confirm) and Detail expired `60:2395` against real frames, via `get_design_context` per child frame. Detail Booking `60:2093` was already built from Figma.
- **Duplicate colour tokens** and **language toggle visual only**: covered by INFRA items above.

## 3. FR-06 payment (existing #41-#45): what to change

Current state: #42 and #43 carry `on-hold`; #41/#44/#45 open. Bodies describe own QRIS/VA screens and an in-app poll, which conflicts with ADR-0008 (Snap hosted page).
Proposed handling (decide Q1 first):
- **#42 M1 and #43 M2: remove `on-hold`** (UI is not blocked by BE). #42 stays "method choice" only if the app picks the method (optional `method` on `startSnapPayment`: qris, gopay, shopeepay, bca_va, mandiri_va, bni_va, bri_va, permata_va, cimb_va, card). #43 states from Figma: Choose method `5:490`, Virtual account `5:589`, QRIS `5:662`, Verifying `5:741`, Payment expired `5:790`; Snap reference frames (`54:1157`, `54:1272`, `54:1410`, `54:1516`) are reference for the web page only. Add edit: remove "check status" polling button, add "Lanjutkan pembayaran" (resume).
- **Option A (Snap only, ADR-0008):** app screens = Choose method (optional) -> "Buka halaman pembayaran" -> Verifying (after App Link return) -> Paid / Expired. VA and QRIS frames serve as web-page reference only.
- **Option B (own VA/QRIS, Core API):** needs an ADR-0008 amendment and BE support; bigger.
- **#44 M3:** re-scope phases to `selecting / handoff(opening) / waitingReturn / verifying / paid / expired / cancelled`; 10-min timer from `expiresAt - serverNow`; effects `OpenPaymentPage(url)`, `NavigateToTicket`, `NavigateCancelled`; handle App Link return as intent `ReturnedFromPayment(bookingId)` that always triggers `getBooking`; resume via `getActiveBooking`.
- **#45 M4:** `createPaymentSession` (Bearer + device token + `X-App-Attestation`), open `payUrl#code=` in Custom Tabs / SFSafariViewController, App Link `https://app.rusuh.id/payment-result?bookingId=...` (assetlinks.json / apple-app-site-association is a BE+hosting task), `getBooking` as truth; stays open, parked.
- **PAY-00 (optional new issue)** instead of extending #41: only if M4 handoff needs a separate parent. Recommendation: keep under #41 and add one new slice **FR-06-M5: App Link return handling (Android intent filter + iOS universal link) on a stub** (S/M, sonnet, can start now since it is client-only).
- Copy: expired/refund text per ADR-0009 ("akan kami kembalikan", not "otomatis").

## 4. Existing issues after PRs #56-#59 merge

PR state when checked: #56, #57, #58 MERGED; #59 OPEN (verify before acting); #55 OPEN (Home visual fidelity; may overlap #56, close or merge).
Per user, #18 #19 #27 #28 #35 #36 #47 #48 were reopened for the Figma gap.

| Issue | Action after merge | Note |
|---|---|---|
| #18 FR-01-M1 Event Card | close with comment "PR #56; remaining Figma pass tracked in NEW Figma visual pass" | |
| #19 FR-01-M2 Home UI | close (PR #56), same note | |
| #27 FR-04-M1 Queue Position | close (PR #57) | |
| #28 FR-04-M2 Waiting Room UI | close (PR #57); six states done | |
| #35 FR-05-M1 Seat Category | close (PR #58) | |
| #36 FR-05-M2 Detail UI | close (PR #58) after checking "Profile without NIK" removed and the login-sheet state is a separate slice | |
| #47 FR-APP-01-M1 | close when #59 merges | |
| #48 FR-APP-01-M2 | close when #59 merges | |
| #20 FR-01-M3 Home MVI (closed) | stay closed, but add comment: state shape changes (queue strip, routing) tracked in Home M3 follow-up | |
| #29 FR-04-M3 | **not done, partly prepared**: UI has six states, so Contract must model preopen/waiting/paused(reason stock vs operator)/yourTurn(60 s)/missed/soldOut. Update body (currently only Waiting/Admitted/Closed) | |
| #37 FR-05-M3 | not done; update body: name + NIK per ticket, "Saya sendiri" prefill name only, login sheet, 5-min expiry, adminFee per order, effects | |
| #49 FR-APP-01-M3 | not done; UI done, small body edit (frontier-derived fields, updatedAgo ticking) | |
| #42/#43 | remove `on-hold`, edit per section 3 | |
| #50 | keep `on-hold` (BE) | |
| #21 #30 #38 #45 | stay open, parked | |

Note on "Detail Booking built from Figma, others from old hi-fi": rework remains tracked by the Figma visual pass ticket, so closing the M1/M2 issues is not claiming pixel parity.

## 5. Open questions (need a human before creating)

1. **Payment scope:** Snap hosted page only (Option A, ADR-0008, recommended) or own VA/QRIS screens (Option B)? Figma has both.
2. **Detail "Profile without NIK" (60:2241)** and the "Dari profil Anda" prefilled NIK: delete the state and ask Design to update Figma? (Notes already decided: no NIK on account.)
3. **Names/numbering:** `AUTH-00`, `TICKET-00`, `ACCOUNT-00` (use `FR-APP-02/03/04` for consistency with `FR-APP-01` instead?); `PAY-00` vs extending #41 (recommended); `NAV-01` and INFRA numbers (EPIC-00-M11+ since M10 is used?).
4. **Tiket tab list:** no Figma frame; build from E-ticket stub style or ask Design?
5. **QR library** for KMP and offline caching of tickets for gate entry.
6. **Hide or disable** Account items with no endpoint (edit name, marketing preference, delete account)? Raise endpoints with BE.
7. **"Kirim e-tiket ke"** on Detail: read-only account email + "Ganti" link (recommended) vs editable field.
8. **Steering migration timing:** do EPIC-00-M5b before or after the 13 Nov demo? Recommendation: after P0, one reference screen first.
9. **Close vs keep open** #18..#48 after merge given the Figma pass is still pending.
10. **Reminders ("Ingatkan saya") and push near turn (FR-27):** in scope for the deadline or stretch?
11. **Docs ADR** recording KMP decision (docs repo allows new files only): create ADR-0016?
12. Which of #55 (open Home fidelity PR) and #56 is canonical.
13. Min OS assumption (Android 26 / iOS 15) confirm.

## 6. Suggested order for the 4 days

Day 1: NAV-01, #29, #37 (parallel agents), AUTH-00 parent + M1. Day 2: AUTH-00-M2/M3, #44 re-scope + #42/#43 UI, Home M3 follow-up. Day 3: TICKET-00 M1-M3, #49, secure storage, i18n locale switch. Day 4: integration pass on a device/simulator, Figma visual pass if quota back, ACCOUNT-00 if time, tests, docs. M4 items untouched.

---

## 7. Changes from the 2026-11-09 Figma exports (user: "follow the new screenshots")

Source and per-state reading: `figma-exports/2026-11-09/README.md` (12 slices: Ticket Detail x3, Payment x5, Midtrans Snap x4). Waiting Room export was reviewed separately (image in the chat, six states).
Still nothing created on GitHub; these edit the draft above.

### 7.1 Payment (FR-06 #41-#45): follow the Payment frames as app screens
Answers draft Q1 in the direction of the screenshots: the five Payment frames are built as our own screens (UI + stub only, BE parked). ADR-0008 still says the method is chosen on Snap, so **which screens are shown at runtime is a flag decided later** (Snap handoff vs own VA/QRIS). Replace section 3 option A/B with this split of #42/#43 (remove `on-hold`):

| Slice (re-scope of) | Figma frame | Content |
|---|---|---|
| FR-06-M1 components (#42) | Payment 5:490-5:790 | `PaymentMethodOption` (radio, group headers PALING CEPAT / E-WALLET / VIRTUAL ACCOUNT 2-col grid / KARTU), `OrderSummaryBox` ("Pesanan" with order no, line items with masked NIK, `Biaya layanan`, total, footer), `InstructionCard` (blue shadow; VA number + `Salin`, QR card + `Simpan QR`), status dot line, stamp badges (`WAKTU HABIS`, `BELUM DIBAYAR`), reuse `CountdownChip` |
| FR-06-M2a Choose method (#43) | 5:490 | picker, sticky `Bayar Rp ... · QRIS →` |
| FR-06-M2b Virtual account (#43) | 5:589 | BCA VA card, copy, instructions, auto-advance note |
| FR-06-M2c QRIS (#43) | 5:662 | QR card, `Simpan QR`, instructions |
| FR-06-M2d Verifying (#43) | 5:741 | spinner, `Memastikan pembayaran Anda...`, "do not pay again" |
| FR-06-M2e Payment expired (#43) | 5:790 | `Pesanan dibatalkan.`, refund box (ADR-0009), `Antre lagi →` / `Ke beranda` |
| FR-06-M2f Handoff / popup closed (new, #43) | 54:1516 | `Bayar lewat Midtrans`, `BELUM DIBAYAR`, `Lanjutkan pembayaran →`, `Ubah pesanan` |
| Snap frames 54:1157, 54:1272, 54:1410 | Midtrans-owned page | reference only, not built |
| #44 M3 / #45 M4 / FR-06-M5 App Link return | as in section 3 | unchanged |

### 7.2 Ticket Detail (FR-05)
Merged #58 already matches the Booking frame (timer, event header, numbered steps, name+NIK per ticket, duplicate NIK, email block, fee per order, expired modal). Remaining deltas, as small tickets:
- **FR-05-M2b Profile variant (60:2241):** name prefilled with help `Dari profil Anda.` and NIK help text per decision (see Q-A below); no `Simpan NIK ke profil` checkbox unless Q-A says otherwise.
- **Expired frame:** keep our header-only backdrop (no PII behind the scrim); ask Design for a clean export (buttons unreadable).
- #37 M3 body unchanged (name + NIK, Saya sendiri prefills name).

### 7.3 Waiting Room visual pass (new slice, FR-04-M2b, follow-up of #28)
From the Waiting Room export vs the emulator build: ID/EN pill in the top bar (`Ruang tunggu`), real event poster data (name, tag, date, venue) instead of the route id (NAV-01 + M3), progress track with "now serving / your number" labels in the waiting state, `± N menit lagi` wording in ID, your-turn chip (`0:52 untuk masuk`), sold-out HABIS stamp and `Kabari saya jika ada pembatalan tambahan` link. Check each of the six states against the real frames when the Figma tool works.

### 7.4 Account
The Account export still shows NIK ("Data sesuai KTP", `Isi NIK →`). We keep **no NIK on the account** (user decision 2026-11-09) and adapt layout; flagged in Q-A.

### 7.5 Questions raised by the new screenshots
- **Q-A:** the new Detail/Account frames keep a profile NIK ("Dari profil Anda", `Simpan NIK ke profil`, `Isi NIK →`). Do we now follow them (needs a profile NIK endpoint that is not in OpenAPI 0.3.0) or keep the earlier "no NIK on account" decision? Draft assumes **keep no NIK** until you say otherwise.
- **Q-B:** fee model: Snap frames show per-ticket `Biaya platform Rp9.009 x 3` + PPN 11%; Payment frames and ADR-0010 show one `Biaya layanan Rp 30.000` per order. Draft follows the order-level fee.
- **Q-C:** show own VA/QRIS screens at runtime, or only Snap (flag)?

Issues to update when creating: #42, #43 (re-scope per 7.1, remove `on-hold`), #36 (close note: profile variant tracked in FR-05-M2b), #28 (close note: visual pass in FR-04-M2b).
