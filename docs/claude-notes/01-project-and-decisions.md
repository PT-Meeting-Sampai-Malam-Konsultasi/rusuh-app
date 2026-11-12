# 01 · Project and decisions

Source: `rusuh-documentation` (ADRs 0001–0015, SRS v1.0, QnA 14 & 21 Oct, team meetings 16/19/23 Oct,
solution-architecture, customer-journey). **Where an ADR and another doc disagree, the ADR wins.**

## Product

- 4 pages in the SRS: Home → Waiting Room → Detail Ticket → Payment. Later additions: Login (email OTP), E-ticket,
  Account, "Antrian" tab (My Queues, a team-added feature approved by the assessor, no doc FR).
- Principles: keep UX stable whatever the backend does; **"Jangan PHP"** — nobody admitted ends without a ticket;
  paid always means issued; sold out → queue dissolves and users are told.
- Owner QnA: stack "murah tapi bagus dan mudah"; bilingual ID/EN; seats by **category capacity only** (no seat map);
  can queue for several events at once; Midtrans real sandbox, no mock; real email via AWS SES; submit via Git.
- Lo-fi may be changed with justification; palette free.

## Business rules to remember in the UI

| Rule | Detail | ADR |
| --- | --- | --- |
| Queue is per **device**, per event | Server-signed device token; guest can wait; position = `seq − served`; ETA = position ÷ `ratePerMin`, shown "± n menit" | 0003 |
| Admission reserves **3 units** per admitted user | `min(3, unreserved)`; order may not exceed `reservedUnits` (can be 1–2 near sell-out) | 0004 |
| **5 min to order**, then **10 min to pay** | Order window starts at claim; payment window starts at hold. Longest path 15 min. Use server `expiresAt − serverNow`, never device clock | 0005 |
| Turn window **60 s** | After "Giliran Anda" you must claim within 60 s or turn is MISSED (changed from 5 min in hifi-api-review). Client auto-claims immediately | hifi-api-review |
| Expired/missed → rejoin at the **back** | No priority re-queue | 0005 |
| No guest transactions | Guest may wait and is nagged to log in; at the turn they are admitted and the **login sheet comes first inside the 5 min** | 0006 |
| Auth = passwordless email OTP | 6 digits, 10 min valid, 5 attempts, resend after 60 s (max 3 / 10 min). Access token 15 min, refresh 30 days rotating (Keychain/Keystore on mobile). Reply never reveals if account exists | 0006 |
| **One NIK per ticket, one seat per NIK per event** | Order = 1–3 tickets, each with attendee **name + NIK**; Dukcapil mock checks both; buyer need not attend; KTP checked at the gate; NIK masked everywhere | 0007 |
| NIK entry rate limit | 3 attempts/min per NIK field per order | 0007 |
| Payment = **Midtrans Snap hosted page** | Mobile opens system tab (Custom Tabs / SFSafariViewController) with one-time handoff code in URL fragment; must also work in a WebView; returns via App Link; app re-fetches booking, never trusts return URL | 0008 |
| Late payment after expiry | Booking stays EXPIRED, no ticket, payment flagged `PAID_AFTER_EXPIRY`, customer emailed; refund is manual/out of scope | 0009 |
| Admin fee | **Fixed, configurable amount per order** (not per ticket), shown as "Biaya layanan" | 0010 |
| Bilingual | ID default + EN; all strings via resources; `Accept-Language`; times in venue timezone (WIB/WITA/WIT); rupiah | 0014 |
| Mobile minimums (assumption) | Android 8.0 (API 26), iOS 15 | 0014 |
| Hybrid queue updates | Poll frontier (10 s >10k back, 5 s ≤10k, 2 s ≤1k, ±20 % jitter); **SSE only in the last 2,000 places** (`streamFrom`), fall back to 2 s polling on failure; polling is source of truth | 0003, 0015 |

## All ADRs (status)

0001 record decisions (Accepted) · 0002 build own waiting room (Proposed) · 0003 device queue exact position
(Accepted) · 0004 stock-aware admission, 3 units (Accepted) · 0005 5 min + 10 min timers (Accepted) · 0006 OTP
accounts, login inside order window (Accepted) · 0007 NIK per ticket (Accepted) · 0008 Midtrans Snap (Accepted) ·
0009 late payment flagged (Accepted) · 0010 admin fee per order (Accepted) · 0011 Go `queue` + Node `core`
(Proposed) · 0012 AWS Jakarta, ECS Fargate (Accepted, runtime proposed) · 0013 outbox + Valkey Streams
(Proposed) · 0014 bilingual (Accepted) · 0015 hybrid polling + SSE (Accepted).
Next ADR number would be **0016** (shared Valkey keys `core`↔`queue`, referenced in `api/shared-keys.md` but not yet written).

## Stock-unit and booking states

- Units: `available → reserved (claim, ≤3) → held (hold, 1–3) → sold`; unused `3−n` return at hold; order window end
  returns all; payment window end returns held.
- Booking status enum: `HELD | PAID | EXPIRED | CANCELLED`. Payment status: `none, pending, settled, denied,
  expired, cancelled, failed, paid_after_expiry`.
- Queue state: `preopen | open | paused | closed`. Pause/close `reason`: `stock | operator | sold_out | cancelled | ended`.
- Event state (Home stamp): `coming_soon, waiting_room_open, on_sale, almost_gone, temporarily_full, sold_out, ended`.
  Mapping from frontier in `api/shared-keys.md` (`paused/stock → temporarily_full` "Penuh sementara").
- Waiting room opens 30 min before sale; joiners before sale open get a random lottery order (pre-queue countdown).

## Architecture (backend, for context)

Edge: CloudFront + WAF + Turnstile. `queue` (Go, Valkey Lua, 1 s admission controller, SSE) and `core` (Node/TS,
Fastify; modules auth, catalog, booking, payment, notification, admin; PostgreSQL; outbox → Valkey Streams). AWS
Jakarta, ECS Fargate. Hosts in the OpenAPI: `api.rusuh.id/v1`, `q.rusuh.id/v1` (frontier + stream, CDN host),
`pay.rusuh.id/api/v1` (payment page), `app.rusuh.id/payment-result` (App Link). Cost ≈ $3,080/mo infra; dev ≈ $7,840
one-off.

The architecture doc proposes **React Native + Expo** for mobile. **Decided 2026-11-09: we go Kotlin Multiplatform**
(see [03](03-mobile-app-state.md)); no ADR records it yet. Also decided 2026-11-09: **the account holds no NIK** (consistent
with ADR-0007; Figma still shows one and must be adapted).

## Timeline context

- 13 Nov 2026: functional app due. Today (9 Nov) = 4 days left.
- Owner meeting 21 Oct and team decisions 23 Oct closed almost every business question. Still open per docs: Q-30
  (native vs WebView screens — arch doc recommends native + Snap in system tab), Q-31 tech radar, Q-62 Go+Node split,
  D-5 login load / SES limit, Q-11 NIK retention, Q-33 web device id, Q-10 anti-bot.
