# 05 · Gaps, conflicts, and what to decide next

## Conflicts between Figma / docs / API / code

| # | Conflict | Sources | Suggested handling |
| --- | --- | --- | --- |
| 1 | **DECIDED 2026-11-09: Account has no NIK.** Figma/web Account still shows NIK ("Data sesuai KTP", "Saya sendiri" prefill, "No NIK yet", "Edit name & NIK"); ADR-0007 + OpenAPI `User` have none. | Figma 60:2806/2910/3021 (mobile), 60:2491/2591/2698 (desktop), ADR-0007 | Adapt web layout to mobile **without NIK** — see "Account adaptation" below. "Saya sendiri" on Detail prefills **name only**; NIK always typed. |
| 2 | Figma Detail shows **name + NIK per ticket** (matches ADR-0007) while old hi-fi HTML showed NIK only. | Figma vs `analysis/hifi/detail.html`, hifi-api-review H-2 | Follow Figma/ADR: name + NIK per ticket. |
| 3 | Figma Detail has an editable-looking **"Kirim e-tiket ke" email**; API has no delivery email (tickets go to account email). | Figma 60:2093, hifi-api-review H-5 | Render read-only account email + link to change. |
| 4 | Service fee per order (ADR-0010) vs per-ticket breakdown in hi-fi. | hifi-api-review H-1 | One "Biaya layanan" line per order from `adminFee`. |
| 5 | Figma Payment shows **own VA number / QRIS image / "Menunggu pembayaran"** screens; ADR-0008 says Snap hosted page collects everything. Figma also has separate "Midtrans Snap" frames (so the Snap pages are mocked as reference). | Figma Payment vs ADR-0008, hifi-api-review H-3 | App picks method (optional `method` on `startSnapPayment`) and opens Snap; own VA/QR screens are only reproducible if Core API is chosen. Confirm scope with user. |
| 6 | Expired payment copy says refund "automatic". | hifi-api-review H-4, ADR-0009 | Use "akan kami kembalikan" + timeframe from Finance. |
| 7 | Detail needs a **login sheet** state (admitted guest). Not in the old hi-fi; Figma Sign in frames exist. | ADR-0006, hifi-api-review H-6 | Include login sheet at the top of Detail when `loginRequired`. |
| 8 | Architecture doc says mobile = **React Native/Expo**; actual app is **KMP/Compose**. **DECIDED: we go KMP.** | solution-architecture vs repo | Optionally record as a new ADR in docs repo (only new files allowed) — ask first. |
| 9 | `shared-keys.md` refers to "ADR-0016" that does not exist in the repo. | shared-keys.md | Next ADR number is 0016. |
| 10 | Turn window is **60 s** (hifi-api-review) but ADR-0003/customer-journey/arch doc still say 5 min before MISSED. | hifi-api-review vs journey | Use 60 s (openapi 0.3.0). |
| 11 | kmp-docs steering describes another codebase (Titan). **DECIDED: it is our inspiration; follow its patterns** (Container pattern, NavEffect/UiEffect, UseCases, module structure). Adapt where Rusuh's code differs (see 03). | workspace-local/kmp-docs | Existing code predates this (`MviViewModel<S,I,E>`, flat feature folders); migrate incrementally per the steering "Legacy screens … migrate incrementally". |
| 12 | Local `main` is 2 behind origin; uncommitted `jvmToolchain(17)` edits duplicate #53. | git | Pull after sorting the edits (ask). |

## Account adaptation (web → mobile, no NIK)

