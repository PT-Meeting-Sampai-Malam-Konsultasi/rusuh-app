@file:Suppress("TooManyFunctions")

package com.rusuh.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.components.BrutalButton
import com.rusuh.app.ui.components.CountdownChip
import com.rusuh.app.ui.components.SeatCategoryCard
import com.rusuh.app.ui.components.StampChip
import com.rusuh.app.ui.components.StepHeader
import com.rusuh.app.ui.components.brutalBox
import com.rusuh.app.ui.components.formatRupiah
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.detail_back_description
import rusuh_app.shared.generated.resources.detail_bar_tickets
import rusuh_app.shared.generated.resources.detail_cap_chip
import rusuh_app.shared.generated.resources.detail_cap_message
import rusuh_app.shared.generated.resources.detail_categories_hint
import rusuh_app.shared.generated.resources.detail_email_change
import rusuh_app.shared.generated.resources.detail_email_hint
import rusuh_app.shared.generated.resources.detail_email_label
import rusuh_app.shared.generated.resources.detail_empty_body
import rusuh_app.shared.generated.resources.detail_empty_title
import rusuh_app.shared.generated.resources.detail_error_title
import rusuh_app.shared.generated.resources.detail_expired_action
import rusuh_app.shared.generated.resources.detail_expired_badge
import rusuh_app.shared.generated.resources.detail_expired_body
import rusuh_app.shared.generated.resources.detail_expired_description
import rusuh_app.shared.generated.resources.detail_expired_headline
import rusuh_app.shared.generated.resources.detail_expired_home
import rusuh_app.shared.generated.resources.detail_holders_hint
import rusuh_app.shared.generated.resources.detail_loading
import rusuh_app.shared.generated.resources.detail_loading_description
import rusuh_app.shared.generated.resources.detail_proceed
import rusuh_app.shared.generated.resources.detail_section_categories
import rusuh_app.shared.generated.resources.detail_section_email
import rusuh_app.shared.generated.resources.detail_section_holders
import rusuh_app.shared.generated.resources.detail_summary_admin_fee
import rusuh_app.shared.generated.resources.detail_summary_subtotal
import rusuh_app.shared.generated.resources.detail_timer_description
import rusuh_app.shared.generated.resources.detail_title
import rusuh_app.shared.generated.resources.detail_total

/**
 * Detail Ticket screen — FR-05 M2, aligned with the Figma "Detail Ticket · Booking" and
 * "Booking time expired" states.
 *
 * Pure `DetailScreen(state, dispatch)`: it renders [state] and reports user actions as
 * [DetailIntent]s. All state (quantities, attendees, countdown) is owned by [DetailViewModel]
 * and arrives through [state]; see the stateful `DetailRoute` host.
 */
@Composable
fun DetailScreen(
    state: DetailUiState,
    dispatch: (DetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining =
        when (state) {
            is DetailUiState.Content -> state.remainingSeconds
            is DetailUiState.Expired -> 0
            else -> null
        }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val expired = state is DetailUiState.Expired
    LaunchedEffect(expired) {
        if (expired) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(RusuhColors.BrandPaper)) {
        // imePadding on the container: scroll area and sticky bar shrink above the keyboard.
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            DetailTopBar(remainingSeconds = remaining, onBack = { dispatch(DetailIntent.Back) })
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    DetailUiState.Loading -> CenteredBox { LoadingState() }
                    DetailUiState.Empty ->
                        CenteredBox {
                            CenteredMessage(
                                title = stringResource(Res.string.detail_empty_title),
                                body = stringResource(Res.string.detail_empty_body),
                            )
                        }
                    is DetailUiState.Error ->
                        CenteredBox {
                            CenteredMessage(
                                title = stringResource(Res.string.detail_error_title),
                                body = state.message,
                                titleColor = RusuhColors.BrandDanger,
                            )
                        }
                    is DetailUiState.Content ->
                        DetailContentBody(state, dispatch)
                    // Inert: only the (PII-free) header is composed, never attendee names/NIKs or
                    // steppers/fields, so focus and TalkBack cannot reach them.
                    is DetailUiState.Expired -> ExpiredBackdrop(state.detail)
                }
            }
        }
        if (state is DetailUiState.Expired) {
            ExpiredOverlay(dispatch)
        }
    }
}

