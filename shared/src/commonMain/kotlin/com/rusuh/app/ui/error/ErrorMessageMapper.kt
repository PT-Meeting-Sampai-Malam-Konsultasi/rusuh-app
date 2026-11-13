package com.rusuh.app.ui.error

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode

/**
 * Maps API errors (DomainResult.Error, ErrorCode, HTTP status) to string resource keys.
 *
 * This is a pure Kotlin mapper in the presentation layer. It never shows server detail text,
 * only localized copy picked by the ErrorCode. The UI layer uses the returned key with
 * `stringResource(Res.string.<key>)`.
 *
 * ## Usage
 *
 * ```kotlin
 * val key = ErrorMessageMapper.keyFor(error.errorCode)
 * val message = stringResource(key.toStringResource()) // from Res.string
 * ```
 */
object ErrorMessageMapper {
    /**
     * String resource key for the given error code. Keys follow the pattern `error_code_<name>`.
     * Every ErrorCode enum entry has a corresponding string in values/ and values-id/.
     */
    fun keyFor(errorCode: ErrorCode): ErrorMessageKey = errorCodeMapping[errorCode] ?: ErrorMessageKey.UNKNOWN

    /**
     * Maps a DomainResult.Error to a string resource key.
     * First checks the error code, then falls back to HTTP status class mapping.
     */
    fun keyFor(error: DomainResult.Error): ErrorMessageKey {
        // If we have a specific error code (not UNKNOWN), use it
        if (error.errorCode != ErrorCode.UNKNOWN) {
            return keyFor(error.errorCode)
        }
        // Fall back to HTTP status class
        return keyForHttpStatus(error.code)
    }

    /**
     * Maps an HTTP status code to a generic string resource key based on the status class.
     * Used when no specific ErrorCode is available.
     */
    fun keyForHttpStatus(httpStatus: Int): ErrorMessageKey =
        when {
            httpStatus in HTTP_CLIENT_ERROR_RANGE -> ErrorMessageKey.HTTP_CLIENT_ERROR
            httpStatus in HTTP_SERVER_ERROR_RANGE -> ErrorMessageKey.HTTP_SERVER_ERROR
            else -> ErrorMessageKey.UNKNOWN
        }

    private val HTTP_CLIENT_ERROR_RANGE = 400..499
    private val HTTP_SERVER_ERROR_RANGE = 500..599

