package com.rusuh.app.domain.model

/**
 * Carries a failed [DomainResult] through the places whose contract is "throw on failure" (`HomeRepository`,
 * `MyQueuesRepository`) or "fail the flow" (`WaitingRoomRepository.observePlace`). The ViewModels map any exception
 * to their error state. The message is null on purpose: the server's text is never shown as is; [toString] shows
 * the result for diagnostics.
 */
class DomainException(
    val result: DomainResult<*>,
) : RuntimeException(null as String?) {
    override fun toString(): String = "DomainException($result)"
}

/**
 * The call needs a signed-in account (401 on claim): a guest must log in first.
 * TODO(AUTH-00 #77): the Waiting Room offers sign-in from this error.
 */
class SignInRequiredException : RuntimeException(null as String?)
