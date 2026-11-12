# Claude working notes — Rusuh Booking Tiket

Private notes for Claude/the team to resume work. **Not part of any repo.** Written 2026-11-09 after a full read of
`rusuh-documentation` (read-only; nothing in it was changed), the GitHub state of `rusuh-app`, and the Figma file.

> Rule from the user: `rusuh-documentation` is **read-only**, except to add a *new* file such as an ADR.

| File | Contents |
| --- | --- |
| [01-project-and-decisions.md](01-project-and-decisions.md) | What the product is, business rules, every ADR in one line, timers, stock model |
| [02-api-and-flows-for-mobile.md](02-api-and-flows-for-mobile.md) | Endpoints, tokens, headers, error codes, flow-by-flow what the mobile app calls |
| [03-mobile-app-state.md](03-mobile-app-state.md) | `rusuh-app` code layout, what is built vs stubbed, conventions, CI, local git state |
| [04-github-and-figma.md](04-github-and-figma.md) | PRs/issues status, Figma frames (mobile) |
| [05-gaps-and-open-questions.md](05-gaps-and-open-questions.md) | Conflicts between Figma / API / ADRs / code, and what to decide next |
| [06-next-ticket-slices-draft.md](06-next-ticket-slices-draft.md) | DRAFT of next issue slices (nothing created on GitHub); section 7 follows the 2026-11-09 Figma exports |
| [figma-exports/2026-11-09/README.md](figma-exports/2026-11-09/README.md) | 12 sliced Figma states (Ticket Detail, Payment, Midtrans Snap) with per-state notes |

## Directory map (`~/Projects/Rusuh-booking-ticket-app`)

| Folder | What | Git |
| --- | --- | --- |
| `rusuh-app` | KMP + Compose Multiplatform app (Android + iOS). **The main checkout, `main`.** | yes, `PT-Meeting-Sampai-Malam-Konsultasi/rusuh-app` |
| `rusuh-app-m2`, `rusuh-app-m4` | Extra **git worktrees** of the same repo, old EPIC-00 branches (M2 CI/CD, M4 Koin). Stale. | worktrees |
| `rusuh-documentation` | ADRs, architecture, API draft, SRS, QnA, hi-fi HTML mockups. **Read-only.** | yes, `…/rusuh-documentation`, `main` |
| `workspace-local` | Mobile task slicing HTML, workflow slides PDF, OpenAPI JSON copy, mockup PNGs, `kmp-docs/` | no |
| `claude-notes` | These notes | no |

## One-paragraph summary

Rusuh Booking Tiket is a concert ticket flash-sale system by PT Meeting Sampai Malam Konsultasi, built as a tender
assessment for Telkomsel. Target: ≥300,000 concurrent users, a **virtual waiting room** with exact queue number and
ETA, stock-aware admission (no "PHP": getting in the queue and ending with no ticket, or paying and not getting a
ticket), Midtrans Snap payment, email e-tickets. Functional app is due **13 Nov 2026** (assessor deliverables:
solution, architecture→infra, cost, a usable app, Git repo). The mobile app (this team's scope) is Kotlin
Multiplatform with Compose Multiplatform, MVI, Koin, Ktor. Backend is Go `queue` + Node.js `core` (another team).
