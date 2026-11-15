package app.wattit.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wattit.car.data.HourPrice
import app.wattit.car.data.Reservation
import app.wattit.car.domain.KST
import app.wattit.car.domain.TariffState
import app.wattit.car.domain.TariffText
import java.time.ZonedDateTime

private val HourWidth = 46.dp

/**
 * The line under the chart: savings for a plan, the cheapest hours when no charging is needed.
 * [suffix] is drawn smaller after the value (the cheapest hours' price).
 */
data class TariffFooter(val label: String, val value: String, val color: Color, val size: Int, val suffix: String? = null)

@Composable
fun TariffPanel(
    tariff: TariffState,
    reservation: Reservation?,
    footer: TariffFooter,
    now: ZonedDateTime?,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(start = 44.dp, end = 44.dp, top = 44.dp, bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("시간대별 요금", style = text(22, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("₩/kWh · 좌우로 넘겨 보기", style = text(15, color = Palette.Faint))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tariff) {
                TariffState.Loading -> CenterNote("요금표를 불러오는 중이에요.", null)
                TariffState.Failed -> CenterNote("요금표를 불러오지 못했어요.", onRetry)
                is TariffState.Ready -> Chart(tariff, reservation, now)
            }
        }
        HorizontalLine()
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(footer.label, style = text(19, color = Palette.Muted), modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(16.dp))
            Text(
                buildAnnotatedString {
                    append(footer.value)
                    footer.suffix?.let {
                        withStyle(SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Palette.Muted)) { append(" · $it") }
                    }
                },
                style = text(footer.size, FontWeight.Bold, footer.color),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CenterNote(message: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = text(19, color = Palette.Muted))
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy, contentColor = Palette.Canvas),
                modifier = Modifier.heightIn(min = 64.dp),
            ) { Text("다시 불러오기", style = text(20, FontWeight.SemiBold, Palette.Canvas)) }
        }
    }
}

