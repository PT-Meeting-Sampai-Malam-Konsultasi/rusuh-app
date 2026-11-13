@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.account

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables (no @Preview annotation: commonMain has no
// uiTooling-preview dependency, same convention as the other feature previews).
// The ID/EN variants follow the device language (strings live in values/,
// values-id/, and values-in/).
// Synthetic data only.

private val sampleProfile =
    AccountProfileUi(name = "Nadia Saputri", email = "nadia.saputri@example.com", emailVerified = true)
private val sampleActions = AccountProfileActions(onSignOut = {})

@Composable
internal fun AccountProfilePreview() {
    RusuhTheme { AccountProfileContent(profile = sampleProfile, actions = sampleActions) }
}

@Composable
internal fun AccountProfileLongNamePreview() {
    RusuhTheme {
        AccountProfileContent(
            profile = sampleProfile.copy(name = "Raden Mas Adityawarman Kusumaningtyas Wijayanegara Hadiningrat"),
            actions = sampleActions,
        )
    }
}

@Composable
internal fun AccountProfileLongEmailPreview() {
    RusuhTheme {
        AccountProfileContent(
            profile = sampleProfile.copy(email = "nadia.saputri.very.long.address.for.testing@subdomain.example.com"),
            actions = sampleActions,
        )
    }
}

@Composable
internal fun AccountProfileWithChangeEmailPreview() {
    RusuhTheme {
        AccountProfileContent(
            profile = sampleProfile,
            actions = AccountProfileActions(onSignOut = {}, onChangeEmail = {}),
        )
    }
}

@Composable
internal fun AccountProfileLargeFontPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
        RusuhTheme {
            AccountProfileContent(
                profile = sampleProfile.copy(name = "Nadia Saputri Wulandari"),
                actions = AccountProfileActions(onSignOut = {}, onChangeEmail = {}),
            )
        }
    }
}

/** 2x font on a narrow (320dp) width with a long email: buttons must wrap, link keeps its target. */
@Composable
internal fun AccountProfileLargeFontNarrowPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
        RusuhTheme {
            AccountProfileContent(
                profile = sampleProfile.copy(email = "nadia.saputri.very.long.address@subdomain.example.com"),
                actions = AccountProfileActions(onSignOut = {}, onChangeEmail = {}),
                modifier = Modifier.width(NARROW_WIDTH),
            )
        }
    }
}

private const val LARGE_FONT_SCALE = 2f
private val NARROW_WIDTH = 320.dp
