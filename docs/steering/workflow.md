---
inclusion: always
---
# Workflow & Agent Behavior

Always-on rules for how agents operate in this repo. Git branch/commit/MR
specifics and build commands live in [`git-build-docs.md`](git-build-docs.md) —
this doc covers cross-cutting behavior that applies regardless of the file in
context.

## Agent Output

- Drop filler words, pleasantries, verbose preambles.
- Use fragments over full sentences.
- Keep technical accuracy 100% — compress style only.
- Code, paths, commands, error messages stay exact.
- No "Sure!", "Great question!", "I'd be happy to help".
- One-line answers when one line suffices.

## Human Approval Required

Before executing ANY of the following, request explicit human approval and WAIT
for a reply. Do NOT proceed without it.

### Destructive commands (irreversible)
- `rm -rf`, `git restore`, `git reset --hard`, `git clean -f`/`-fd`
- `git push --force` / `--force-with-lease`, `git branch -D`, `git checkout -- .`
- `git stash drop` / `clear`
- `drop table` / `drop database`, `mkfs`, `dd if=`, `chmod 777`

### Data operations (irreversible)
- `adb shell pm clear` (clears app data / login), `adb uninstall`
- Deleting any file under `~/Projects/` without a backup
- Truncating/overwriting `spec.md` / `plan.md` / `README.md` without reading first

### Environment changes (hard to undo)
- Switching production ↔ staging/development flavor
- Changing `local.properties` / `gradle.properties` values, editing any `.env`
- Installing/removing system packages (`brew install/uninstall`)

### Safe (no approval needed)
- `git stash`, `git diff` / `log` / `status` (read-only)
- `adb logcat` / `dumpsys` (read-only), `cat` / `grep` / `find` / `ls`
- `./gradlew` build tasks (not data-destructive)
- Creating new files/folders, appending to docs (not overwriting)

## Git Hooks

- **Never `--no-verify`.** Fix hook failures by fixing the CODE, not the hook.
- **Never remove, disable, rename, or modify git hooks** (`.git/hooks/*`,
  `.husky/*`, pre-commit configs). Never delete `.git/hooks/`. Hooks are
  guardrails, not obstacles.
- Hook fails on Gradle/lint/format → fix the source, not the tooling.

## Gradle

- Always `--no-daemon`.

## Communication (Telegram)

- Always send Telegram notifications on task completion (`send-telegram`).
- **Never dump full build output.** Summarize: `BUILD SUCCESSFUL in 2m 30s` or
  the relevant error lines only.
- Short tasks → single Telegram message with the result.
- Long tasks (>30s) → run foreground, notify on completion. Do NOT background
  builds; you need the result to continue.

```bash
<cmd> > /tmp/task.log 2>&1
EXIT=$?
if [ $EXIT -eq 0 ]; then
  R=$(grep -E "BUILD SUCCESSFUL|PASSED" /tmp/task.log | tail -5)
  send-telegram text "✅ Done:\n$R"
else
  R=$(grep -E "FAILED|Error:" /tmp/task.log | tail -8)
  send-telegram text "❌ Failed (exit $EXIT):\n$R"
fi
```

## Migration Parity — Legacy Emulator

When migrating a feature from SuperApps (legacy) to Titan:

### Mandatory: observe legacy first
Before writing spec or code, **run the actual feature on the Legacy emulator** to capture:
1. Visual layout — screenshot the real screen as reference.
2. API requests & responses — Chucker DB or proxy for actual payloads.
3. Navigation flow — what screens open, in what order.
4. Edge cases — error, empty, loading states.

### How
- Legacy & Titan APK: pkg `com.telkomsel.telkomselcm`, installed on their respective emulators.
- **Identify emulators** (ports change — never assume):
  ```bash
  adb devices  # lists emulator-XXXX serials
  adb -s emulator-XXXX shell getprop ro.boot.qemu.avd_name  # "Legacy" or "Titan"
  ```
- Open a feature via deeplink: `adb -s <serial> shell am start -a android.intent.action.VIEW -d "<deeplink>"`
- **Screenshot (ALWAYS downscale — never pipe raw `screencap -p` inline; base64 pixels bloat the AI session):**
  - Titan: `scripts/titan-shot.sh` → auto-detects the Titan AVD, downscales to 720px, saves to `docs/qa/`, prints the path. **Feed the PATH to the image tool, never inline pixels.**
  - Legacy / manual: `adb -s <serial> exec-out screencap -p > /tmp/raw.png && sips -Z 720 /tmp/raw.png --out docs/qa/legacy.png && rm /tmp/raw.png`
  - Prefer `adb -s <serial> shell uiautomator dump` (XML text) when you only need UI state — near-zero context cost.
- Capture API (Chucker):
  ```bash
  adb -s <serial> shell "run-as <pkg> cat databases/chucker.db" > /tmp/ck.db
  sqlite3 /tmp/ck.db "SELECT url, responseCode, responseBody FROM transactions ORDER BY id DESC LIMIT 10;"
  ```
- Save all evidence to `docs/qa/`.

### Verify on Titan emulator
Open the **same deeplink** on Titan and compare: visual match (layout, spacing,
text, icons), API calls (same endpoints + request shape), behavior (navigation,
error handling).

