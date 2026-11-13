@file:Suppress("TooManyFunctions")

package com.rusuh.app.feature.eticket

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusuh.app.ui.components.QrCode
import com.rusuh.app.ui.components.QrEncodingResult
import com.rusuh.app.ui.components.brutalBox
import com.rusuh.app.ui.components.encodeQr
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.eticket_add_to_calendar
import rusuh_app.shared.generated.resources.eticket_add_to_calendar_description
import rusuh_app.shared.generated.resources.eticket_at_door_title
import rusuh_app.shared.generated.resources.eticket_doors_label
import rusuh_app.shared.generated.resources.eticket_doors_value
import rusuh_app.shared.generated.resources.eticket_entry_label
import rusuh_app.shared.generated.resources.eticket_headline
import rusuh_app.shared.generated.resources.eticket_holder_label
import rusuh_app.shared.generated.resources.eticket_holder_nik
import rusuh_app.shared.generated.resources.eticket_intro
import rusuh_app.shared.generated.resources.eticket_months_short
import rusuh_app.shared.generated.resources.eticket_order_line
import rusuh_app.shared.generated.resources.eticket_qr_description
import rusuh_app.shared.generated.resources.eticket_qr_error
import rusuh_app.shared.generated.resources.eticket_seating_seated
import rusuh_app.shared.generated.resources.eticket_seating_standing
import rusuh_app.shared.generated.resources.eticket_stamp_paid
import rusuh_app.shared.generated.resources.eticket_stamp_paid_description
import rusuh_app.shared.generated.resources.eticket_step_1
import rusuh_app.shared.generated.resources.eticket_step_2
import rusuh_app.shared.generated.resources.eticket_step_2_bold
import rusuh_app.shared.generated.resources.eticket_step_3
import rusuh_app.shared.generated.resources.eticket_step_number
import rusuh_app.shared.generated.resources.eticket_ticket_label
import rusuh_app.shared.generated.resources.eticket_ticket_summary
import rusuh_app.shared.generated.resources.eticket_ticket_summary_no_entry
import rusuh_app.shared.generated.resources.eticket_time_format
import rusuh_app.shared.generated.resources.eticket_weekdays_short

// ---- Intro: paid stamp, headline, body, calendar action, order line ----

@Composable
internal fun ETicketIntro(
    ui: ETicketUi,
    onAddToCalendar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xl)) {
        PaidStamp(ui.paidAt)
        Text(
            text = stringResource(Res.string.eticket_headline, ui.venueShort),
            style =
                HEADLINE_STYLE.display().copy(
                    shadow = Shadow(RusuhColors.Blue, Offset(HEADLINE_SHADOW, HEADLINE_SHADOW)),
                ),
            color = RusuhBrandColors.Pink,
            modifier = Modifier.semantics { heading() },
        )
        val email = ui.contactEmail
        val intro =
            pluralStringResource(Res.plurals.eticket_intro, ui.tickets.size, ui.tickets.size, ui.eventName, email)
        Text(
            text = boldText(intro, email),
            style = RusuhBrandTextStyles.bodyLarge.body(),
            color = RusuhBrandColors.Ink,
        )
        CalendarButton(onAddToCalendar)
        val orderLine = stringResource(Res.string.eticket_order_line, ui.orderId, ui.orderTotal, ui.paymentMethod)
        Text(
            text = boldText(orderLine, ui.orderId),
            style = RusuhBrandTextStyles.note.body(),
            color = RusuhBrandColors.Ink2,
        )
        Rule()
    }
}

/** Blue outlined, slightly tilted "✓ LUNAS · 12.07 WIB" stamp. */
@Composable
private fun PaidStamp(paidAt: EventMoment) {
    val time = paidAt.timeLabel()
    val description = stringResource(Res.string.eticket_stamp_paid_description, time, paidAt.zoneLabel)
    Text(
        text = stringResource(Res.string.eticket_stamp_paid, time, paidAt.zoneLabel),
        style = RusuhBrandTextStyles.eyebrow.body().copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = RusuhColors.BlueInk,
        modifier =
            Modifier
                .rotate(STAMP_TILT)
                .border(RusuhSpacing.borderHeavy + 0.5.dp, RusuhColors.BlueInk)
                .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.md)
                .semantics { contentDescription = description },
    )
}

