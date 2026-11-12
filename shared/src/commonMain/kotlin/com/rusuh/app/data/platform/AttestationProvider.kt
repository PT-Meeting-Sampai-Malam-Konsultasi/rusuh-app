package com.rusuh.app.data.platform

/** Supplies the platform attestation token required by device registration. */
interface AttestationProvider {
    fun attestation(): String
}

/** Staging placeholder. TODO(real attestation): release builds must replace this with Play Integrity or App Attest. */
class DevAttestationProvider : AttestationProvider {
    override fun attestation(): String = "dev"
}