### Validate resource-ids / run automation (skill)
- Canonical on-device UI validation tool = the **automation-testing skill**
  → [`docs/skills/automation-testing/SKILL.md`](../../docs/skills/automation-testing/SKILL.md).
  Consult it BEFORE hand-rolling adb/Appium.
- One-time setup: `scripts/setup-automation.sh` — auto-clones the WebdriverIO repo
  @ `feature/android/9.4`, installs `appium@3` + the `uiautomator2` driver + npm deps,
  writes `.env` for BLUE/LOCAL vs the Titan emulator. Idempotent + non-destructive.
- Cheapest validation (DEFAULT, NO Appium): after implementing/migrating a feature,
  prove its `Modifier.resId(...)` tags surface as real `resource-id`s —
  `scripts/setup-automation.sh --run-test` (one-shot read-only `uiautomator dump` +
  grep of slash-prefixed resource-ids), or the raw dump+grep from the skill. This is
  the PRIMARY gate for resource-id / migration work.
- Journey/behavioral test for a FIXED feature: `scripts/setup-automation.sh --run-test=spec:<path>`
  (e.g. `--run-test=spec:test/specs/utility/profile/profile.specs.js`), or
  `--run-test=wdio` for the full Appium suite — or `cd <wdio repo> && ENV=BLUE npm run blue:android`
  (override `--spec` to a guest dashboard/login spec).
- KNOWN LIMIT: account-DFM screens (MyTheme / Billing / History / JagaPulsa / MyPackage, and
  profile/account journeys) do NOT surface resource-ids to uiautomator on LOCAL — a host-export
  gap, NOT a tagging gap; resid smokes AND resid-locator journey specs false-negative there.
  Restrict resid checks to base-window surfaces (dashboard / login / bottom-sheet).
- OUT OF SCOPE: `rewards` / `pay` (payment) / `lifestyle` are handled natively / removed later —
  do NOT chain resid tests for them, do NOT count them as coverage gaps.
- SAFETY: never `adb shell pm clear` / `adb uninstall` to reset (wipes login —
  human-approval-gated). For an authed state use the `titan-login` skill.

### Resource-id rule — reuse the automation list VERBATIM
1. REUSE, NEVER INVENT. When implementing or fixing a screen the WebdriverIO suite targets, the id
   applied via `Modifier.resId(...)` MUST be the EXISTING legacy id from the automation locators
   (`~/Projects/mytelkomsel-automation-webdriverio/test/locators/**`, branch `feature/android/9.4`),
   copied VERBATIM — bare id, case-sensitive, prefix preserved (`tv`/`iv`/`cl`/`cv`/etc.). Never
   rename/normalize/invent. The leading `/` is added by `Modifier.resId` only — never in the catalog const.
2. SOURCE-OF-TRUTH ORDER: (a) find the id in the WDIO locators for that surface; (b) add it as a
   `const val` in the owning module's `*ResId` catalog; (c) apply `Modifier.resId(Catalog.X)` on the
   real node. If NO legacy id exists for a node, leave it untagged — do not fabricate.
3. CHAIN THE TEST INTO THE DEV STEP: implementing/fixing a targeted surface is NOT done until the dev
   CHAINS a resid validation — `scripts/setup-automation.sh --run-test` (or the skill dump+grep) — and
   confirms each listed id for that surface surfaces as `resource-id="/<id>"` on Titan, matching the
   locator character-for-character. Save evidence to `docs/qa/` BEFORE `master done`.
4. Do NOT edit the automation repo to fit Titan — Titan conforms to the EXISTING locator ids (legacy
   SuperApps still uses the same suite). The locator list is the SOURCE OF TRUTH.
5. OUT OF SCOPE: `rewards` / `pay` (payment) / `lifestyle` are native/removed later — do NOT chain
   resid tests for them.
6. Reference specs (don't restate): `features/core/docs/spec/cmp-resource-id-catalog-breakdown.md`
   (inventory), `features/core/docs/spec/cmp-resource-id-automation-parity.md` (mechanism).

### Applies to
- **SA**: capture legacy screenshots + API payloads in spec.md. Include deeplinks.
- **Dev**: run legacy, observe, implement, verify on Titan before DONE. Reuse the listed resource-id
  VERBATIM (never invent) and CHAIN `scripts/setup-automation.sh --run-test` (or the skill's dump+grep)
  to confirm the surface's resource-ids surface on Titan — save evidence to `docs/qa/` BEFORE
  `master done`. Skip the chain for out-of-scope `rewards`/`pay`/`lifestyle`.
- **QA**: use the automation-testing skill (cheap resid smoke and/or full WDIO / journey spec) on Titan
  AND compare with legacy; restrict resid checks to base-window surfaces (account-DFM + profile/account
  host-export gap); skip `rewards`/`pay`/`lifestyle` (out of scope). Report differences.
- **DIJE**: wire legacy vs Titan comparison screenshots into spec/README; link the
  automation-testing skill from the feature README.

### Exceptions
- Intentional redesign → `DESIGN CHANGE: <reason>` in spec.
- Legacy bug → `LEGACY BUG: not replicated intentionally`.
- Deprecated API → `API CHANGE: <old> → <new>, reason: <why>`.
