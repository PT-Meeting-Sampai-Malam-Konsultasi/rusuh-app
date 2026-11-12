package com.rusuh.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rusuh.app.feature.queues.AppLanguageOption
import com.rusuh.app.feature.queues.LanguageToggle
import com.rusuh.app.ui.components.brutalBox
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.sign_in_back
import rusuh_app.shared.generated.resources.sign_in_event_description
import rusuh_app.shared.generated.resources.sign_in_event_label
import rusuh_app.shared.generated.resources.sign_in_event_opens
import rusuh_app.shared.generated.resources.sign_in_info_1_body
import rusuh_app.shared.generated.resources.sign_in_info_1_title
import rusuh_app.shared.generated.resources.sign_in_info_2_body
import rusuh_app.shared.generated.resources.sign_in_info_2_title
import rusuh_app.shared.generated.resources.sign_in_info_3_body
import rusuh_app.shared.generated.resources.sign_in_info_3_title
import rusuh_app.shared.generated.resources.sign_in_top_title

private const val TAG_TILT_DEGREES = -3f
private const val BLOB_ALPHA = 0.85f
private val BANNER_MIN_HEIGHT = 80.dp
private val BLOB_HEIGHT = 36.dp
private val BLOB_WIDTH = 190.dp

/**
 * Full login screen (#101), a pushed route without bottom bar: top bar (back, "Masuk atau daftar",
 * ID|EN toggle), the event the user continues to, the shared [SignInContent], then the thick rule
 * and the three info blocks. Stateless; back raises [SignInActions.onDismiss].
 */
@Composable
fun SignInScreen(
    state: SignInUiState,
    event: SignInEventContext,
    modifier: Modifier = Modifier,
    actions: SignInActions = SignInActions(),
) {
    Column(modifier = modifier.fillMaxSize().background(RusuhColors.BrandPaper)) {
        SignInTopBar(onBack = actions.onDismiss)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.xl),
        ) {
            Text(
                text = stringResource(Res.string.sign_in_event_label).uppercase(),
                style = RusuhBrandTextStyles.eyebrow.body(),
                color = RusuhColors.BrandMuted,
            )
            Spacer(Modifier.height(RusuhSpacing.md))
            SignInEventBanner(event)
            Spacer(Modifier.height(RusuhSpacing.xxl))
            SignInContent(state = state, actions = actions, variant = SignInVariant.Full, autoFocusEmail = true)
            Spacer(Modifier.height(RusuhSpacing.xxl))
            SignInInfoBlocks()
        }
    }
}

@Composable
private fun SignInTopBar(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(RusuhColors.BrandPaper).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(RusuhSpacing.appBarHeight).padding(horizontal = RusuhSpacing.md),
        ) {
            val back = stringResource(Res.string.sign_in_back)
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(RusuhSpacing.actionMinHeight)
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = back },
            ) {
                // Arrow glyph, not copy; the description above is what TalkBack reads.
                Text(text = "←", style = RusuhBrandTextStyles.stat.display(), color = RusuhColors.BrandInk)
            }
            Text(
                text = stringResource(Res.string.sign_in_top_title),
                style = RusuhBrandTextStyles.subtitle.display(),
                color = RusuhColors.BrandInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box(Modifier.padding(end = RusuhSpacing.md)) { LanguageToggle(selected = AppLanguageOption.Indonesian) }
        }
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
    }
}

/** Blue event banner: opening time, event name, tilted tag (Figma "SAMUDRA" card). */
@Composable
internal fun SignInEventBanner(
    event: SignInEventContext,
    modifier: Modifier = Modifier,
) {
    val opens = stringResource(Res.string.sign_in_event_opens, event.openingTime)
    val description = stringResource(Res.string.sign_in_event_description, opens, event.name, event.tag)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // Room for the hard shadow.
                .padding(end = RusuhSpacing.shadowMd, bottom = RusuhSpacing.shadowMd)
                .heightIn(min = BANNER_MIN_HEIGHT)
                .brutalBox(
                    fill = RusuhColors.Blue,
                    shadow = RusuhColors.Ink,
                    shadowOffset = RusuhSpacing.shadowMd,
                )
                .drawBehind {
                    // Soft pink glow on the right half, as in the design.
                    val width = BLOB_WIDTH.toPx()
                    val height = BLOB_HEIGHT.toPx()
                    drawOval(
                        brush =
                            Brush.horizontalGradient(
                                listOf(RusuhColors.Blue, RusuhColors.Pink.copy(alpha = BLOB_ALPHA)),
                                startX = size.width - width,
                                endX = size.width,
                            ),
                        topLeft = Offset(size.width - width, (size.height - height) / 2),
                        size = Size(width, height),
                    )
                }
                .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.lg),
        ) {
            Text(
                text = opens.uppercase(),
                style = RusuhBrandTextStyles.eyebrow.body(),
                color = RusuhColors.BrandPaper,
            )
            Spacer(Modifier.height(RusuhSpacing.xs))
            Text(
                text = event.name.uppercase(),
                style = RusuhBrandTextStyles.hero.display(),
                color = RusuhColors.BrandPaper,
            )
        }
        Text(
            text = event.tag,
            style = RusuhBrandTextStyles.label.display(),
            color = RusuhColors.PinkInk,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = RusuhSpacing.lg)
                    .rotate(TAG_TILT_DEGREES)
                    .background(RusuhColors.PaperCard)
                    .padding(horizontal = RusuhSpacing.md, vertical = RusuhSpacing.xs),
        )
    }
}

/** Thick rule and the three reassurance blocks below the login form (full variant only). */
@Composable
private fun SignInInfoBlocks() {
    Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
    InfoBlock(Res.string.sign_in_info_1_title, Res.string.sign_in_info_1_body, divider = true)
    InfoBlock(Res.string.sign_in_info_2_title, Res.string.sign_in_info_2_body, divider = true)
    InfoBlock(Res.string.sign_in_info_3_title, Res.string.sign_in_info_3_body, divider = false)
}

@Composable
private fun InfoBlock(
    title: org.jetbrains.compose.resources.StringResource,
    body: org.jetbrains.compose.resources.StringResource,
    divider: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = RusuhSpacing.lg).semantics(mergeDescendants = true) {}) {
        Text(
            text = stringResource(title),
            style = RusuhBrandTextStyles.itemTitle.display(),
            color = RusuhColors.BrandInk,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(RusuhSpacing.sm))
        Text(
            text = stringResource(body),
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandMuted,
        )
    }
    if (divider) Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderThin).background(RusuhColors.BrandHairline))
}