/** Outlined action with accessibility label for the calendar button. */
@Composable
private fun CalendarButton(onClick: () -> Unit) {
    val description = stringResource(Res.string.eticket_add_to_calendar_description)
    Box(modifier = Modifier.fillMaxWidth().padding(end = RusuhSpacing.shadowSm, bottom = RusuhSpacing.shadowSm)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RusuhSpacing.ctaHeight)
                    .brutalBox(
                        fill = RusuhBrandColors.Card,
                        borderWidth = RusuhSpacing.borderHeavy,
                        shadow = RusuhBrandColors.Ink,
                        shadowOffset = RusuhSpacing.shadowSm,
                    )
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.md)
                    .semantics { contentDescription = description },
        ) {
            Text(
                text = stringResource(Res.string.eticket_add_to_calendar),
                style = RusuhBrandTextStyles.subtitle.display(),
                color = RusuhBrandColors.Ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---- Ticket card ----

@Composable
internal fun ETicketCard(card: ETicketCardUi) {
    val summary = card.spokenSummary()
    Box(modifier = Modifier.fillMaxWidth().padding(end = RusuhSpacing.sm, bottom = RusuhSpacing.sm)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .brutalBox(
                        fill = RusuhBrandColors.Card,
                        borderWidth = RusuhSpacing.borderHeavy,
                        shadow = RusuhBrandColors.Ink,
                        shadowOffset = RusuhSpacing.sm,
                    ),
        ) {
            // One spoken summary for the whole header + facts block; the QR is its own node below.
            Column(modifier = Modifier.clearAndSetSemantics { contentDescription = summary }) {
                CardHeader(card)
                CardFacts(card)
            }
            DashedDivider()
            QrSection(card)
        }
    }
}

@Composable
private fun ETicketCardUi.spokenSummary(): String {
    val doors = doorsOpen.doorsLabel()
    val entry = entryText(entryGate, seating.label(), ", ")
    return if (entry.isEmpty()) {
        stringResource(Res.string.eticket_ticket_summary_no_entry, position, total, category, nikMasked, doors)
    } else {
        stringResource(Res.string.eticket_ticket_summary, position, total, category, nikMasked, entry, doors)
    }
}

@Composable
private fun CardHeader(card: ETicketCardUi) {
    val color = if (card.tone == TicketTone.Blue) RusuhColors.Blue else RusuhColors.Pink
    val glow = if (card.tone == TicketTone.Blue) RusuhColors.Pink else RusuhColors.Blue
    Column(
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xs),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color)
                .drawBehind {
                    // Soft accent blob behind the event name, as in the design.
                    drawOval(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, glow.copy(alpha = GLOW_ALPHA))),
                        topLeft = Offset(size.width * GLOW_LEFT, size.height * GLOW_TOP),
                        size = Size(size.width * GLOW_WIDTH, size.height * GLOW_HEIGHT),
                    )
                }
                .padding(RusuhSpacing.xl),
    ) {
        Text(
            text = stringResource(Res.string.eticket_ticket_label, card.position, card.total),
            style = RusuhBrandTextStyles.eyebrow.body(),
            color = RusuhBrandColors.Card,
        )
        Text(
            text = card.eventName,
            style =
                RusuhBrandTextStyles.sheetHeadline.display().copy(
                    fontSize = EVENT_NAME_SIZE,
                    lineHeight = EVENT_NAME_SIZE,
                ),
            color = RusuhBrandColors.Card,
        )
        Text(
            text = card.category,
            style = RusuhBrandTextStyles.label.display().copy(fontWeight = FontWeight.Bold),
            color = if (card.tone == TicketTone.Blue) RusuhColors.PinkInk else RusuhColors.BlueInk,
            modifier =
                Modifier
                    .padding(top = RusuhSpacing.xs)
                    .rotate(TAG_TILT)
                    .background(RusuhBrandColors.Card)
                    .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.xs),
        )
    }
    Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhBrandColors.Ink))
}

@Composable
private fun CardFacts(card: ETicketCardUi) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(RusuhSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        Fact(
            label = stringResource(Res.string.eticket_holder_label),
            value = stringResource(Res.string.eticket_holder_nik, card.nikMasked),
        )
        val entry = entryText(card.entryGate, card.seating.label(), ENTRY_SEPARATOR)
        Row(horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xl)) {
            if (entry.isNotEmpty()) {
                Fact(
                    label = stringResource(Res.string.eticket_entry_label),
                    value = entry,
                    modifier = Modifier.weight(1f),
                )
            }
            Fact(
                label = stringResource(Res.string.eticket_doors_label),
                value = card.doorsOpen.doorsLabel(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = RusuhBrandTextStyles.eyebrow.body(), color = RusuhBrandColors.Ink2)
        Text(
            text = value,
            style = RusuhBrandTextStyles.itemTitle.display().copy(fontSize = FACT_SIZE, lineHeight = FACT_LINE),
            color = RusuhBrandColors.Ink,
        )
    }
}

