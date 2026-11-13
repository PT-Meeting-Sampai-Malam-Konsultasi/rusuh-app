# AUTH-00-M6: 60-second vs 5-minute countdown question

Issue #103 / PR AUTH-00-M6 (#103): Waiting Room login gate

## Context

The Waiting Room's "Your Turn" screen shows a 60-second countdown ("to enter") while
ADR-0005/0006 define a 5-minute order window once the customer claims admission.

## Current Implementation

The Waiting Room displays the `turnExpiresAt` countdown from the queue place snapshot:

- **60 seconds**: the time the waiting room gives you to either (a) sign in if you're a guest,
  or (b) proceed to the Detail screen to pick tickets.
- **5 minutes**: the time the Detail screen gives you to complete ticket selection (from
  `AdmissionInfo.expiresAt`).

These are two different deadlines:
1. The 60-second "turn window" is the time to move from the Waiting Room to Detail.
2. The 5-minute "order window" is the time to complete the booking on the Detail screen.

## Held Guest Flow

When a guest's turn arrives:
1. The claim happens automatically (as before).
2. The guest sees "Your turn!" with the 60-second countdown and a "Sign in to continue" button.
3. If the guest signs in within 60 seconds, they proceed to Detail with their claimed admission
   (which has its own 5-minute window from `AdmissionInfo.expiresAt`).
4. If the 60 seconds expire while held, the turn is marked as Missed.

## Open Question

**Is the 60-second "turn window" the right UX for a guest who needs to sign in first?**

- A guest signing in via OTP email may need more than 60 seconds to:
  - Enter their email
  - Wait for the OTP email
  - Check their inbox / spam folder
  - Enter the 6-digit code
  - Optionally complete the new-account name step

- The admission's 5-minute window (`AdmissionInfo.expiresAt`) starts ticking at the claim,
  not when they reach Detail, so even a fast sign-in loses some of that time.

**Possible adjustments (future tickets):**
1. Extend the turn window for guests (e.g., 2 minutes instead of 60 seconds).
2. Show the admission's 5-minute countdown on the held screen instead of the 60-second turn
   window, since the claim already happened.
3. Keep the 60-second turn window but display a warning when < 30 seconds remain while held.

For now, the implementation uses the existing 60-second `turnExpiresAt` from the queue place
snapshot, which is consistent with how signed-in users experience the turn. The backend would
need to provide a longer `turnExpiresAt` for guests if we wanted option 1.

## References

- ADR-0005: Queue mechanics (the 60-second turn window)
- ADR-0006: Admission tokens (the 5-minute order window)
- docs/data-layer.md: "Claimed admission" section
