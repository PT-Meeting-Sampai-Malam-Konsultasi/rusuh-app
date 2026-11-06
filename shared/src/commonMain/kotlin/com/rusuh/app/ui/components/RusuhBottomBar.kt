package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.theme.RusuhSpacing

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
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = RusuhSpacing.bottomBarElevation,
    ) {
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
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = "${tab.label}${if (selected) ", terpilih" else ""}"
                    this.selected = selected
                }
                .padding(vertical = RusuhSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = tab.glyph,
            style = MaterialTheme.typography.titleMedium,
            color = iconColor,
            textAlign = TextAlign.Center,
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
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface)
                    .padding(horizontal = RusuhSpacing.sm, vertical = RusuhSpacing.xxs),
        )
    } else {
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