@Composable
private fun QrSection(card: ETicketCardUi) {
    val description = stringResource(Res.string.eticket_qr_description, card.ticketCode)
    val errorMessage = stringResource(Res.string.eticket_qr_error)
    val spokenDescription = if (encodeQr(card.qrPayload) is QrEncodingResult.Failed) errorMessage else description
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(RusuhSpacing.xl)
                .clearAndSetSemantics { contentDescription = spokenDescription },
    ) {
        QrCode(payload = card.qrPayload, contentDescription = description, errorMessage = errorMessage, size = QR_SIZE)
        Text(
            text = card.ticketCode,
            style = RusuhBrandTextStyles.label.body().copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = RusuhBrandColors.Ink,
        )
    }
}

@Composable
private fun DashedDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(RusuhSpacing.borderMedium)
            .drawBehind {
                val stroke = RusuhSpacing.borderMedium.toPx()
                drawLine(
                    color = RusuhColors.Ink,
                    start = Offset(0f, stroke / 2),
                    end = Offset(size.width, stroke / 2),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH * stroke, DASH_GAP * stroke)),
                )
            },
    )
}

// ---- Footer: "Di pintu masuk" ----

@Composable
internal fun AtTheDoor() {
    Column(verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg)) {
        Rule()
        Text(
            text = stringResource(Res.string.eticket_at_door_title),
            style = RusuhBrandTextStyles.sectionTitle.display(),
            color = RusuhBrandColors.Ink,
            modifier = Modifier.padding(top = RusuhSpacing.md).semantics { heading() },
        )
        Step(1, AnnotatedString(stringResource(Res.string.eticket_step_1)))
        val bold = stringResource(Res.string.eticket_step_2_bold)
        Step(2, boldText(stringResource(Res.string.eticket_step_2, bold), bold))
        Step(3, AnnotatedString(stringResource(Res.string.eticket_step_3)))
    }
}

@Composable
private fun Step(
    number: Int,
    text: AnnotatedString,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = stringResource(Res.string.eticket_step_number, number),
            style = RusuhBrandTextStyles.bodyLarge.body(),
            color = RusuhBrandColors.Ink,
            modifier = Modifier.widthIn(min = STEP_NUMBER_WIDTH),
        )
        Text(
            text = text,
            style = RusuhBrandTextStyles.bodyLarge.body(),
            color = RusuhBrandColors.Ink,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhBrandColors.Ink))
}

// ---- Formatting helpers ----

/** [text] with the first occurrence of [bold] emphasised; plain when absent. */
internal fun boldText(
    text: String,
    bold: String,
): AnnotatedString {
    val range = boldRange(text, bold) ?: return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        append(text.substring(range.last + 1))
    }
}

@Composable
private fun EventMoment.timeLabel(): String = stringResource(Res.string.eticket_time_format, hourText(), minuteText())

/** "Sab, 12 Des · 16.00 WIB". */
@Composable
private fun EventMoment.doorsLabel(): String {
    val weekdays = stringArrayResource(Res.array.eticket_weekdays_short)
    val months = stringArrayResource(Res.array.eticket_months_short)
    return stringResource(
        Res.string.eticket_doors_value,
        weekdays.getOrElse(weekdayIndex) { "" },
        day,
        months.getOrElse(monthIndex) { "" },
        timeLabel(),
        zoneLabel,
    )
}

@Composable
private fun Seating.label(): String? =
    when (this) {
        Seating.Standing -> stringResource(Res.string.eticket_seating_standing)
        Seating.Seated -> stringResource(Res.string.eticket_seating_seated)
        Seating.Unknown -> null
    }

private val HEADLINE_STYLE =
    TextStyle(fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
private const val HEADLINE_SHADOW = 4f
private const val STAMP_TILT = -2f
private const val TAG_TILT = -2f
private const val GLOW_ALPHA = 0.85f
private const val GLOW_LEFT = 0.3f
private const val GLOW_TOP = 0.15f
private const val GLOW_WIDTH = 0.5f
private const val GLOW_HEIGHT = 0.55f
private const val DASH = 3f
private const val DASH_GAP = 2.5f
private const val ENTRY_SEPARATOR = " · "
private val EVENT_NAME_SIZE = 34.sp
private val FACT_SIZE = 18.sp
private val FACT_LINE = 22.sp
private val QR_SIZE = 184.dp
private val STEP_NUMBER_WIDTH = 24.dp
