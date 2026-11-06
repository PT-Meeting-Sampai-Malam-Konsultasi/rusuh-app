@file:Suppress("MatchingDeclarationName", "TooManyFunctions")

package com.rusuh.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.rusuh.app.ui.components.SeatCategoryCard
import com.rusuh.app.ui.components.SeatCategoryUi
import com.rusuh.app.ui.components.formatRupiah
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.action_back
import rusuh_app.shared.generated.resources.detail_cap_reached
import rusuh_app.shared.generated.resources.detail_cap_selected
import rusuh_app.shared.generated.resources.detail_categories_hint
import rusuh_app.shared.generated.resources.detail_empty_body
import rusuh_app.shared.generated.resources.detail_empty_title
import rusuh_app.shared.generated.resources.detail_error_title
import rusuh_app.shared.generated.resources.detail_expired_action
import rusuh_app.shared.generated.resources.detail_expired_badge
import rusuh_app.shared.generated.resources.detail_expired_body
import rusuh_app.shared.generated.resources.detail_expired_description
import rusuh_app.shared.generated.resources.detail_expired_headline
import rusuh_app.shared.generated.resources.detail_loading
import rusuh_app.shared.generated.resources.detail_loading_description
import rusuh_app.shared.generated.resources.detail_nik_example
import rusuh_app.shared.generated.resources.detail_nik_field_label
import rusuh_app.shared.generated.resources.detail_nik_field_placeholder
import rusuh_app.shared.generated.resources.detail_nik_hint
import rusuh_app.shared.generated.resources.detail_nik_invalid
import rusuh_app.shared.generated.resources.detail_nik_valid
import rusuh_app.shared.generated.resources.detail_niks_incomplete
import rusuh_app.shared.generated.resources.detail_proceed
import rusuh_app.shared.generated.resources.detail_section_categories
import rusuh_app.shared.generated.resources.detail_section_nik
import rusuh_app.shared.generated.resources.detail_title
import rusuh_app.shared.generated.resources.detail_total

/** Maximum tickets a single order may hold across all categories (FR-13). */
const val MAX_TICKETS_PER_ORDER = 3

/** Required NIK length: 16 digits as printed on a KTP (FR-13). */
const val NIK_LENGTH = 16

/**
 * Static placeholder content for the Detail Ticket page (FR-05 M1+M2).
 *
 * This is UI-only sample data: the event header plus its seat categories. There
 * is NO ViewModel, NO repository and NO API behind it — M3/M4 replace it with
 * live state (see the TODOs on [DetailScreen]).
 *
 * @param eventTitle main event name shown in the header (e.g. "Samudra").
 * @param eventSubtitle tagline under the title (e.g. "Tur Gelombang 2026"); null hides it.
 * @param eventMeta pre-formatted date · time · venue line, already localized.
 * @param categories the seat categories offered for this event.
 */
data class DetailContent(
    val eventTitle: String,
    val eventSubtitle: String?,
    val eventMeta: String,
    val categories: List<SeatCategoryUi>,
)

/**
 * UI-level render state for the Detail Ticket page.
 *
 * NOT the MVI state — a presentation-only discriminator so the stateless screen
 * can render each visual placeholder the design calls for (loading / error /
 * empty / content / expired). FR-05-M3 introduces the real DetailViewModel and
 * maps its MVI state onto these cases.
 */
sealed interface DetailUiState {
    /** Fetching the event + categories. */
    data object Loading : DetailUiState

    /** Loaded: an event with its seat categories to display. */
    data class Content(val detail: DetailContent) : DetailUiState

    /** No categories to sell (event has none / all withdrawn). */
    data object Empty : DetailUiState

    /**
     * Failed to load the event detail.
     *
     * @param message pre-formatted, user-facing error copy supplied by the caller.
     */
    data class Error(val message: String) : DetailUiState

    /**
     * The buyer's selection window elapsed (hi-fi "Waktu habis" / expired sheet).
     * Their picks were not saved and their place went to the next in line.
     */
    data object Expired : DetailUiState
}