    /** Direct mapping from ErrorCode to ErrorMessageKey. Each ErrorCode has one key. */
    private val errorCodeMapping: Map<ErrorCode, ErrorMessageKey> =
        mapOf(
            ErrorCode.VALIDATION_FAILED to ErrorMessageKey.VALIDATION_FAILED,
            ErrorCode.INTERNAL_ERROR to ErrorMessageKey.INTERNAL_ERROR,
            ErrorCode.VERIFICATION_UNAVAILABLE to ErrorMessageKey.VERIFICATION_UNAVAILABLE,
            ErrorCode.UNAUTHORIZED to ErrorMessageKey.UNAUTHORIZED,
            ErrorCode.ADMISSION_INVALID to ErrorMessageKey.ADMISSION_INVALID,
            ErrorCode.NOT_FOUND to ErrorMessageKey.NOT_FOUND,
            ErrorCode.RATE_LIMITED to ErrorMessageKey.RATE_LIMITED,
            ErrorCode.QUEUE_CLOSED to ErrorMessageKey.QUEUE_CLOSED,
            ErrorCode.NOT_YET to ErrorMessageKey.NOT_YET,
            ErrorCode.FULL to ErrorMessageKey.FULL,
            ErrorCode.MISSED to ErrorMessageKey.MISSED,
            ErrorCode.EXPIRED to ErrorMessageKey.EXPIRED,
            ErrorCode.NO_STOCK to ErrorMessageKey.NO_STOCK,
            ErrorCode.TOO_FAR to ErrorMessageKey.TOO_FAR,
            ErrorCode.STREAM_FULL to ErrorMessageKey.STREAM_FULL,
            ErrorCode.ACCOUNT_HAS_PLACE_ELSEWHERE to ErrorMessageKey.ACCOUNT_HAS_PLACE_ELSEWHERE,
            ErrorCode.MOVED to ErrorMessageKey.MOVED,
            ErrorCode.WRONG_CODE to ErrorMessageKey.WRONG_CODE,
            ErrorCode.CHALLENGE_EXPIRED to ErrorMessageKey.CHALLENGE_EXPIRED,
            ErrorCode.CHALLENGE_LOCKED to ErrorMessageKey.CHALLENGE_LOCKED,
            ErrorCode.NIK_INVALID to ErrorMessageKey.NIK_INVALID,
            ErrorCode.NIK_SEAT_TAKEN to ErrorMessageKey.NIK_SEAT_TAKEN,
            ErrorCode.DUPLICATE_NIK to ErrorMessageKey.DUPLICATE_NIK,
            ErrorCode.ATTENDEE_NOT_VALIDATED to ErrorMessageKey.ATTENDEE_NOT_VALIDATED,
            ErrorCode.QUANTITY_OVER_RESERVATION to ErrorMessageKey.QUANTITY_OVER_RESERVATION,
            ErrorCode.DUKCAPIL_UNAVAILABLE to ErrorMessageKey.DUKCAPIL_UNAVAILABLE,
            ErrorCode.ACCOUNT_HAS_ACTIVE_BOOKING to ErrorMessageKey.ACCOUNT_HAS_ACTIVE_BOOKING,
            ErrorCode.CATEGORY_SOLD_OUT to ErrorMessageKey.CATEGORY_SOLD_OUT,
            ErrorCode.BOOKING_NOT_HELD to ErrorMessageKey.BOOKING_NOT_HELD,
            ErrorCode.BOOKING_NOT_PAID to ErrorMessageKey.BOOKING_NOT_PAID,
            ErrorCode.NOT_CANCELLABLE to ErrorMessageKey.NOT_CANCELLABLE,
            ErrorCode.CODE_INVALID to ErrorMessageKey.CODE_INVALID,
            ErrorCode.PAYMENT_PROVIDER_BUSY to ErrorMessageKey.PAYMENT_PROVIDER_BUSY,
            ErrorCode.FORBIDDEN to ErrorMessageKey.FORBIDDEN,
            ErrorCode.SERVICE_UNAVAILABLE to ErrorMessageKey.SERVICE_UNAVAILABLE,
            ErrorCode.TIKET_TIDAK_DITEMUKAN to ErrorMessageKey.TIKET_TIDAK_DITEMUKAN,
            ErrorCode.TIKET_SUDAH_DIGUNAKAN to ErrorMessageKey.TIKET_SUDAH_DIGUNAKAN,
            ErrorCode.TIKET_TIDAK_BERLAKU to ErrorMessageKey.TIKET_TIDAK_BERLAKU,
            ErrorCode.UNKNOWN to ErrorMessageKey.UNKNOWN,
        )
}

/**
 * Typed key for an error message string resource. Each key corresponds to a string in
 * values/strings.xml (EN) and values-id/strings.xml (ID).
 */
enum class ErrorMessageKey {
    // ErrorCode-specific keys (error_code_*)
    VALIDATION_FAILED,
    INTERNAL_ERROR,
    VERIFICATION_UNAVAILABLE,
    UNAUTHORIZED,
    ADMISSION_INVALID,
    NOT_FOUND,
    RATE_LIMITED,
    QUEUE_CLOSED,
    NOT_YET,
    FULL,
    MISSED,
    EXPIRED,
    NO_STOCK,
    TOO_FAR,
    STREAM_FULL,
    ACCOUNT_HAS_PLACE_ELSEWHERE,
    MOVED,
    WRONG_CODE,
    CHALLENGE_EXPIRED,
    CHALLENGE_LOCKED,
    NIK_INVALID,
    NIK_SEAT_TAKEN,
    DUPLICATE_NIK,
    ATTENDEE_NOT_VALIDATED,
    QUANTITY_OVER_RESERVATION,
    DUKCAPIL_UNAVAILABLE,
    ACCOUNT_HAS_ACTIVE_BOOKING,
    CATEGORY_SOLD_OUT,
    BOOKING_NOT_HELD,
    BOOKING_NOT_PAID,
    NOT_CANCELLABLE,
    CODE_INVALID,
    PAYMENT_PROVIDER_BUSY,
    FORBIDDEN,
    SERVICE_UNAVAILABLE,
    TIKET_TIDAK_DITEMUKAN,
    TIKET_SUDAH_DIGUNAKAN,
    TIKET_TIDAK_BERLAKU,
    UNKNOWN,

    // HTTP status class fallbacks (error_http_*)
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    ;

    /** The string resource name in strings.xml. */
    val resourceName: String
        get() =
            when (this) {
                HTTP_CLIENT_ERROR -> "error_http_client"
                HTTP_SERVER_ERROR -> "error_http_server"
                else -> "error_code_${name.lowercase()}"
            }
}
