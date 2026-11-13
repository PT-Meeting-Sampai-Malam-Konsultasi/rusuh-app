package com.rusuh.app.analytics

/** Event and parameter names shared by Android and web. */
object AnalyticsEvents {
    const val SCREEN_VIEW = "screen_view"
    const val PARAM_SCREEN_NAME = "screen_name"
    const val PARAM_SCREEN_CLASS = "screen_class"

    const val VIEW_ITEM_LIST = "view_item_list"
    const val SELECT_ITEM = "select_item"
    const val QUEUE_JOIN = "queue_join"
    const val QUEUE_TURN = "queue_turn"
    const val QUEUE_CLAIM = "queue_claim"
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val VIEW_ITEM = "view_item"
    const val ADD_TO_CART = "add_to_cart"
    const val HOLDERS_COMPLETE = "holders_complete"
    const val BEGIN_CHECKOUT = "begin_checkout"
    const val ADD_PAYMENT_INFO = "add_payment_info"
    const val PURCHASE = "purchase"
    const val VIEW_TICKET = "view_ticket"
    const val QUEUE_LEAVE = "queue_leave"
    const val QUEUE_REJOIN = "queue_rejoin"
    const val SOLD_OUT_SEEN = "sold_out_seen"
    const val ORDER_EXPIRED = "order_expired"
    const val NIK_REJECTED = "nik_rejected"
    const val PAYMENT_FAILED = "payment_failed"
    const val PAYMENT_EXPIRED = "payment_expired"
    const val TICKET_DOWNLOAD = "ticket_download"
    const val TICKET_ADD_CALENDAR = "ticket_add_calendar"
    const val SHARE = "share"

    const val EVENT_ID = "event_id"
    const val BOOKING_ID = "booking_id"
    const val TRANSACTION_ID = "transaction_id"
    const val VALUE = "value"
    const val CURRENCY = "currency"
    const val ITEMS = "items"
    const val PAYMENT_TYPE = "payment_type"
    const val OUTCOME = "outcome"
    const val REASON = "reason"
    const val WHERE = "where"
    const val STEP = "step"
    const val FROM = "from"
    const val VIA = "via"
    const val METHOD = "method"
    const val POSITION_BUCKET = "position_bucket"
    const val WAIT_BUCKET = "wait_bucket"
    const val TICKET_COUNT = "ticket_count"
    const val MYSELF_USED = "myself_used"
    const val LANGUAGE = "language"
    const val SIGNED_IN = "signed_in"
    const val APP_LANGUAGE = "app_language"
    const val ITEM_LIST_NAME = "item_list_name"
    const val ITEM_NAME = "item_name"
    const val ITEM_CATEGORY = "item_category"
    const val ITEM_BRAND = "item_brand"
    const val PRICE = "price"
    const val QUANTITY = "quantity"

    const val OUTCOME_JOINED = "joined"
    const val OUTCOME_RESUMED = "resumed"
    const val OUTCOME_NO_QUEUE = "no_queue"
    const val OUTCOME_SOLD_OUT = "sold_out"
    const val OUTCOME_ELSEWHERE = "elsewhere"
    const val OUTCOME_NOT_FOUND = "not_found"
    const val OUTCOME_ADMITTED = "admitted"
    const val OUTCOME_MISSED = "missed"
    const val OUTCOME_PAUSED = "paused"
    const val REASON_LEFT = "left"
    const val REASON_MISSED = "missed"
    const val REASON_NIK_INVALID = "NIK_INVALID"
    const val REASON_NIK_SEAT_TAKEN = "NIK_SEAT_TAKEN"
    const val REASON_DUPLICATE_NIK = "DUPLICATE_NIK"
    const val REASON_DUKCAPIL_UNAVAILABLE = "DUKCAPIL_UNAVAILABLE"
    const val WHERE_QUEUE = "queue"
    const val WHERE_ORDER = "order"
    const val STEP_SELECT = "select"
    const val STEP_HOLDERS = "holders"
    const val FROM_ACCOUNT = "account"
    const val FROM_QUEUE = "queue"
    const val FROM_ORDER = "order"
    const val FROM_TICKETS = "tickets"
    const val VIA_GOOGLE = "google"
    const val VIA_OUTLOOK = "outlook"
    const val VIA_ICS = "ics"
    const val VIA_DEVICE = "device"
    const val METHOD_EMAIL_CODE = "email_code"
    const val METHOD_PDF_PRINT = "pdf_print"
    const val METHOD_PDF = "pdf"
    const val METHOD_WALLET = "wallet"
    const val CONTENT_TYPE = "content_type"
    const val CONTENT_EVENT = "event"
    const val ITEM_ID = "item_id"
    const val PAYMENT_QRIS = "qris"
    const val PAYMENT_GOPAY = "gopay"
    const val PAYMENT_SHOPEEPAY = "shopeepay"
    const val PAYMENT_BCA_VA = "bca_va"
    const val PAYMENT_MANDIRI_VA = "mandiri_va"
    const val PAYMENT_BNI_VA = "bni_va"
    const val PAYMENT_BRI_VA = "bri_va"
    const val PAYMENT_PERMATA_VA = "permata_va"
    const val PAYMENT_CIMB_VA = "cimb_va"
    const val PAYMENT_CARD = "card"
    const val POSITION_1_100 = "1-100"
    const val POSITION_101_1000 = "101-1000"
    const val POSITION_1001_10000 = "1001-10000"
    const val POSITION_10000_PLUS = "10000+"
    const val WAIT_0_60 = "0-60"
    const val WAIT_61_300 = "61-300"
    const val WAIT_301_900 = "301-900"
    const val WAIT_900_PLUS = "900+"

    fun positionBucket(position: Int): String? =
        when {
            position < 1 -> null
            position <= 100 -> POSITION_1_100
            position <= 1_000 -> POSITION_101_1000
            position <= 10_000 -> POSITION_1001_10000
            else -> POSITION_10000_PLUS
        }

    fun waitBucket(seconds: Int): String? =
        when {
            seconds < 0 -> null
            seconds <= 60 -> WAIT_0_60
            seconds <= 300 -> WAIT_61_300
            seconds <= 900 -> WAIT_301_900
            else -> WAIT_900_PLUS
        }
}