/**
 * Detail Ticket screen — FR-05 M2.
 *
 * Assembles the event header, a list of [SeatCategoryCard]s, per-ticket NIK
 * inputs, a running total with the 3-ticket cap indicator, and the proceed
 * button, plus the loading / error / empty / expired visual variants.
 *
 * Pure presentation: it renders the [state] it is handed and keeps only
 * ephemeral local UI state (selected quantities, typed NIKs) via `remember` so
 * the placeholder is interactive without a ViewModel. There is NO business
 * logic, NO repository and NO API here.
 *
 * TODO(M3): wire DetailViewModel — collect the MVI state/effects and map them
 *  onto [DetailUiState], and hoist the selected-quantities / NIK state out of
 *  this composable into the ViewModel so it survives configuration changes.
 * TODO(M4): feed [DetailContent] from the event-detail API (FR-11/FR-12) and
 *  submit the real selection + NIKs (FR-13) from [onProceed].
 *
 * @param state what to render.
 * @param onBack optional back-navigation handler; shows a back button when non-null.
 * @param onProceed invoked when the (visually enabled) proceed button is tapped;
 *   wired to real navigation in M3/M4. No-op placeholder by default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailUiState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onProceed: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        // Text chevron rather than a material-icons dependency (matches WaitingRoomScreen).
                        TextButton(onClick = onBack) {
                            Text(text = stringResource(Res.string.action_back))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                DetailUiState.Loading -> LoadingState()
                DetailUiState.Empty -> EmptyState()
                DetailUiState.Expired -> ExpiredState(onBack = onBack)
                is DetailUiState.Error -> ErrorState(message = state.message)
                is DetailUiState.Content -> ContentState(detail = state.detail, onProceed = onProceed)
            }
        }
    }
}

@Composable
private fun ContentState(
    detail: DetailContent,
    onProceed: () -> Unit,
) {
    // Placeholder-only local UI state. Quantities keyed by category id, and one
    // NIK string per selected ticket slot. Hoisted into DetailViewModel in M3.
    val quantities = remember { mutableStateMapOf<String, Int>() }
    var nikInputs by remember { mutableStateOf(listOf<String>()) }

    val selectedTotal = quantities.values.sum()
    val capReached = isCapReached(selectedTotal)
    val subtotal = subtotalRupiah(detail.categories, quantities)

    // Keep the NIK slot count in step with the selected quantity (FR-13:
    // 1 NIK per ticket). Preserve already-typed values when the count grows.
    // shortcut: NIK slots are positional, so decrementing drops the LAST typed
    // NIK rather than the one tied to the removed ticket; upgrade when M3 hoists
    // selection+NIK into DetailViewModel keyed per ticket.
    fun resizeNiks(target: Int) {
        nikInputs =
            when {
                target == nikInputs.size -> nikInputs
                target < nikInputs.size -> nikInputs.take(target)
                else -> nikInputs + List(target - nikInputs.size) { "" }
            }
    }

    val allNiksValid = nikInputs.isNotEmpty() && nikInputs.all(::isNikValid)
    val canProceed = canProceed(selectedTotal, nikInputs)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(RusuhSpacing.xl),
    ) {
        EventHeader(detail)
        Spacer(Modifier.height(RusuhSpacing.xl))

        SectionTitle(stringResource(Res.string.detail_section_categories))
        Text(
            text = stringResource(Res.string.detail_categories_hint, MAX_TICKETS_PER_ORDER),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(RusuhSpacing.lg))

        detail.categories.forEach { category ->
            val qty = quantities[category.id] ?: 0
            SeatCategoryCard(
                data = category,
                quantity = qty,
                canIncrement = !capReached,
                onQuantityChange = { newQty ->
                    quantities[category.id] = newQty
                    resizeNiks(quantities.values.sum())
                },
            )
            Spacer(Modifier.height(RusuhSpacing.lg))
        }

        CapIndicator(selected = selectedTotal)
        Spacer(Modifier.height(RusuhSpacing.xl))

        if (nikInputs.isNotEmpty()) {
            SectionTitle(stringResource(Res.string.detail_section_nik))
            Text(
                text = stringResource(Res.string.detail_nik_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(RusuhSpacing.lg))
            nikInputs.forEachIndexed { index, value ->
                NikField(
                    ticketNumber = index + 1,
                    value = value,
                    onValueChange = { typed ->
                        // Visual validation only: keep digits, cap at 16 (FR-13).
                        val digits = typed.filter(Char::isDigit).take(NIK_LENGTH)
                        nikInputs = nikInputs.toMutableList().also { it[index] = digits }
                    },
                )
                Spacer(Modifier.height(RusuhSpacing.lg))
            }
            Spacer(Modifier.height(RusuhSpacing.md))
        }

        TotalRow(subtotal = subtotal)
        Spacer(Modifier.height(RusuhSpacing.lg))

        Button(
            onClick = onProceed,
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.detail_proceed))
        }
        if (selectedTotal > 0 && !allNiksValid) {
            Spacer(Modifier.height(RusuhSpacing.md))
            Text(
                text = stringResource(Res.string.detail_niks_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventHeader(detail: DetailContent) {
    val description =
        buildString {
            append(detail.eventTitle)
            detail.eventSubtitle?.let { append(", $it") }
            append(". ${detail.eventMeta}")
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Column {
            PosterSlot()
            Column(modifier = Modifier.padding(RusuhSpacing.xl)) {
                Text(
                    text = detail.eventTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                detail.eventSubtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(RusuhSpacing.md))
                Text(
                    text = detail.eventMeta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Poster image slot. A branded placeholder box (no image library pulled into
 * commonMain for one slot — matches EventCard's PosterSlot). M4 supplies the
 * real poster from the event-detail payload.
 */
