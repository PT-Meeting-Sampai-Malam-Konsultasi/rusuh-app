@file:Suppress("MatchingDeclarationName", "LongParameterList")

package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.seat_category_almost_gone
import rusuh_app.shared.generated.resources.seat_category_available
import rusuh_app.shared.generated.resources.seat_category_selected
import rusuh_app.shared.generated.resources.seat_category_sold_out
import rusuh_app.shared.generated.resources.seat_decrement
import rusuh_app.shared.generated.resources.seat_increment

/**
 * Availability label the backend returns for a category (`Category.availability`,
 * a snapshot at load): "Tersedia" / "Hampir habis" / "Habis". There is no seat number
 * and no seat map — entry is by category.
 */
enum class SeatAvailability { Available, AlmostGone, SoldOut }

/**
 * Display data for one seat category on the Detail Ticket page (FR-11/FR-12).
 *
 * Every string is already localized by the caller; the price stays numeric so the screen can
 * sum the order and format it canonically via [formatRupiah].
 *
 * @param id stable identity for list keys and quantity callbacks (`categoryId`).
 * @param name category name (e.g. "Festival B").
 * @param description seating + position line (e.g. "Berdiri · di belakang Festival A"); null hides it.
 * @param priceRupiah price in whole rupiah for ONE ticket.
 * @param availability availability label; [SeatAvailability.SoldOut] disables the card.
 * @param entryGate entry gate (e.g. "Gerbang 5"); null hides it.
 */
@Immutable
data class SeatCategoryUi(
    val id: String,
    val name: String,
    val description: String?,
    val priceRupiah: Long,
    val availability: SeatAvailability = SeatAvailability.Available,
    val entryGate: String? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(priceRupiah >= 0) { "priceRupiah must be >= 0, was $priceRupiah" }
    }

    /** True when the category cannot be bought; the card renders disabled. */
    val isSoldOut: Boolean get() = availability == SeatAvailability.SoldOut
}

