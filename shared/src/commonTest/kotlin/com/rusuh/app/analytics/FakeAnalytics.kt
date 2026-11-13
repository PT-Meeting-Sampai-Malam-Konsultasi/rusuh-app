package com.rusuh.app.analytics

class FakeAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private var userIdValue: String? = null
    val recordedUserId: String? get() = userIdValue
    val userProperties = mutableMapOf<String, String?>()
    var collectionEnabled: Boolean? = null

    override fun log(
        event: String,
        params: Map<String, Any>,
    ) {
        events.add(event to params)
    }

    override fun setUserId(id: String?) {
        userIdValue = id
    }

    override fun setUserProperty(
        name: String,
        value: String?,
    ) {
        userProperties[name] = value
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }
}
