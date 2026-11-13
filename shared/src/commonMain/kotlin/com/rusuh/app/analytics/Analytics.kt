package com.rusuh.app.analytics

/** Platform-agnostic analytics interface. Implementations must not throw. */
interface Analytics {
    fun log(
        event: String,
        params: Map<String, Any> = emptyMap(),
    )

    fun setUserId(id: String?)

    fun setUserProperty(
        name: String,
        value: String?,
    )

    fun setCollectionEnabled(enabled: Boolean)
}

/** Does nothing. Used in tests and when analytics is disabled. */
object NoOpAnalytics : Analytics {
    override fun log(
        event: String,
        params: Map<String, Any>,
    ) = Unit

    override fun setUserId(id: String?) = Unit

    override fun setUserProperty(
        name: String,
        value: String?,
    ) = Unit

    override fun setCollectionEnabled(enabled: Boolean) = Unit
}