@Composable
private fun DetailTopBar(
    remainingSeconds: Int?,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(RusuhColors.BrandPaper)) {
        Box(modifier = Modifier.fillMaxWidth().height(RusuhSpacing.topBarHeight)) {
            val backDescription = stringResource(Res.string.detail_back_description)
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(RusuhSpacing.topBarHeight)
                        .semantics { contentDescription = backDescription }
                        .clickable(role = Role.Button, onClick = onBack),
            ) {
                // ← is the left arrow glyph (no icon dependency, matches the other screens).
                Text(
                    text = "←",
                    style = RusuhBrandTextStyles.stepperSymbol.body(),
                    color = RusuhColors.BrandInk,
                )
            }
            Text(
                text = stringResource(Res.string.detail_title),
                style = RusuhBrandTextStyles.itemTitle.display(),
                color = RusuhColors.BrandInk,
                modifier = Modifier.align(Alignment.Center),
            )
            if (remainingSeconds != null) {
                val clock = formatCountdown(remainingSeconds)
                CountdownChip(
                    text = clock,
                    description = stringResource(Res.string.detail_timer_description, clock),
                    warn = remainingSeconds <= COUNTDOWN_WARN_SECONDS,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = RusuhSpacing.xl),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
    }
}

@Composable
private fun DetailContentBody(
    state: DetailUiState.Content,
    dispatch: (DetailIntent) -> Unit,
) {
    val detail = state.detail
    val form = state.form
    val focusManager = LocalFocusManager.current
    val maxTickets = detail.maxTickets
    val total = form.totalTickets
    val capReached = isCapReached(total, maxTickets)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // Tap outside a field: drop focus (children that consume the tap are unaffected).
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RusuhSpacing.xl)
                    .padding(top = RusuhSpacing.xl, bottom = RusuhSpacing.xxl),
        ) {
            EventHeader(detail)
            Spacer(Modifier.height(RusuhSpacing.xxl))

            StepHeader(
                number = 1,
                title = stringResource(Res.string.detail_section_categories),
                note = stringResource(Res.string.detail_categories_hint, maxTickets),
            )
            detail.categories.forEach { category ->
                val quantity = form.quantities[category.id] ?: 0
                SeatCategoryCard(
                    data = category,
                    quantity = quantity,
                    canIncrement = !capReached && quantity < detail.stockOf(category.id),
                    onQuantityChange = { new ->
                        dispatch(
                            if (new > quantity) {
                                DetailIntent.IncrementCategory(category.id)
                            } else {
                                DetailIntent.DecrementCategory(category.id)
                            },
                        )
                    },
                )
            }
            if (capReached) {
                Spacer(Modifier.height(RusuhSpacing.lg))
                CapNotice(total = total, maxTickets = maxTickets)
            }
            Spacer(Modifier.height(RusuhSpacing.xxxl))

            StepHeader(
                number = 2,
                title = stringResource(Res.string.detail_section_holders),
                note = stringResource(Res.string.detail_holders_hint),
            )
            form.attendees.forEachIndexed { index, attendee ->
                // Keyed by the stable ticket id so per-ticket UI state (touched/focused) stays put.
                key(attendee.id) {
                    AttendeeBlock(
                        ticketNumber = index + 1,
                        categoryName = detail.categories.firstOrNull { it.id == attendee.categoryId }?.name.orEmpty(),
                        attendee = attendee,
                        duplicateOf = form.duplicateOf(index),
                        validationStatus = attendee.validationStatus,
                        isLast = index == form.attendees.lastIndex,
                        onNameChange = { dispatch(DetailIntent.AttendeeNameChanged(index, it)) },
                        onNikChange = { dispatch(DetailIntent.AttendeeNikChanged(index, it)) },
                        onRetryValidation = { dispatch(DetailIntent.RetryValidation(index)) },
                    )
                }
            }
            Spacer(Modifier.height(RusuhSpacing.xxxl))

            StepHeader(number = 3, title = stringResource(Res.string.detail_section_email))
            Spacer(Modifier.height(RusuhSpacing.xl))
            EmailBlock(email = detail.accountEmail, onChange = { dispatch(DetailIntent.ChangeEmail) })

            if (total > 0) {
                Spacer(Modifier.height(RusuhSpacing.xxl))
                PriceSummary(
                    subtotal = state.subtotalRupiah,
                    adminFee = detail.adminFeeRupiah,
                    total = state.totalRupiah,
                )
            }
        }
        BottomBar(
            tickets = total,
            total = state.totalRupiah,
            enabled = state.canProceed,
            onProceed = { dispatch(DetailIntent.Proceed) },
        )
    }
}

/** What stays behind the "Waktu habis" sheet: the event header only, no attendee data. */
@Composable
private fun ExpiredBackdrop(detail: DetailContent) {
    Column(modifier = Modifier.fillMaxSize().padding(RusuhSpacing.xl)) { EventHeader(detail) }
}

