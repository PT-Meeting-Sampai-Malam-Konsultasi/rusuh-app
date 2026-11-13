package com.rusuh.app.crash

class FakeCrashReporter : CrashReporter {
    val nonFatals = mutableListOf<Throwable>()
    val customKeys = mutableMapOf<String, String>()
    val logs = mutableListOf<String>()
    var userId: String? = null
        private set
    var collectionEnabled: Boolean? = null
        private set

    override fun recordNonFatal(throwable: Throwable) {
        nonFatals.add(throwable)
    }

    override fun setCustomKey(
        key: String,
        value: String,
    ) {
        customKeys[key] = value
    }

    override fun log(message: String) {
        logs.add(message)
    }

    override fun setUserId(id: String?) {
        userId = id
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }

    fun clear() {
        nonFatals.clear()
        customKeys.clear()
        logs.clear()
        userId = null
        collectionEnabled = null
    }
}
