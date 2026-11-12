# 04 · GitHub status and Figma

## Issue model

- **Parents:** `PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation` issues #1–#25 = FR-01…FR-25, #26–#35 = NFR-01…10,
  #36–#43 = BR-01…08. All **open**, none sliced there. **Read-only for us: do not comment, label or close.**
- **Slices:** `PT-Meeting-Sampai-Malam-Konsultasi/rusuh-app` issues, titled `FR-xx-Mn` (see 03). Each slice cites the doc issue.
- **Standing decisions (user, 2026-11-09):**
  1. Several UI slices were closed but the **UI is still far from Figma** → those issues must be **reopened** (not done yet, waiting for the user's go-ahead because it is visible to the team).
  2. **BE is not ready** → every API-integration slice stays **open**.

## `rusuh-app` issues (checked 2026-11-09; no open PRs)

| # | State | Title | Note |
| --- | --- | --- | --- |
| 1–8 | closed | EPIC-00 M1–M7 (scaffold, CI, networking, Koin, MVI, navigation, tokens) | done |
| 33 | closed | EPIC-00-M8 brand fonts | done |
| 51 | closed | EPIC-00-M10 i18n resources | done (PR #54, merged on origin, not yet pulled locally) |
| 23 | closed | iOS project + CI | done |
| 17 | open | FR-01 Home (parent) | |
| 18 | closed | FR-01-M1 Event Card | **reopen candidate (Figma)** |
| 19 | closed | FR-01-M2 Home Screen UI | **reopen candidate** |
| 20 | closed | FR-01-M3 Home MVI | keep closed unless state shape changes |
| 21 | open | FR-01-M4 Home API integration | BE |
| 26 | open | FR-04 Waiting Room (parent) | |
| 27 | closed | FR-04-M1 Queue Position Component | **reopen candidate** |
| 28 | closed | FR-04-M2 Waiting Room Screen UI | **reopen candidate** (Figma has 6 states: pre-queue, waiting, paused, your turn, missed, sold out) |
| 29 | open | FR-04-M3 Waiting state MVI | UI side, can proceed against stub |
| 30 | open | FR-04-M4 Queue API / realtime | BE |
| 34 | open | FR-05 Detail Ticket (parent) | |
| 35 | closed | FR-05-M1 Seat Category Component | **reopen candidate** |
| 36 | closed | FR-05-M2 Detail Screen UI | **reopen candidate** (Figma: name+NIK per ticket, Saya sendiri/Orang lain, "Kirim e-tiket ke", booking-expired state) |
| 37 | open | FR-05-M3 Detail MVI | |
| 38 | open | FR-05-M4 Detail/booking API | BE |
| 41 | open | FR-06 Payment (parent) | |
| 42 | open `on-hold` | FR-06-M1 Payment Method Component | UI is not blocked by BE → `on-hold` label probably should go |
| 43 | open `on-hold` | FR-06-M2 Payment Screen UI | same; Figma states: choose method, VA, QRIS, verifying, expired (+ Snap frames) |
| 44 | open | FR-06-M3 Payment MVI (stub) | |
| 45 | open | FR-06-M4 Midtrans integration | BE, parked |
| 46 | open | FR-APP-01 My Queues (parent, team-added, assessor approved) | no doc FR |
| 47 | closed | FR-APP-01-M1 Queue List Item | **reopen candidate** |
| 48 | closed | FR-APP-01-M2 My Queues Screen UI | **reopen candidate** |
| 49 | open | FR-APP-01-M3 My Queues MVI | |
| 50 | open `on-hold` | FR-APP-01-M4 Queues API | BE |

### Own parent issues for features with no FR in the docs (decided 2026-11-09)

Create them in `rusuh-app` only (never in the docs repo), same style as `FR-APP-01` (#46): a parent `XXX-00` with M1–M4 slices,
"Origin: team-added, no rusuh-documentation FR", UI-first, M4 left open (BE not ready). **Not created yet — waiting for the user's go-ahead.**

| Proposed parent | Covers | Figma | Slices (UI-first) |
| --- | --- | --- | --- |
| `AUTH-00` Sign in (email OTP) | Email, Code, Wrong code, Code expired, New account (+ login sheet on Detail, "Masuk" banner in waiting room) | 52:340–52:596 | M1 OTP code input component, M2 sign-in screens/sheet, M3 auth state + token storage seam, M4 `requestOtp`/`verifyOtp`/refresh (BE) |
| `TICKET-00` My tickets + E-ticket | Tiket tab list and the E-ticket page (QR, ticketCode, PDF, add to calendar) | 5:863 | M1 ticket stub/QR component, M2 list + e-ticket screens, M3 state, M4 `listMyTickets`/`getTicket`/PDF (BE) |
| `ACCOUNT-00` Account (no NIK) | Profile, Masuk, email preferences, language toggle, logout, delete | 60:2806 (mobile), 60:2491 (web reference) | M1 section/row components, M2 screen, M3 state, M4 `getMe`/email change/(PATCH me, delete: no endpoint yet) |
| `PAY-00` Payment handoff + return | Open Snap system tab, App Link return, verifying/expired, resume via active booking; may extend FR-06 (#41) instead | 54:1157–54:1516, 5:490–5:790 | could stay under FR-06 |
| `INFRA-M5/M8` follow-ups | device registration, secure token storage, attestation | — | `EPIC-00` style |

Numbering note: `AUTH-00`, `TICKET-00` (user's examples) then `ACCOUNT-00`; confirm names before creating.

**No issues exist yet** for things Figma and the task-slicing doc cover: Login/OTP (Auth), E-ticket (Ticket Purchased),
Account, Snap handoff / payment return, Tiket tab, device registration + secure storage (INFRA-M5), attestation
(INFRA-M8), pre-queue/paused/missed/sold-out states if kept separate. The task slicing HTML counts 47 tasks; only ~33
exist as issues. FR-05 `DetailRepository`, FR-06 etc. need FR-xx parent cross-links added by whoever slices them.

Parent FR-xx numbering used in `rusuh-app` titles is **not** the doc issue numbers for all (e.g. app "FR-05 Detail Ticket" ↔ doc
FR-11/12/13 entry FR-05; app "FR-06 Payment Page" ↔ doc FR-20/21/23). Check the "Documentation Reference" section of each issue.

## Figma (`V1VQxA5Ckf82rMppnuMdSw`, "Rusuh-Tiket-App"; mobile only)

URL: https://www.figma.com/design/V1VQxA5Ckf82rMppnuMdSw/Rusuh-Tiket-App — one page ("Page 1"), desktop sections at top,
mobile row at y≈7796. Each mobile section is a 390-wide frame stacking the states vertically. Node ids:

| Flow | Section id | States (child frame ids) |
| --- | --- | --- |
| Home | 4:1544 (frame 4:1397) | Event list (4:1401 / 4:1402) |
| Waiting Room | 5:266 (5:2) | Pre-queue 5:6 · Waiting 5:55 · Paused 5:108 · Your turn 5:155 · Missed turn 5:194 · Sold out 5:229 |
| Ticket Detail | 60:2486 (60:2089) | Booking 60:2093 · Profile without NIK 60:2241 · Booking time expired 60:2395 |
| Payment | 5:843 (5:486) | Choose method 5:490 · Virtual account 5:589 · QRIS 5:662 · Verifying 5:741 · Payment expired 5:790 |
| Midtrans Snap | 54:1570 (54:1153) | Snap methods 54:1157 · order details 54:1272 · QRIS 54:1410 · popup closed 54:1516 |
| E-ticket | 5:1013 (5:859) | E-ticket 5:863 |
| Sign in | 52:640 (52:336) | Email 52:340 · Code 52:390 · Wrong code 52:454 · Code expired 52:526 · New account 52:596 |
| Account | 60:3128 (60:2802) | Profile 60:2806 · Edit name & NIK 60:2910 · No NIK yet 60:3021 |

Design language (seen in screenshots): cream background, heavy black outlines + hard drop-shadows, Bricolage Grotesque
display / Space Grotesk body, hot-pink accent (#e0147a-ish), black section number chips ("1 Kategori"), ID/EN pill toggle
in header, 4-tab bottom bar (Event · Antrian · Tiket · Akun), countdown chip top right of Detail (e.g. 4:12), sticky
bottom CTA bar with total ("3 tiket · Rp 2.180.000 · Lanjut bayar →").

**Implementation caveat:** before generating code from Figma, load the `figma:figma-design-to-code` skill and use
`get_design_context` per state frame (use the child section ids above, not the label text layers). The whole page
metadata is ~600 KB; don't fetch it again — query per node. A session id exists for this task but a new
session should be created per task.

## UI rework status (2026-11-09)

Issues #18 #19 #27 #28 #35 #36 #47 #48 were **reopened** (comment links the Figma file). Four sub-agents reworked the UI in worktrees
under `~/Projects/Rusuh-booking-ticket-app/worktrees/` (branches `feat/fr-01-m1-m2-figma-ui`, `feat/fr-04-m1-m2-figma-ui`,
`feat/fr-05-m1-m2-figma-ui`, `feat/fr-app-01-m1-m2-figma-ui`; each has one local commit), merged into
**`feat/figma-ui-integration`** (`worktrees/integration`, from origin/main 5c14995, local only, **not pushed, no PR**).
Verified on that branch: `:shared:allTests` 264 tests / 0 failures, ktlintCheck + detekt pass, `:androidApp:assembleDebug` + unit tests pass.
**Figma MCP hit the Starter-plan call limit**, so only the Detail *Booking* state was built from Figma; Home, Waiting Room, My Queues and Detail *Expired*
follow the old hi-fi (`rusuh-documentation/analysis/hifi`). **Redo a visual pass against Figma when the quota resets.**
Cleanup debt: duplicate colour tokens (Home `Paper/Ink/Pink…` + aliases, Detail `Brand*`, My Queues `RusuhBrandColors`), bottom bar partly restyled,
tab labels hardcoded, `DetailScreen` not wired into navigation, nothing run on a device/simulator.
Worktrees `worktrees/home|waiting|detail|queues|integration` can be removed after merge (`git worktree remove`).
