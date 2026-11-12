package com.rusuh.app.feature.auth

import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AuthFailure
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.authFailure
import com.rusuh.app.domain.session.SessionPhase
import com.rusuh.app.domain.usecase.ObserveSessionUseCase
import com.rusuh.app.domain.usecase.RequestOtpUseCase
import com.rusuh.app.domain.usecase.SaveDisplayNameUseCase
import com.rusuh.app.domain.usecase.VerifyOtpOutcome
import com.rusuh.app.domain.usecase.VerifyOtpUseCase
import com.rusuh.app.mvi.MviViewModel
import com.rusuh.app.ui.components.OTP_CODE_LENGTH
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L

/**
 * MVI ViewModel of the email-code sign-in (login and register in one flow), behind the [SignInUiState] seam of the
 * screens. Used by the Account tab (compact) and the full-screen route.
 *
 * Flow: Email -> `requestOtp` -> Code (resend cooldown from `resendAfterSeconds`, validity from the server's
 * `expiresAt`) -> `verifyOtp` -> signed in. Only when the answer says `isNewAccount` the NewAccount step (name and
 * consent) follows; "Lanjut" then finishes. Failures that the five design states do not cover are one inline
 * [SignInError] in the current step; a wrong code is the WrongCode state; an expired or locked challenge is
 * CodeExpired (the old code is kept). Nothing server-written is ever shown.
 *
 * - One request at a time: while [SignInState.isBusy] every intent is ignored (double taps, the auto-submit of the
 *   last digit plus the button).
 * - Time comes from the [ServerClock] only; one ticker coroutine runs while a code step or a 429 countdown is
 *   visible, and stops with it.
 * - A sign-in that completes after a sign-out is dropped by [VerifyOtpUseCase] (generation counter).
 * - The session ending (sign-out, expiry) resets the flow to a fresh Email step.
 * - shortcut: not saved across process death (the user requests a new code); it does survive rotation and tab
 *   switches because the route keeps this ViewModel in its back-stack entry.
 * - shortcut: the name has no API to be saved, so it only goes to the device ([SaveDisplayNameUseCase]); the consent
 *   checkbox is not sent anywhere (the consent version travels with the code request).
 */
