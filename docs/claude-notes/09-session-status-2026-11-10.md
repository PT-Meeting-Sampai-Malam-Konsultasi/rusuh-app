# 09 · Status note, 2026-11-10 (review loop stopped by the user, nothing else merged)

Repo `PT-Meeting-Sampai-Malam-Konsultasi/rusuh-app`. Deadline 13 Nov 2026. Payment UI is the Midtrans web page (no native payment screens).

## Merged today
- **#60** TICKET Tiket tab UI + stubbed `GetMyTicketsUseCase` (3 review rounds, CI green). Merge commit d8cc66e.
- **#86** FR-22-M3 (#72) Tickets state (MVI) (1 review round, CI green). Merge commit 552b417. Issue #72 closed.

## Open PRs (all reviewed clean, NOT merged, all currently CONFLICTING with main)
| PR | Ticket | Branch | Review | CI | Screenshots |
| --- | --- | --- | --- | --- | --- |
| #87 | FR-APP-01-M3 (#49) My Queues MVI | feat/fr-app-01-m3-my-queues-mvi | 2 rounds, no High/Medium left | iOS `build` still pending, rest green | attached |
| #88 | FR-04-M3 (#29) Waiting Room MVI | feat/fr-04-m3-waiting-room-mvi | 2 rounds, no High/Medium left | iOS `build` pending, rest green | attached |
| #89 | AUTH-00-M1+M2 (#75 #76) OTP input + sign-in sheet UI | feat/auth-00-m1-m2-figma-ui | 2 rounds, no High/Medium left | not started after last push | attached (2) |
| #90 | FR-05-M3 (#37) Detail MVI (select items + NIK input) | feat/fr-05-m3-detail-mvi | 3 rounds, no High/Medium left | not started after last push | attached (2) |

Review comments and fix summaries are posted on each PR. Screenshots are on branch `pr-screenshots` under `mvi-2026-11-10/`.

### What is left to merge them
1. **Conflicts.** #86 changed `NavGraph.kt`/`AppModule.kt`; #87-#90 all touch `NavGraph.kt`, `AppModule.kt`, both `strings.xml`; #88 adds `clear()` and #90 changes the dispatcher to `Main.immediate` in the shared `mvi/MviViewModel.kt`; #89 also touches `shared/build.gradle.kts`. Rebase each onto `origin/main` in this order: #87 -> #88 -> #90 -> #89 (merge one, rebase the next, re-run the check command).
2. **CI.** The iOS `build` job takes ~14-16 min per run; every rebase restarts it. Wait for all checks green before merging (merge commit style, as for earlier PRs).
3. After a rebase of #88/#90, re-check the `MviViewModel` combination (`clear()` + `Main.immediate`) and that the effect/state tests still pass.
4. Implementer agents (resumable by id if still available): tickets MVI a413849dcd65cd0dd (done), my queues a1cd9c53b4636aab5, waiting room a8647da970ce9c747, auth UI a6e78ecb4925e564b, detail a1cebff8674bd0015. Their worktrees: `Projects/Rusuh-booking-ticket-app/rusuh-app/.claude/worktrees/agent-<id>`.

