package com.rusuh.app.ui.components

import com.rusuh.app.qr.QrMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QrCodeTest {
    @Test
    fun quietZoneIsFourModulesOnEachSide() {
        assertEquals(4, QR_QUIET_ZONE_MODULES)
        val matrix = QrMatrix.encode("RSH1.SMD-FB-04827.Q2hlY2tJbi1zaWduZWQ")
        assertEquals(29 + 8, matrix.modulesWithQuietZone())
    }

    @Test
    fun payloadTooLongMapsToFailure() {
        val result = encodeQr("x".repeat(QrMatrix.maxBytes() + 1))
        assertIs<QrEncodingResult.Failed>(result)
    }

    @Test
    fun validPayloadMapsToEncodedState() {
        val result = encodeQr("ticket-code")
        assertIs<QrEncodingResult.Encoded>(result)
        assertEquals(1, result.matrix.version)
    }
}
