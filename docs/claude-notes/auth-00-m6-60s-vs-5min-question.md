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

## Race Condition: Sign-in vs Expiry

**Intended rule: Sign-in just in time wins.**

If the user's sign-in completes at the exact same tick where the countdown would expire:
- The session state update (signed-in) is processed first.
- The user navigates to Detail, NOT to MissedTurn.

This is implemented in the ViewModel's session observer: when `isSignedIn` transitions to `true`
while the user is held at `YourTurn`, the navigation to Detail fires immediately (before any
subsequent tick could transition to MissedTurn).

Test coverage: `signInAtExactExpiryNavigatesToDetail` in `WaitingRoomViewModelTest`.

## Verified Behaviour

The countdown shown to a held guest uses the **60-second turn window** (`turnExpiresAtEpochMillis`)
from the queue place snapshot, NOT the 5-minute admission window (`AdmissionInfo.expiresAt`).
This is correct because:

1. The 60-second window is what the screen text promises ("60 seconds to enter").
2. The admission's 5-minute window is the Detail screen's concern.
3. A guest who signs in quickly preserves most of the 5-minute order window.
4. Consistency: signed-in users also see the same 60-second countdown before auto-advancing.

The `WaitingRoomViewModel.tick()` method checks `phase.secondsLeft <= 0` against elapsed time from
the monotonic clock anchored at `receivedAtMillis`. The expiry decision uses server time, never
device time.

## Open Question (UX, deferred)

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