@Composable
private fun EventHeader(detail: DetailContent) {
    val description =
        buildString {
            append(detail.eventTitle)
            detail.eventSubtitle?.let { append(", $it") }
            append(". ${detail.eventMeta}")
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xl),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        // Poster slot: branded placeholder (no image library in commonMain for one slot). M4
        // supplies the real poster from the event payload.
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier =
                Modifier
                    .size(width = RusuhSpacing.posterWidth, height = RusuhSpacing.posterHeight)
                    .brutalBox(
                        fill = RusuhColors.BrandBlue,
                        borderWidth = 0.dp,
                        shadow = RusuhColors.BrandInk,
                        shadowOffset = RusuhSpacing.shadowSm,
                    )
                    .padding(RusuhSpacing.xs),
        ) {
            Text(
                text = detail.eventTitle.uppercase(),
                style = RusuhBrandTextStyles.eyebrow.display(),
                color = RusuhColors.BrandPaper,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xs)) {
            Text(
                text = detail.eventTitle,
                style = RusuhBrandTextStyles.hero.display(),
                color = RusuhColors.BrandInk,
            )
            detail.eventSubtitle?.let {
                Text(
                    text = it,
                    style = RusuhBrandTextStyles.subtitle.display(),
                    color = RusuhColors.BrandMagentaDeep,
                )
            }
            Text(
                text = detail.eventMeta,
                style = RusuhBrandTextStyles.note.body(),
                color = RusuhColors.BrandMuted,
            )
        }
    }
}

/** "3/3 — Batas 3 tiket tercapai…" notice shown once the order cap is hit. */
@Composable
private fun CapNotice(
    total: Int,
    maxTickets: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        Text(
            text = stringResource(Res.string.detail_cap_chip, total, maxTickets),
            style = RusuhBrandTextStyles.chip.display(),
            color = Color.White,
            modifier =
                Modifier
                    .background(RusuhColors.BrandMagenta)
                    .padding(horizontal = RusuhSpacing.md, vertical = RusuhSpacing.xs),
        )
        Text(
            text = stringResource(Res.string.detail_cap_message, maxTickets),
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandInk,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Read-only e-ticket destination: the account email, with a way to change it later. */
@Composable
private fun EmailBlock(
    email: String,
    onChange: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg)) {
        Text(
            text = stringResource(Res.string.detail_email_label),
            style = RusuhBrandTextStyles.label.body(),
            color = RusuhColors.BrandInk,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .brutalBox(fill = RusuhColors.BrandCream)
                    .padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.lg),
        ) {
            Text(
                text = email,
                style = RusuhBrandTextStyles.input.body(),
                color = RusuhColors.BrandInk,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.detail_email_change),
                style = RusuhBrandTextStyles.label.body(),
                color = RusuhColors.BrandMuted,
                textDecoration = TextDecoration.Underline,
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onChange)
                        .padding(start = RusuhSpacing.lg, top = RusuhSpacing.md, bottom = RusuhSpacing.md),
            )
        }
        FieldNote(stringResource(Res.string.detail_email_hint))
    }
}

/** Ticket subtotal, ONE "Biaya layanan" line per order, and the total (ADR-0010). */
@Composable
private fun PriceSummary(
    subtotal: Long,
    adminFee: Long,
    total: Long,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        modifier =
            Modifier
                .fillMaxWidth()
                .brutalBox(fill = RusuhColors.BrandCream)
                .padding(RusuhSpacing.xl),
    ) {
        SummaryRow(stringResource(Res.string.detail_summary_subtotal), formatRupiah(subtotal))
        SummaryRow(stringResource(Res.string.detail_summary_admin_fee), formatRupiah(adminFee))
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderThin).background(RusuhColors.BrandHairline))
        SummaryRow(stringResource(Res.string.detail_total), formatRupiah(total), strong = true)
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amount: String,
    strong: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = if (strong) RusuhBrandTextStyles.label.body() else RusuhBrandTextStyles.bodyMedium.body(),
            color = if (strong) RusuhColors.BrandInk else RusuhColors.BrandMuted,
        )
        Text(
            text = amount,
            style = RusuhBrandTextStyles.price.body(),
            color = RusuhColors.BrandInk,
        )
    }
}

/** Sticky bottom bar: ticket count + total on the left, "Lanjut bayar →" on the right. */
@Composable
private fun BottomBar(
    tickets: Int,
    total: Long,
    enabled: Boolean,
    onProceed: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(RusuhColors.BrandPaper)
                // The IME inset already covers the nav bar; don't add it twice while typing.
                .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
    ) {
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
        PriceAndAction(
            modifier = Modifier.fillMaxWidth().padding(RusuhSpacing.xl),
            gap = RusuhSpacing.xl,
            price = {
                Column {
                    Text(
                        text = stringResource(Res.string.detail_bar_tickets, tickets),
                        style = RusuhBrandTextStyles.note.body(),
                        color = RusuhColors.BrandMuted,
                    )
                    Text(
                        text = formatRupiah(total),
                        style = RusuhBrandTextStyles.stat.display(),
                        color = RusuhColors.BrandInk,
                    )
                }
            },
            action = {
                BrutalButton(
                    text = stringResource(Res.string.detail_proceed),
                    onClick = onProceed,
                    enabled = enabled,
                    modifier = Modifier.padding(end = RusuhSpacing.xs, bottom = RusuhSpacing.xs),
                )
            },
        )
    }
}