Web (Figma desktop 1440, section 60:2801, frames 60:2491 profile / 60:2591 edit / 60:2698 no-NIK): header `RUSUH!` +
nav Event · Tiket Saya · ID|EN toggle · Akun; page title = full name (huge display type) + email below; **main column
876 px + right aside 380 px**; three numbered sections with black number chips and a rule under each heading:
**1 Data sesuai KTP** (Nama, NIK + pink "Dipakai untuk 'Saya sendiri'" badge, "Ubah" link), **2 Masuk** (Email ✓ Terverifikasi,
"Ganti email", "Cara masuk: kode 6 digit ke email ini, tanpa kata sandi"), **3 Email dari Rusuh** (checkbox "Antrian, e-tiket,
refund — selalu dikirim" locked on, checkbox "Presale dan event baru"); then **Keluar** button + **Hapus akun** link; aside card
"NIK di profil dipakai untuk…" + encryption note; footer links (Syarat, Privasi, Refund, Bantuan). Edit state is an inline
form (Nama sesuai KTP, NIK with "Format NIK valid · 16 digit", info box, Simpan/Batal). No-NIK state is a banner "NIK Anda belum diisi… Isi NIK →".

Mobile (Figma 60:2806): same sections stacked, header becomes top bar `RUSUH!` · "Akun" · ID|EN pill, bottom bar (Event, Antrian, Tiket, Akun),
explainer card at the bottom instead of the aside.

**Mobile version without NIK (proposal):**
- Keep: title (name + email), **2 Masuk**, **3 Email dari Rusuh**, Keluar, Hapus akun, ID|EN toggle, bottom bar.
- Section 1 becomes **"Profil"**: Nama (from `getMe.fullName`), Email (from `getMe.email`) — drop the NIK row, badge, "Ubah NIK",
  edit-NIK state, "No NIK yet" banner and the NIK explainer card. Replace the card, if wanted, by a short note
  "NIK diisi per tiket saat memesan; KTP dicek di pintu masuk".
- **API gap:** OpenAPI has no profile-update endpoint (`PATCH /me`) or delete-account endpoint, no marketing-preference field,
  and `locale` lives on `User`. Name edit, "Presale dan event baru" toggle and "Hapus akun" have no backend yet → build UI against a
  stub seam, hide or disable what has no endpoint (BE not ready anyway). `startEmailChange`/`verifyEmailChange` exist for "Ganti email".
- Footer links (Syarat/Privasi/Refund/Bantuan) are web-only; on mobile put them as list rows or omit.
- Detail screen follows: "Saya sendiri" = prefill the **name** from the profile; NIK field always entered by the user
  (Figma shows NIK prefilled as "Dari profil Anda" — remove). Delete the "Profile without NIK" Detail state (60:2241).

## Hi-fi API review leftovers that affect mobile (open questions H-7…H-10)

"Ingatkan saya" on coming-soon (local notification from `waitingRoomOpensAt`, no API), "Kabari saya" waitlist (hide),
push near the turn (FR-27, undecided; stretch), paused copy "Biasanya kurang dari 5 menit" (keep generic "Lanjut otomatis").

## Open questions in the docs that may bite mobile

Q-30 native vs WebView (default native + Snap in system tab), Q-33 device id reset, Q-10 anti-bot, Q-61 min OS (assume
Android 26 / iOS 15), Q-48/D-5 login load, Q-11 NIK retention/consent text (consent version sent with `requestOtp`),
Q-53 which payment methods at launch, Q-35 chance indicator copy for far-back users, Q-14 confirm-your-turn window
(superseded by the 60 s turn window).

## Suggested next steps (for the user to pick)

1. **Reopen** the Figma-gap UI slices: #18, #19, #27, #28, #35, #36, #47, #48 (with a comment pointing to the Figma frame ids in 04),
   and decide whether to drop `on-hold` on #42/#43 (UI not BE-blocked). Not done yet.
2. Create missing slices (Auth/OTP, Account, E-ticket, Tiket tab, Snap handoff + App Link return, device registration +
   secure storage) under parent FR/NFR issues from the docs repo (read-only there).
3. Pull `origin/main` (i18n + jvm pin) after resolving local edits.
4. Per flow, rebuild screens from Figma via `get_design_context` per state frame, keeping repository seams stubbed (BE not ready).
5. Resolve conflict #5 (own VA/QRIS vs Snap) before building Payment. (#1 NIK on account is decided: none.)
6. Dates: due 13 Nov 2026 (4 days).