## Not started
- **#77 AUTH-00-M3** auth state (MVI): depends on #89 merged.
- **#71 FR-22-M2** e-ticket page UI (hi-fi only; Figma export of 5:863 missing, Figma MCP is rate-limited).
- All API tickets (#78-#85, EPIC-01-M1..M7 #62-#68) and `FR-22-M4` #73.

## Issues created today (rusuh-app)
- **EPIC-01 data layer:** parent #61; M1 #62 result types/ApiService pattern, M2 #63 environments, M3 #64 HTTP plugins, M4 #65 secure storage + cache, M5 #66 device identity, M6 #67 auth session data layer, M7 #68 stub switch/test harness.
- **FR-22:** parent #69; M1 #70 (closed, done in #60), M2 #71 e-ticket page UI, M3 #72 (closed by #86), M4 #73 Tickets API.
- **AUTH-00:** parent #74; M1 #75, M2 #76 (both in PR #89), M3 #77.
- **FR API tickets (parent = doc FR):** FR-04-M5 #78 join queue, FR-08-M1 #79 claim turn, FR-15-M1 #80 NIK validation, FR-16-M1 #81 create booking + hold, FR-17-M1 #82 booking countdown, FR-18-M1 #83 idle expiry/cancel, FR-21-M1 #84 payment countdown, FR-25-M1 #85 payment status + expired.
- **Existing tickets edited (reference notes appended only):** #17, #21, #26, #30, #34, #38, #41, #45. #30/#38/#45 were described as narrowed after the split; their original bodies are unchanged.

## Things to know / follow-ups
- **Literal backslash bug (pre-existing):** strings written as `\'` in `strings.xml` render with a visible backslash (e.g. "You\'re already in."). Compose resources 1.7.0 does not unescape it here. Affects older strings from earlier PRs; worth a small chore ticket (replace with plain `'` or `&apos;`/`’`).
- **Snackbar blocks the effect collector** (`showSnackbar` inside `collect`) still exists in #87 and #88's routes (rated Low there because nothing reachable triggers it yet); #90 already fixed it. Apply the same one-liner when those routes get real Refresh.
- **Screenshot-only local builds** (never committed): #89 forced `isSignedIn = { false }` in `AppModule`; #90 fed `homePreviewEvents()` into `StubHomeRepository`. Both reverted with `git checkout`; PR bodies say so. The signed-out Tiket state and Home events are not reachable in the real app until auth (#67/#77) and Home API (#21) land.
- **Figma MCP quota** (Starter) is exhausted; auth UI (#89) was built from notes, not Figma frames 52:340-52:596: a visual pass against Figma is still needed.
- **`MviViewModel` has no clearing hook** (accepted base issue; #88 adds `clear()` used by the Waiting Room route only).
- Plan/draft docs: `07-be-integration-plan.md` (mobile BE integration plan), `08-be-tickets-draft.md` (superseded by the issues above).

## Local environment state
- **Emulator `emulator-5554` (Pixel_6_Pro):** has the #90 screenshot build (Home with sample events) installed as `com.rusuh.app.android`; reinstall from a clean branch before testing.
- **Mockoon mock API** running via `mockoon-cli` (pid 68713, port 3000, env `workspace-local/mockoon/rusuh-api.json`); stop with `mockoon-cli stop --all` or kill the process. Mockoon.app is installed.
- **Apidog MCP** added (file-based, `~/Downloads/Rusuh Booking Tiket API.openapi.json`), needs a Claude Code restart; for the live project use `--project=<id>` + `APIDOG_ACCESS_TOKEN` (see conversation).
- **Worktrees:** scratchpad `prshots` worktree (branch `pr-shots-local`) and the five agent worktrees above; `liauli/toadfish` worktree is detached at origin/main (d8cc66e).
- Main checkout `Projects/Rusuh-booking-ticket-app/rusuh-app` is still 17+ commits behind and has 3 uncommitted Gradle edits (`jvmToolchain(17)`), untouched.


---

## Update 2026-11-11 (after the user asked to stop the Pixel 6 Pro usage: non-UI + unit tests only)

Merged since the first note (all with CI green, merge commits): #87 My Queues MVI, #88 Waiting Room MVI, #90 Detail MVI, #89 sign-in sheet UI, #91 EPIC-01-M1 result types/ApiClient, #92 EPIC-01-M4 secure storage, #93 EPIC-01-M2 environments + core Koin module. Closed with them: #29, #37, #49, #62, #63, #65, #75, #76. Open PR left: #55 (old, unrelated).

In progress (non-UI, agents running, not yet reviewed): EPIC-01-M3 (#64) HTTP plugins; EPIC-01-M7 (#68) stub switch + harness. Next in order: EPIC-01-M5 (#66) device identity, EPIC-01-M6 (#67) auth data layer, then the FR API tickets (#78-#85, #73) and AUTH-00-M3 (#77), FR-22-M2 (#71, UI).

New local doc: workspace-local/e2e/rusuh-e2e-test-plan.md + .html (178 cases, 14 spec discrepancies D-01..D-14, 15 mock scenarios). Defects found when running it go to a LOCAL list first.

Process notes: every PR so far went through 1-3 independent review rounds (comments posted on the PR), screenshots attached for UI PRs, merge only after CI green (the iOS archive job takes 13-18 min and restarts on every push). Branch updates were done with `git merge origin/main` (no force push).

---

## PAUSED by the user, 2026-11-11 (state at the pause, nothing running)

Merged since the previous update: nothing new. EPIC-01-M1/M2/M4 (#91, #93, #92), the four UI/MVI PRs (#87-#90), #89 and earlier ones are all merged.

Open PRs (non-UI, unit-tested only, no emulator needed):
- **#94 EPIC-01-M7 (#68) stub backend switch**: 2 review rounds done, no High/Medium left; local checks green (358 tests). CI: iOS `build` job was still running when paused. Next step: wait for green CI, then `gh pr merge 94 --merge`. Decision to confirm with the user: temporary stub fallback in Real/Staging/Production (`ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES = true`) so release does not crash while real repositories do not exist.
- **#95 EPIC-01-M3 (#64) HTTP plugins**: round 1 fixes pushed (commit d39b4dc), local checks green (357 tests). Round 2 review was running and was stopped before reporting, so it still needs round 2 (and round 3 if Mediums). It changes `configurePlugins`/`forHost`/`ServerTimeSink`; whichever of #94/#95 merges second must `git merge origin/main` into its branch (the worktrees are `.claude/worktrees/agent-a87f3e61900f760fd` for #94 and `agent-ad4f931c7ade7a35c` for #95) and resolve around `data/network/ClientBuilder.kt` (#94) vs the new plugin parameters (#95), re-run the unit tests, push, wait for CI.

Next EPIC-01 tickets once those merge: #66 device identity (needs M3+M4), #67 auth session data layer (needs M3+M5). Then the FR API tickets (#78-#85, #73), AUTH-00-M3 (#77), FR-22-M2 e-ticket page UI (#71, needs the Pixel/Figma).

Other pending items from earlier: the E2E plan review (workspace-local/e2e), the local defect-list doc (not created yet), 14 spec discrepancies D-01..D-14 to confirm with the team, the `\'` literal-backslash string bug (pre-existing) as a chore ticket, the iOS ATS/`Info-Debug.plist` drift check already in CI.

---

## 2026-11-11: attribution cleanup (user request)

- All 15 PR descriptions had the "Generated with Claude Code" footer: removed (15/15 clean).
- `main` history rewritten and force-pushed (old 60b7568 -> new 5c3f3b0): the 38 `Co-Authored-By: Claude` trailers removed from the messages, trees byte-identical, 71 commits, authors/dates unchanged. Backup bundle of the old main (local only): scratchpad `rewrite/main-before-cleanup.bundle`. One human co-author line (`liauli`) intentionally kept.
- Open PR branches #94 and #95: trailers removed and replayed onto the new main (identical code); CI restarted.
- `pr-screenshots` branch: one trailer commit rewritten.
- Still holding old trailer commits: the old merged feature branches on the remote (`origin/feat/...`) and the merged PR pages (GitHub keeps them via refs/pull). Deleting those branches was not requested.
- **Teammates / local clones must reset**: `git fetch origin && git reset --hard origin/main` (the user's main checkout at Projects/Rusuh-booking-ticket-app/rusuh-app has 3 uncommitted Gradle edits: stash them first). PR #55 (fix/ui-fidelity-home) still branches from the OLD main history and needs `git rebase --onto origin/main <old merge-base>`.
- Rule from now on: no Co-Authored-By / "Generated with Claude" in commits, PRs or issues, including agent prompts (memory: feedback-no-claude-attribution).

### Follow-up (2026-11-11, same day)
- Local `main` in Projects/Rusuh-booking-ticket-app/rusuh-app reset to origin/main (5c3f3b0); the 3 uncommitted Gradle edits (`jvmToolchain(17)`) were restored on top (the androidApp conflict with the new cleartext guard block was resolved by keeping both).
- PR #55 (fix/ui-fidelity-home) closed as superseded by #56 (22 conflict blocks in 8 files with the merged Home/EventCard rework); branch kept on the remote.
- Open PRs are only #94 (EPIC-01-M7) and #95 (EPIC-01-M3); both were rebased onto the new main, CI restarted, work still paused as before (see the PAUSED section for the resume steps).