/**
 * Seat-category row for the Detail Ticket page (FR-05 M1), matching the Figma "Item":
 * name, seating line, availability label, right-aligned price and a boxed quantity stepper.
 *
 * Stateless: [quantity] is supplied and every change goes through [onQuantityChange]. The
 * caller owns the per-order cap (max 3, bounded by reserved units) and passes
 * [canIncrement] = false once it is reached. Sold-out categories show a "Habis" chip, a
 * struck-through price and a disabled stepper.
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
    val nameColor =
        when {
            soldOut -> RusuhColors.BrandSubtle
            quantity > 0 -> RusuhColors.BrandMagentaDeep
            else -> RusuhColors.BrandInk
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = RusuhSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xl),
        ) {
            Column(
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
            ) {
                Text(
                    text = data.name,
                    style = RusuhBrandTextStyles.itemTitle.display(),
                    color = nameColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                data.description?.let { desc ->
                    Text(
                        text = desc,
                        style = RusuhBrandTextStyles.note.body(),
                        color = RusuhColors.BrandMuted,
                    )
                }
                data.entryGate?.let { gate ->
                    Text(
                        text = gate,
                        style = RusuhBrandTextStyles.note.body(),
                        color = RusuhColors.BrandMuted,
                    )
                }
                AvailabilityLabelView(data.availability)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRupiah(data.priceRupiah),
                    style = RusuhBrandTextStyles.price.body(),
                    color = if (soldOut) RusuhColors.BrandSubtle else RusuhColors.BrandInk,
                    textDecoration = if (soldOut) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(RusuhSpacing.lg))
                QuantityStepper(
                    quantity = quantity,
                    // Decrement stays available when the category turned sold out, so the user can back out.
                    canDecrement = quantity > 0,
                    canIncrement = !soldOut && canIncrement,
                    decrementLabel = stringResource(Res.string.seat_decrement, data.name),
                    incrementLabel = stringResource(Res.string.seat_increment, data.name),
                    onDecrement = { onQuantityChange((quantity - 1).coerceAtLeast(0)) },
                    onIncrement = { onQuantityChange(quantity + 1) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderThin).background(RusuhColors.BrandHairline))
    }
}

@Composable
private fun AvailabilityLabelView(availability: SeatAvailability) {
    val text = stringResource(availabilityLabel(availability)).uppercase()
    val style = RusuhBrandTextStyles.eyebrow.body()
    when (availability) {
        SeatAvailability.SoldOut ->
            Text(
                text = text,
                style = style,
                color = RusuhColors.BrandInk,
                modifier =
                    Modifier
                        .background(RusuhColors.BrandChip)
                        .padding(horizontal = RusuhSpacing.sm, vertical = RusuhSpacing.xs),
            )
        SeatAvailability.AlmostGone -> Text(text = text, style = style, color = RusuhColors.BrandMagentaDeep)
        SeatAvailability.Available -> Text(text = text, style = style, color = RusuhColors.BrandMuted)
    }
}

/** Boxed "- n +" stepper (Figma 129x45). The centre cell fills magenta once quantity > 0. */
@Composable
private fun QuantityStepper(
    quantity: Int,
    canDecrement: Boolean,
    canIncrement: Boolean,
    decrementLabel: String,
    incrementLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .size(width = RusuhSpacing.stepperWidth, height = RusuhSpacing.stepperHeight)
                .brutalBox(fill = RusuhColors.BrandCream),
    ) {
        StepperButton("−", canDecrement, decrementLabel, onDecrement) // minus sign
        val selected = quantity > 0
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .width(RusuhSpacing.stepperCell)
                    .fillMaxHeight()
                    .drawBehind {
                        if (selected) drawRect(RusuhColors.BrandMagenta)
                        val rule = RusuhSpacing.borderHeavy.toPx()
                        drawRect(RusuhColors.BrandInk, Offset.Zero, Size(rule, size.height))
                        drawRect(RusuhColors.BrandInk, Offset(size.width - rule, 0f), Size(rule, size.height))
                    },
        ) {
            Text(
                text = quantity.toString(),
                style = RusuhBrandTextStyles.stepperValue.display(),
                color = if (selected) Color.White else RusuhColors.BrandInk,
                textAlign = TextAlign.Center,
            )
        }
        StepperButton("+", canIncrement, incrementLabel, onIncrement)
    }
}

@Composable
private fun RowScope.StepperButton(
    symbol: String,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics { contentDescription = label }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = symbol,
            style = RusuhBrandTextStyles.stepperSymbol.body(),
            color = if (enabled) RusuhColors.BrandInk else RusuhColors.BrandSubtle,
        )
    }
}

/** String resource for an availability label. Pure — unit-tested directly. */
internal fun availabilityLabel(availability: SeatAvailability): StringResource =
    when (availability) {
        SeatAvailability.Available -> Res.string.seat_category_available
        SeatAvailability.AlmostGone -> Res.string.seat_category_almost_gone
        SeatAvailability.SoldOut -> Res.string.seat_category_sold_out
    }

/**
 * Formats whole rupiah as "Rp 850.000" with Indonesian thousands dots.
 * Pure function — unit-tested directly. Reuses [formatThousands] so grouping
 * stays identical to the rest of the app.
 */
internal fun formatRupiah(amount: Long): String = "Rp ${amount.formatThousands()}"

/** Screen-reader description: name, seating, gate, price, availability and current selection. */
@Composable
internal fun buildSeatCategoryDescription(
    data: SeatCategoryUi,
    quantity: Int,
): String {
    val availability = stringResource(availabilityLabel(data.availability))
    val selected = if (data.isSoldOut) null else stringResource(Res.string.seat_category_selected, quantity)
    return listOfNotNull(
        data.name,
        data.description,
        data.entryGate,
        formatRupiah(data.priceRupiah),
        availability,
        selected,
    ).joinToString(". ", postfix = ".")
}
