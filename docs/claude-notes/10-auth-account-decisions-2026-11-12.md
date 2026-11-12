# 10 · Auth and Account decisions (user, 2026-11-12)

Source: PNGs in `~/Downloads/Rusuh Tiket App/` (`Login - Mobile.png` 5 states, `Account - Mobile.png` 3 states) and the user's answers.

## Login (full UI vs Account tab)
- Full screen, NOT a bottom sheet (the `SignInSheet` from #89 is replaced).
- **Waiting-room login = full version:** top bar (back arrow, "Masuk atau daftar", ID/EN), "SETELAH MASUK, ANDA LANJUT KE" + event banner, title, subtitle, email field / OTP boxes, button, hint + terms lines, then the thick rule and the three info blocks (Tanpa kata sandi / Daftar = masuk / Satu akun, banyak antrian).
- **Account tab (signed out) = compact version:** only title, subtitle, email field / OTP, button, plus the hint and terms lines (everything above the first thick rule). No event banner, no info blocks, no back arrow (tab keeps its normal top bar and bottom bar). The new-account step ends with a different final button (no queue to continue to).
- 5 states: Email, Code, Wrong code, Code expired, New account (name + consent checkbox).

## Login only in the waiting room (no login on Detail)
- A guest can wait. When the turn arrives and the user is not signed in: they are HELD in the waiting room until they sign in; the Detail (order) countdown has ALREADY started. After sign-in they continue to Detail with the remaining time.
- If the Detail countdown ends while they are still stuck in the waiting room: blocked, they lose their turn (Missed).
- So Detail has no login sheet (supersedes the "Masuk untuk melanjutkan pesanan" sheet in ADR-0006 / the architecture doc).

## Account (signed in)
- Drop section 3 "Email dari Rusuh" (the two checkboxes).
- No NIK anywhere on the account for this POC: remove the NIK field, the "NIK belum diisi" state, the edit name+NIK state, the "DIPAKAI UNTUK SAYA SENDIRI" tag and the "NIK di profil dipakai untuk" info box. NIK is not stored.
- Detail attendee form: remove the "Saya sendiri | Orang lain" toggle. (NIK and name are still typed per ticket at order time, they are not saved.)

## Spec discrepancies noticed in the design
- Wrong-code text says wait 15 minutes; expired text says code valid 5 minutes; ADR-0006 says valid 10 minutes, new code after 60 s (resend), 5 attempts.
- New-account name is asked AFTER the code (design); Apidog `POST /auth/otp` takes `fullName` before the code, and has no profile PUT.
- Signed-in Account shows "Ganti email" (Apidog has /me/email-change) and "Hapus akun" (no endpoint).

## Flow confirmation (user, later on 2026-11-12)
- New-account step (name + consent) appears only when the verify response says `isNewAccount = true`.
- After login or register, the app shows the signed-in Account page (profile) in the Account tab.
- Name entered in the new-account step has no API to save it (Apidog has no profile update): kept on the device only for now, to confirm with the backend.
