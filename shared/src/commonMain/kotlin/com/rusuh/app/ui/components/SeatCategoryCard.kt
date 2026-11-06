@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing

/**
 * Display data for one seat category on the Detail Ticket page (FR-11/FR-12).
 *
 * UI-facing view model: every string is already localized/formatted by the
 * caller so the component stays clock- and locale-agnostic. "Seat category"
 * here is capacity-only — there are NO seat numbers and NO seat map (FR-11);
 * [available] is simply how many tickets remain in this category.
 *
 * @param id stable identity for list keys and quantity callbacks.
 * @param name category name (e.g. "Festival B").
 * @param description short supporting line (e.g. "Berdiri · di belakang Festival A"); null hides it.
 * @param priceRupiah raw price in whole rupiah for ONE ticket (e.g. 850_000).
 *   Kept numeric (not a pre-formatted string) so the component renders the
 *   canonical "Rp 850.000" form via [formatRupiah] and the running total on the
 *   screen can sum categories without re-parsing.
 * @param available tickets left in this category; 0 means sold out (disabled).
 */
data class SeatCategoryUi(
    val id: String,
    val name: String,
    val description: String?,
    val priceRupiah: Int,
    val available: Int,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(priceRupiah >= 0) { "priceRupiah must be >= 0, was $priceRupiah" }
        require(available >= 0) { "available must be >= 0, was $available" }
    }

    /** True when nothing is left in this category — the card renders disabled. */
    val isSoldOut: Boolean get() = available == 0
}

/**
 * Seat-category card for the Detail Ticket page (FR-05 M1).
 *
 * Stateless and token-driven: it shows the category name, its own formatted IDR
 * price, the available (capacity-only) count, and a quantity stepper. It holds
 * NO business logic — the current [quantity] is supplied by the caller and
 * every change is reported through [onQuantityChange]. The caller owns the
 * 3-ticket-total cap (FR-13) and passes [canIncrement] = false once the cap is
 * reached, which disables the `+` button here without this component knowing
 * the rule.
 *
 * A sold-out category ([SeatCategoryUi.isSoldOut]) is dimmed with both stepper
 * buttons disabled, mirroring the hi-fi "Habis" state.
 *
 * NO ViewModel, NO repository, NO API — the caller supplies [data] and [quantity].
 *
 * @param quantity how many tickets are currently selected in this category (>= 0).
 * @param canIncrement whether `+` is allowed right now (false when the order
 *   cap is hit); ignored when sold out (always disabled then).
 * @param onQuantityChange invoked with the new quantity after a +/- tap.
 */
@Composable
fun SeatCategoryCard(
    data: SeatCategoryUi,
    quantity: Int,
    modifier: Modifier = Modifier,
    canIncrement: Boolean = true,
    onQuantityChange: (Int) -> Unit = {},
) {
    val soldOut = data.isSoldOut
    val description = buildSeatCategoryDescription(data, quantity)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (soldOut) RusuhSpacing.DISABLED_ALPHA else 1f)
                .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(RusuhSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                data.description?.let { desc ->
                    Spacer(Modifier.height(RusuhSpacing.xxs))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(RusuhSpacing.sm))
                Text(
                    text = formatRupiah(data.priceRupiah),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(RusuhSpacing.xxs))
                Text(
                    text = availabilityLabel(data),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (soldOut) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
            Spacer(Modifier.width(RusuhSpacing.xl))
            // +/- quantity stepper. Purely presentational: the caller decides
            // whether each button is enabled and what the resulting quantity is.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
            ) {
                StepperButton(
                    // \u2212 is the minus sign (visually balanced against "+").
                    symbol = "\u2212",
                    enabled = !soldOut && quantity > 0,
                    label = "Kurangi ${data.name}",
                    onClick = { onQuantityChange((quantity - 1).coerceAtLeast(0)) },
                )
                Text(
                    text = quantity.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(RusuhSpacing.stepperValue),
                    textAlign = TextAlign.Center,
                )
                StepperButton(
                    symbol = "+",
                    enabled = !soldOut && canIncrement,
                    label = "Tambah ${data.name}",
                    onClick = { onQuantityChange(quantity + 1) },
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    // A bordered box rather than a material IconButton so the control reads as
    // the boxed +/- cells in the hi-fi, and no material-icons dependency is
    // pulled in for two glyphs (matches EventCard's text-chevron choice).
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(RusuhSpacing.stepperButton)
                .border(
                    width = RusuhSpacing.stepperBorder,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RusuhShapes.small,
                )
                .alpha(if (enabled) 1f else RusuhSpacing.DISABLED_ALPHA)
                .semantics { contentDescription = label }
                .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Availability line for a category. Pure function — unit-tested directly.
 * Capacity-only (FR-11): a remaining count or the sold-out label, never a seat
 * number.
 */
internal fun availabilityLabel(data: SeatCategoryUi): String =
    if (data.isSoldOut) "Habis" else "Sisa ${data.available.formatThousands()} tiket"

/**
 * Formats whole rupiah as "Rp 850.000" with Indonesian thousands dots.
 * Pure function — unit-tested directly. Reuses [formatThousands] so grouping
 * stays identical to the rest of the app.
 */
internal fun formatRupiah(amount: Int): String = "Rp ${amount.formatThousands()}"

/** Full accessibility description combining name, price, availability and current quantity. */
internal fun buildSeatCategoryDescription(
    data: SeatCategoryUi,
    quantity: Int,
): String =
    buildString {
        append("${data.name}. ")
        data.description?.let { append("$it. ") }
        append("${formatRupiah(data.priceRupiah)}. ")
        append("${availabilityLabel(data)}.")
        if (!data.isSoldOut) append(" $quantity dipilih.")
    }