@Composable
private fun PosterSlot() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 3-ticket cap indicator (hi-fi "3/3 … batas tercapai"). */
@Composable
private fun CapIndicator(selected: Int) {
    val atCap = isCapReached(selected)
    Text(
        text =
            if (atCap) {
                stringResource(Res.string.detail_cap_reached, selected, MAX_TICKETS_PER_ORDER)
            } else {
                stringResource(Res.string.detail_cap_selected, selected, MAX_TICKETS_PER_ORDER)
            },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (atCap) FontWeight.SemiBold else FontWeight.Normal,
        color = if (atCap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NikField(
    ticketNumber: Int,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val valid = isNikValid(value)
    val showError = value.isNotEmpty() && !valid
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.detail_nik_field_label, ticketNumber)) },
            placeholder = { Text(stringResource(Res.string.detail_nik_field_placeholder)) },
            singleLine = true,
            isError = showError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        val (msg, color) =
            when {
                valid -> stringResource(Res.string.detail_nik_valid) to MaterialTheme.colorScheme.primary
                showError ->
                    stringResource(Res.string.detail_nik_invalid, NIK_LENGTH) to
                        MaterialTheme.colorScheme.error
                else -> stringResource(Res.string.detail_nik_example) to MaterialTheme.colorScheme.onSurfaceVariant
            }
        Spacer(Modifier.height(RusuhSpacing.xs))
        Text(text = msg, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun TotalRow(subtotal: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.detail_total),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatRupiah(subtotal),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LoadingState() {
    val loadingDescription = stringResource(Res.string.detail_loading_description)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.semantics { contentDescription = loadingDescription },
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(RusuhSpacing.xl))
        Text(
            text = stringResource(Res.string.detail_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState() {
    CenteredMessage(
        title = stringResource(Res.string.detail_empty_title),
        body = stringResource(Res.string.detail_empty_body),
    )
}

@Composable
private fun ErrorState(message: String) {
    CenteredMessage(
        title = stringResource(Res.string.detail_error_title),
        body = message,
        titleColor = MaterialTheme.colorScheme.error,
    )
}

/** Expired / "Waktu habis" variant — mirrors the hi-fi expired sheet. */
@Composable
private fun ExpiredState(onBack: (() -> Unit)?) {
    val expiredDescription = stringResource(Res.string.detail_expired_description)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(RusuhSpacing.xl)
                .semantics { contentDescription = expiredDescription },
    ) {
        StatusDot(color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(RusuhSpacing.xl))
        Text(
            text = stringResource(Res.string.detail_expired_badge),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        Text(
            text = stringResource(Res.string.detail_expired_headline),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        Text(
            text = stringResource(Res.string.detail_expired_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onBack != null) {
            Spacer(Modifier.height(RusuhSpacing.xl))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.detail_expired_action))
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Spacer(
        modifier =
            Modifier
                .size(RusuhSpacing.queueDotCore)
                .background(color = color, shape = CircleShape),
    )
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier.fillMaxWidth().padding(RusuhSpacing.xl)
                .semantics { contentDescription = "$title. $body" },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Pure presentation helpers (unit-tested in commonTest, no Compose harness) ---

/** True once the order is at (or past) the 3-ticket cap (FR-13). */
internal fun isCapReached(selectedTotal: Int): Boolean = selectedTotal >= MAX_TICKETS_PER_ORDER

/**
 * Visual-only NIK validity: exactly [NIK_LENGTH] digits (FR-13). No checksum or
 * province-code check — that is an M3/M4 concern once a real validator exists.
 */
internal fun isNikValid(nik: String): Boolean = nik.length == NIK_LENGTH && nik.all(Char::isDigit)

/**
 * Whether the proceed button is visually enabled: at least one ticket selected,
 * within the cap, and every selected ticket has a valid 16-digit NIK (FR-13).
 */
internal fun canProceed(
    selectedTotal: Int,
    niks: List<String>,
): Boolean =
    selectedTotal in 1..MAX_TICKETS_PER_ORDER &&
        niks.size == selectedTotal &&
        niks.all(::isNikValid)

/** Running subtotal of selected categories in whole rupiah. Pure function. */
internal fun subtotalRupiah(
    categories: List<SeatCategoryUi>,
    quantities: Map<String, Int>,
): Int = categories.sumOf { it.priceRupiah * (quantities[it.id] ?: 0) }

private const val POSTER_ASPECT_RATIO = 16f / 9f
