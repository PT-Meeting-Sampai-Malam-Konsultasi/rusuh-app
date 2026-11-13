package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandMetrics
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val TabIconSize = 24.dp

/**
 * Shared bottom navigation bar used by every top-level tab screen (Event /
 * Antrian / Tiket / Akun). Pushed screens (Detail, Waiting Room opened from a
 * row) must NOT host it.
 *
 * Stateless and token-driven: it renders the [selectedTab] with active emphasis
 * and raises [onTabSelected] when another tab is tapped; it holds no navigation
 * state of its own. Tapping the already-selected tab is a no-op.
 *
 * Active emphasis mirrors the hi-fi `.tabbar a[aria-current]`: the active tab's
 * label becomes a filled chip (ink background, paper text) and its icon takes
 * the full ink color, while inactive tabs stay muted.
 *
 * @param selectedTab the tab the current screen represents.
 * @param onTabSelected invoked with the tapped tab; the nav layer navigates.
 */
@Composable
fun RusuhBottomBar(
    selectedTab: RusuhTab,
    onTabSelected: (RusuhTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        // Figma/hi-fi bar: paper background with a 2.5dp ink rule on top.
        color = RusuhBrandColors.Paper,
        contentColor = RusuhBrandColors.Ink,
    ) {
        // Keep the tabs clear of the system navigation bar (3-button / gesture area).
        Column(Modifier.navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(RusuhBrandMetrics.borderWidth).background(RusuhBrandColors.Ink))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = RusuhSpacing.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RusuhTab.entries.forEach { tab ->
                    BottomBarItem(
                        tab = tab,
                        selected = tab == selectedTab,
                        onClick = { if (tab != selectedTab) onTabSelected(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: RusuhTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Active tab takes the full on-surface ink; inactive tabs are muted. Mirrors
    // the hi-fi `.tabbar a` (muted) vs `a[aria-current]` (ink) contrast.
    val iconColor =
        if (selected) RusuhBrandColors.Ink else RusuhBrandColors.Ink2

    Column(
        modifier =
            modifier
                .clickable(role = Role.Tab, onClick = onClick)
                // Announce "<label>, selected" from the tab semantics; the decorative glyph is hidden below.
                .semantics(mergeDescendants = true) { this.selected = selected }
                .padding(vertical = RusuhSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(TabIconSize),
        )
        Spacer(Modifier.height(RusuhSpacing.xxs))
        TabLabel(tab = tab, selected = selected)
    }
}

/**
 * The tab caption. When [selected], it renders as a filled chip (on-surface
 * background, surface text) exactly like the hi-fi `a[aria-current] span`;
 * otherwise it is plain muted text.
 */
@Composable
private fun TabLabel(
    tab: RusuhTab,
    selected: Boolean,
) {
    if (selected) {
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = RusuhBrandColors.Paper,
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(RusuhBrandColors.Ink)
                    .padding(horizontal = RusuhSpacing.sm, vertical = RusuhSpacing.xxs),
        )
    } else {
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelMedium,
            color = RusuhBrandColors.Ink2,
        )
    }
}
