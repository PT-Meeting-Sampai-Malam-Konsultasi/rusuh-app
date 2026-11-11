package com.rusuh.app.domain.model

/**
 * Stable machine codes of the Rusuh API (`ErrorCode` schema in the OpenAPI contract).
 * The UI maps a code to localized copy; logic layers never carry user-facing text.
 */
enum class ErrorCode {
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

    /** Fallback for a code this app version does not know yet. */
    UNKNOWN,
    ;

    companion object {
        /** Parses a wire value, falling back to [UNKNOWN] for null or unseen strings. */
        fun fromWire(value: String?): ErrorCode = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