/**
 * Price on the left, CTA on the right when both fit on one line at their natural
 * (unwrapped) width; otherwise the CTA drops below at full width. Never clips the
 * total and never wraps the CTA label (large font scale, narrow phones).
 */
@Composable
private fun PriceAndAction(
    gap: Dp,
    price: @Composable () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(content = {
        price()
        action()
    }, modifier = modifier) { measurables, constraints ->
        val (priceM, actionM) = measurables
        val gapPx = gap.roundToPx()
        val maxW = constraints.maxWidth
        val priceW = priceM.maxIntrinsicWidth(Int.MAX_VALUE).coerceAtMost(maxW)
        val actionW = actionM.maxIntrinsicWidth(Int.MAX_VALUE)
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        if (priceW + gapPx + actionW <= maxW) {
            val p = priceM.measure(loose.copy(maxWidth = priceW))
            val a = actionM.measure(loose.copy(minWidth = actionW, maxWidth = maxW - priceW - gapPx))
            val h = maxOf(p.height, a.height)
            layout(maxW, h) {
                p.placeRelative(0, (h - p.height) / 2)
                a.placeRelative(maxW - a.width, (h - a.height) / 2)
            }
        } else {
            val p = priceM.measure(loose.copy(maxWidth = maxW))
            val a = actionM.measure(loose.copy(minWidth = maxW, maxWidth = maxW))
            layout(maxW, p.height + gapPx + a.height) {
                p.placeRelative(0, 0)
                a.placeRelative(0, p.height + gapPx)
            }
        }
    }
}

/** Dim layer + bottom sheet shown when the order window has elapsed ("Waktu habis"). */
@Composable
private fun BoxScope.ExpiredOverlay(dispatch: (DetailIntent) -> Unit) {
    val description = stringResource(Res.string.detail_expired_description)
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .background(RusuhColors.ExpiredScrim)
                // Swallow taps so the dimmed page behind the sheet is inert.
                .pointerInput(Unit) { detectTapGestures { } },
    )
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(RusuhColors.BrandPaper)
                .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
        Column(
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = RusuhSpacing.xxl, vertical = RusuhSpacing.xxl),
        ) {
            StampChip(text = stringResource(Res.string.detail_expired_badge))
            Spacer(Modifier.height(RusuhSpacing.xl))
            Text(
                text = stringResource(Res.string.detail_expired_headline),
                style = RusuhBrandTextStyles.sheetHeadline.display(),
                color = RusuhColors.BrandInk,
            )
            Spacer(Modifier.height(RusuhSpacing.lg))
            Text(
                text = stringResource(Res.string.detail_expired_body),
                style = RusuhBrandTextStyles.bodyLarge.body(),
                color = RusuhColors.BrandInk,
            )
            Spacer(Modifier.height(RusuhSpacing.xxl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrutalButton(
                    text = stringResource(Res.string.detail_expired_action),
                    onClick = { dispatch(DetailIntent.QueueAgain) },
                    modifier = Modifier.padding(end = RusuhSpacing.md, bottom = RusuhSpacing.md),
                )
                Spacer(Modifier.size(RusuhSpacing.lg))
                Text(
                    text = stringResource(Res.string.detail_expired_home),
                    style = RusuhBrandTextStyles.subtitle.body(),
                    color = RusuhColors.BrandInk,
                    textDecoration = TextDecoration.Underline,
                    modifier =
                        Modifier
                            .clickable(role = Role.Button) { dispatch(DetailIntent.GoHome) }
                            .padding(RusuhSpacing.md),
                )
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun LoadingState() {
    val loadingDescription = stringResource(Res.string.detail_loading_description)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.semantics { contentDescription = loadingDescription },
    ) {
        CircularProgressIndicator(color = RusuhColors.BrandMagenta)
        Spacer(Modifier.height(RusuhSpacing.xl))
        Text(
            text = stringResource(Res.string.detail_loading),
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    titleColor: Color = RusuhColors.BrandInk,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(RusuhSpacing.xl)
                .semantics { contentDescription = "$title. $body" },
    ) {
        Text(
            text = title,
            style = RusuhBrandTextStyles.sectionTitle.display(),
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        Text(
            text = body,
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandMuted,
            textAlign = TextAlign.Center,
        )
    }
}