@Suppress("TooManyFunctions") // one small function per step transition
class SignInViewModel(
    private val requestOtp: RequestOtpUseCase,
    private val verifyOtp: VerifyOtpUseCase,
    private val saveDisplayName: SaveDisplayNameUseCase,
    private val observeSession: ObserveSessionUseCase,
    private val clock: ServerClock,
) : MviViewModel<SignInState, SignInIntent, SignInEffect>(SignInState()) {
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            var previous: SessionPhase? = null
            observeSession.phase().collect { phase ->
                // Signed out (or expired) after being signed in: the next login starts from a clean form.
                if (previous == SessionPhase.SignedIn && phase != SessionPhase.SignedIn) reset()
                previous = phase
            }
        }
    }

    override suspend fun reduce(intent: SignInIntent) {
        if (currentState.isBusy) return
        when (intent) {
            is SignInIntent.EmailChanged -> editEmail(intent.value)
            SignInIntent.SendCode -> sendCode()
            is SignInIntent.CodeChanged -> editCode(intent.value)
            is SignInIntent.SubmitCode -> submitCode(intent.code)
            SignInIntent.ResendCode -> resend()
            SignInIntent.ChangeEmail -> changeEmail()
            is SignInIntent.NameChanged -> editName(intent.value)
            is SignInIntent.ConsentChanged -> editConsent(intent.accepted)
            SignInIntent.ContinueNewAccount -> continueNewAccount()
        }
    }

    // --- Fields ---

    private fun editEmail(value: String) {
        val step = currentState.step as? SignInUiState.Email ?: return
        // A 429 countdown stays (it is not about this text); other errors are about the old text.
        setState { copy(step = step.copy(email = value), error = error.takeIf { it is SignInError.RateLimited }) }
    }

    private fun editCode(raw: String) {
        val value = raw.filter { it.isDigit() }.take(OTP_CODE_LENGTH)
        val keepRateLimit = currentState.error.takeIf { it is SignInError.RateLimited }
        when (val step = currentState.step) {
            is SignInUiState.Code -> setState { copy(step = step.copy(code = value), error = keepRateLimit) }
            // The first edit after a wrong code goes back to the plain code step, timers kept.
            is SignInUiState.WrongCode ->
                setState {
                    copy(
                        step = SignInUiState.Code(step.email, value, step.resendInSeconds, step.validForSeconds),
                        error = keepRateLimit,
                    )
                }
            else -> Unit
        }
    }

    private fun editName(value: String) {
        val step = currentState.step as? SignInUiState.NewAccount ?: return
        setState { copy(step = step.copy(fullName = value)) }
    }

    private fun editConsent(accepted: Boolean) {
        val step = currentState.step as? SignInUiState.NewAccount ?: return
        setState { copy(step = step.copy(consentAccepted = accepted)) }
    }

    private fun changeEmail() {
        val email =
            when (val step = currentState.step) {
                is SignInUiState.Code -> step.email
                is SignInUiState.WrongCode -> step.email
                is SignInUiState.CodeExpired -> step.email
                // NewAccount: the session already exists, there is no way back to the email.
                else -> return
            }
        setState { copy(step = SignInUiState.Email(email), challenge = null, error = null, retryAtMillis = null) }
    }

    // --- Request a code ---

    private suspend fun sendCode() {
        val step = currentState.step as? SignInUiState.Email ?: return
        if (!isPlausibleEmail(step.email) || isRateLimited()) return
        requestCode(step.email.trim())
    }

    private suspend fun resend() {
        val step = currentState.step
        val email =
            when {
                step is SignInUiState.Code && step.resendInSeconds <= 0 -> step.email
                step is SignInUiState.WrongCode && step.resendInSeconds <= 0 -> step.email
                step is SignInUiState.CodeExpired -> step.email
                else -> return
            }
        if (isRateLimited()) return
        requestCode(email)
    }

    private suspend fun requestCode(email: String) {
        setState { copy(isBusy = true, error = null) }
        when (val result = requestOtp(email)) {
            is DomainResult.Success -> openCodeStep(email, result.data)
            else -> showError(result.authFailure())
        }
    }

    private fun openCodeStep(
        email: String,
        challenge: OtpChallenge,
    ) {
        val now = clock.nowMillis()
        // The server's expiry is only comparable when the clock has seen the server; otherwise count the design's
        // validity from now on the same clock.
        val validUntil =
            challenge.expiresAtMillis.takeIf { clock.isSynced }
                ?: (now + SignInPolicy.CODE_VALID_SECONDS * MILLIS_PER_SECOND)
        val resendSeconds = challenge.resendAfterSeconds.coerceAtLeast(0)
        val pending =
            PendingChallenge(
                challengeId = challenge.challengeId,
                validUntilMillis = validUntil,
                resendAtMillis = now + resendSeconds * MILLIS_PER_SECOND,
            )
        val validFor = secondsUntil(validUntil, now)
        setState {
            copy(
                isBusy = false,
                error = null,
                challenge = pending.takeIf { validFor > 0 },
                step =
                    if (validFor > 0) {
                        SignInUiState.Code(email, "", resendSeconds, validFor)
                    } else {
                        SignInUiState.CodeExpired(email)
                    },
            )
        }
        ensureTicker()
    }

    // --- Verify the code ---

    private suspend fun submitCode(code: String) {
        val pending = currentState.challenge
        val step = currentState.step
        val (email, resend, valid) = codeStepValues(step) ?: return
        if (pending == null || code.length != OTP_CODE_LENGTH || isRateLimited()) return
        setState { copy(step = SignInUiState.Code(email, code, resend, valid), isBusy = true, error = null) }
        when (val outcome = verifyOtp(pending.challengeId, code)) {
            // A sign-out happened meanwhile: the use case dropped the answer (the session phase resets the flow).
            VerifyOtpOutcome.Superseded -> setState { copy(isBusy = false) }
            is VerifyOtpOutcome.Completed -> handleVerified(outcome.result, code)
        }
    }

    /** Email, resend seconds and validity seconds of a Code or WrongCode step; null on any other step. */
    private fun codeStepValues(step: SignInUiState): Triple<String, Int, Int>? =
        when (step) {
            is SignInUiState.Code -> Triple(step.email, step.resendInSeconds, step.validForSeconds)
            is SignInUiState.WrongCode -> Triple(step.email, step.resendInSeconds, step.validForSeconds)
            else -> null
        }

    private fun handleVerified(
        result: DomainResult<AuthSession>,
        code: String,
    ) {
        if (result is DomainResult.Success) {
            onVerified(result.data)
            return
        }
        val email = emailOfCodeStep() ?: (currentState.step as? SignInUiState.CodeExpired)?.email ?: return
        when (val failure = result.authFailure()) {
            is AuthFailure.WrongCode ->
                if (currentState.challenge == null || currentState.step is SignInUiState.CodeExpired) {
                    setState {
                        copy(
                            isBusy = false,
                            error = null,
                            challenge = null,
                            step = SignInUiState.CodeExpired(email, code),
                        )
                    }
                } else {
                    wrongCode(email, code, failure.attemptsLeft)
                }
            AuthFailure.ChallengeExpired, AuthFailure.ChallengeLocked ->
                setState {
                    copy(
                        isBusy = false,
                        error = null,
                        challenge = null,
                        step = SignInUiState.CodeExpired(email, code),
                    )
                }
            else -> showError(failure)
        }
    }

    private fun wrongCode(
        email: String,
        code: String,
        attemptsLeft: Int?,
    ) {
        val state = currentState
        val timers = state.step as? SignInUiState.Code
        val tries = (state.challenge?.wrongAttempts ?: 0) + 1
        val left = attemptsLeft ?: (SignInPolicy.MAX_ATTEMPTS - tries).coerceAtLeast(1)
        setState {
            copy(
                isBusy = false,
                error = null,
                challenge = challenge?.copy(wrongAttempts = tries),
                step =
                    SignInUiState.WrongCode(
                        email = email,
                        code = code,
                        attemptsLeft = left,
                        resendInSeconds = timers?.resendInSeconds ?: 0,
                        validForSeconds = timers?.validForSeconds ?: 0,
                    ),
            )
        }
    }

    private fun onVerified(session: AuthSession) {
        val email = emailOfCodeStep() ?: session.user.email
        if (session.isNewAccount) {
            // Signed in already, but the Account tab keeps the login until the name step is done.
            setState {
                copy(
                    isBusy = false,
                    error = null,
                    challenge = null,
                    newAccountUserId = session.user.userId,
                    step = SignInUiState.NewAccount(email, fullName = session.user.fullName.orEmpty()),
                )
            }
        } else {
            setState { copy(isBusy = false, error = null, challenge = null, isFinished = true) }
            sendEffect(SignInEffect.SignedIn(isNewAccount = false))
        }
    }

    // --- New account ---

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun continueNewAccount() {
        val step = currentState.step as? SignInUiState.NewAccount ?: return
        val name = step.fullName.trim()
        if (name.isEmpty()) return
        setState { copy(isBusy = true, error = null) }
        currentState.newAccountUserId?.let { userId ->
            try {
                saveDisplayName(userId, name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // shortcut: the name is a device-only convenience; the page then shows the email instead.
            }
        }
        setState { copy(isBusy = false, isFinished = true) }
        sendEffect(SignInEffect.SignedIn(isNewAccount = true))
    }

    // --- Errors ---

    private fun showError(failure: AuthFailure?) {
        val now = clock.nowMillis()
        when (failure) {
            AuthFailure.Network -> setState { copy(isBusy = false, error = SignInError.Network) }
            is AuthFailure.RateLimited -> {
                val seconds = failure.retryAfterSeconds?.takeIf { it > 0 }
                setState {
                    copy(
                        isBusy = false,
                        error = SignInError.RateLimited(seconds),
                        retryAtMillis = seconds?.let { now + it * MILLIS_PER_SECOND },
                    )
                }
                ensureTicker()
            }
            AuthFailure.InvalidInput -> setState { copy(isBusy = false, error = SignInError.InvalidInput) }
            else -> setState { copy(isBusy = false, error = SignInError.Unknown) }
        }
    }

    private fun isRateLimited(): Boolean = ((currentState.error as? SignInError.RateLimited)?.secondsLeft ?: 0) > 0

    private fun emailOfCodeStep(): String? =
        when (val step = currentState.step) {
            is SignInUiState.Code -> step.email
            is SignInUiState.WrongCode -> step.email
            is SignInUiState.CodeExpired -> step.email
            else -> null
        }

    // --- Time ---

    private fun reset() {
        ticker?.cancel()
        ticker = null
        setState { SignInState() }
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker =
            viewModelScope.launch {
                while (isActive && needsTick()) {
                    delay(TICK_MILLIS)
                    tick()
                }
            }
    }

    private fun needsTick(): Boolean {
        val state = currentState
        val onCodeStep = state.step is SignInUiState.Code || state.step is SignInUiState.WrongCode
        return (state.challenge != null && onCodeStep) || state.retryAtMillis != null
    }

    private fun tick() {
        val now = clock.nowMillis()
        setState {
            val afterCode = withCodeCountdown(now)
            afterCode.withRetryCountdown(now)
        }
    }

    private fun SignInState.withCodeCountdown(now: Long): SignInState {
        val pending = challenge ?: return this
        val valid = secondsUntil(pending.validUntilMillis, now)
        val resend = secondsUntil(pending.resendAtMillis, now)
        return when (val current = step) {
            is SignInUiState.Code ->
                if (valid <= 0) {
                    copy(step = SignInUiState.CodeExpired(current.email, current.code), challenge = null)
                } else {
                    copy(step = current.copy(resendInSeconds = resend, validForSeconds = valid))
                }
            is SignInUiState.WrongCode ->
                if (valid <= 0) {
                    copy(step = SignInUiState.CodeExpired(current.email, current.code), challenge = null)
                } else {
                    copy(step = current.copy(resendInSeconds = resend, validForSeconds = valid))
                }
            else -> this
        }
    }

    private fun SignInState.withRetryCountdown(now: Long): SignInState {
        val until = retryAtMillis ?: return this
        val left = secondsUntil(until, now)
        return if (left <= 0) {
            copy(retryAtMillis = null, error = error.takeUnless { it is SignInError.RateLimited })
        } else {
            copy(error = SignInError.RateLimited(left))
        }
    }

    private fun secondsUntil(
        deadlineMillis: Long,
        nowMillis: Long,
    ): Int {
        val remaining = deadlineMillis - nowMillis
        return if (remaining <= 0) 0 else ((remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }
}
