package com.rusuh.app.ui.components

import com.rusuh.app.qr.QrMatrix
import kotlin.test.Test
import kotlin.test.assertEquals

class QrCodeTest {
    @Test
    fun quietZoneIsFourModulesOnEachSide() {
        assertEquals(4, QR_QUIET_ZONE_MODULES)
        val matrix = QrMatrix.encode("RSH1.SMD-FB-04827.Q2hlY2tJbi1zaWduZWQ")
        assertEquals(29 + 8, matrix.modulesWithQuietZone())
    }
}