@Composable
private fun Chart(tariff: TariffState.Ready, reservation: Reservation?, now: ZonedDateTime?) {
    val hours: List<HourPrice> = tariff.days.flatMap { it.hours.sortedBy { h -> h.hour } }
    val lowest = TariffText.lowest(hours) ?: return
    val highest = TariffText.highest(hours) ?: return
    val lightLegend = TariffText.lightLegend(tariff.days)
    val discountLegends = TariffText.discountLegends(tariff.days)
    val span = reservation?.let { TariffText.reservationSpan(tariff.days.first().day, it.startAt, it.endAt, hours.size) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val chartWidth = HourWidth * hours.size

    // Open on the reservation (with six hours before it, as in the mock), otherwise on the current hour.
    LaunchedEffect(tariff, span) {
        val firstHour = span?.let { it.start - 6f } ?: ((now ?: ZonedDateTime.now(KST)).withZoneSameInstant(KST).hour - 1f)
        // Half a cell earlier, so the label on the first boundary is not cut at the left edge.
        scroll.scrollTo(with(density) { (HourWidth * (firstHour - 0.5f).coerceAtLeast(0f)).roundToPx() })
    }

    val description = buildString {
        append("시간대별 요금 그래프, 오늘과 내일 48시간. ")
        append("최저 ${TariffText.won(lowest)}, 최고 ${TariffText.won(highest)}.")
        lightLegend?.let { append(" $it.") }
        discountLegends.forEach { append(" $it.") }
        reservation?.let { append(" 예약 ${it.startHhmm}부터 ${it.endHhmm}까지.") }
    }

    Column(Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = description }) {
        Spacer(Modifier.height(18.dp))
        Text(TariffText.won(highest), style = text(15, color = Palette.Faint), modifier = Modifier.align(Alignment.End))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                Canvas(Modifier.width(chartWidth).fillMaxHeight()) {
                    val step = size.width / hours.size
                    val top = 12.dp.toPx()
                    val bottom = size.height
                    if (span != null) {
                        drawRect(
                            color = Palette.Navy.copy(alpha = 0.06f),
                            topLeft = Offset(step * span.start, 0f),
                            size = Size(step * (span.endInclusive - span.start), bottom),
                        )
                    }
                    val range = (highest - lowest).takeIf { it > 0.0 }
                    fun y(price: Double): Float =
                        if (range == null) {
                            bottom * 0.6f
                        } else {
                            (bottom - (bottom - top) * ((price - lowest) / range) * 0.85f - (bottom - top) * 0.06f).toFloat()
                        }
                    val points = hours.mapIndexed { i, h -> Offset(step * i + step / 2, y(h.pricePerKwh)) }
                    val line = Path().apply {
                        moveTo(0f, points.first().y)
                        lineTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val a = points[i - 1]
                            val b = points[i]
                            val mid = (a.x + b.x) / 2
                            cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                        }
                        lineTo(size.width, points.last().y)
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(size.width, bottom)
                        lineTo(0f, bottom)
                        close()
                    }
                    drawPath(fill, Palette.TealFill.copy(alpha = 0.85f))
                    drawPath(line, Palette.Teal, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Text(
                TariffText.won(lowest),
                style = text(15, color = Palette.Faint),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Palette.Canvas)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Axis(hours, span, chartWidth, scroll)
        ScrollIndicator(scroll.value, scroll.maxValue)
        Legend(lightLegend, discountLegends, reservation)
    }
}

/** Band bar, reservation bar and hour labels; scrolls with the chart (same ScrollState). */
@Composable
private fun Axis(hours: List<HourPrice>, span: ClosedFloatingPointRange<Float>?, chartWidth: androidx.compose.ui.unit.Dp, scroll: ScrollState) {
    Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        // One canvas for both bars: a Row of 46 dp boxes rounds each box to whole pixels and drifts
        // from the chart (about 10 px by 22:00 at 200 DPI), so cells are placed by the same x as the chart.
        Canvas(Modifier.width(chartWidth).padding(top = 12.dp).height(24.dp)) {
            val step = size.width / hours.size
            val band = 10.dp.toPx()
            hours.forEachIndexed { i, h ->
                drawRect(
                    color = if (h.band == "light" || h.discounted) Palette.Teal else Palette.BandOther,
                    topLeft = Offset(step * i, 0f),
                    size = Size(step + 0.5f, band),
                )
            }
            val barTop = band + 8.dp.toPx()
            val barHeight = 6.dp.toPx()
            drawRect(Palette.Track, topLeft = Offset(0f, barTop), size = Size(size.width, barHeight))
            if (span != null) {
                drawRect(
                    Palette.Navy,
                    topLeft = Offset(step * span.start, barTop),
                    size = Size(step * (span.endInclusive - span.start), barHeight),
                )
            }
        }
        // Labels sit on hour boundaries (hour h starts at x = h cells), where the band, reservation bar
        // and shading start too. The first label is left-aligned so it is not cut at the chart edge.
        Box(Modifier.width(chartWidth).padding(top = 10.dp)) {
            hours.forEachIndexed { i, h ->
                val label = when {
                    i == 24 -> "내일"
                    h.hour % 2 == 0 -> "%02d".format(h.hour)
                    else -> null
                }
                if (label != null) {
                    Text(
                        label,
                        style = text(16, FontWeight.SemiBold, Palette.Muted)
                            .copy(textAlign = if (i == 0) TextAlign.Start else TextAlign.Center),
                        modifier = Modifier
                            .offset(x = if (i == 0) 0.dp else HourWidth * i - HourWidth / 2)
                            .width(HourWidth),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(light: String?, discounts: List<String>, reservation: Reservation?) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        light?.let { LegendItem(it, Palette.TealText) { Box(Modifier.size(16.dp).background(Palette.Teal)) } }
        discounts.forEach { LegendItem(it, Palette.TealText) { Box(Modifier.size(16.dp).background(Palette.Teal)) } }
        reservation?.let {
            LegendItem("예약 ${it.startHhmm}–${it.endHhmm}", Palette.Navy) {
                Box(Modifier.width(22.dp).height(6.dp).background(Palette.Navy))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(12.dp))
        Text(label, style = text(19, FontWeight.Medium, color), maxLines = 1)
    }
}

@Composable
private fun ScrollIndicator(value: Int, max: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).background(Palette.Track)) {
        if (max <= 0) return@BoxWithConstraints
        val thumb = maxWidth * 0.4f
        val offset = (maxWidth - thumb) * (value.toFloat() / max)
        Box(Modifier.offset(x = offset).width(thumb).height(6.dp).background(Palette.BandOther))
    }
}
