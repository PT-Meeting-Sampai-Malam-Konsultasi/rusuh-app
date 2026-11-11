package com.rusuh.app.data.network

/** The iOS simulator shares the host's network, so the mock is plain localhost. */
actual val platformMockBaseUrl: String = "http://localhost:3000"
