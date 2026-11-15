package app.wattit.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wattit.car.data.HourPrice
import app.wattit.car.data.Reservation
import app.wattit.car.domain.KST
import app.wattit.car.domain.TariffState
import app.wattit.car.domain.TariffText
import java.time.ZonedDateTime

private val HourWidth = 46.dp

/** The line under the chart: savings for a plan, the cheapest window when no charging is needed. */
data class TariffFooter(val label: String, val value: String, val color: Color, val size: Int)

@Composable
fun TariffPanel(
    tariff: TariffState,
    reservation: Reservation?,
    footer: TariffFooter,
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
                is TariffState.Ready -> Chart(tariff, reservation)
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
            Text(footer.value, style = text(footer.size, FontWeight.Bold, footer.color))
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
private fun Chart(tariff: TariffState.Ready, reservation: Reservation?) {
    val hours: List<HourPrice> = tariff.days.flatMap { it.hours.sortedBy { h -> h.hour } }
    val lowest = TariffText.lowest(hours) ?: return
    val highest = TariffText.highest(hours) ?: return
    val window = TariffText.lightWindow(tariff.days.first())
    val span = reservation?.let { TariffText.reservationSpan(tariff.days.first().day, it.startAt, it.endAt, hours.size) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val chartWidth = HourWidth * hours.size

    // Open on the reservation (with six hours before it, as in the mock), otherwise on the current hour.
    LaunchedEffect(tariff, span) {
        val firstHour = span?.let { it.start - 6f } ?: (ZonedDateTime.now(KST).hour - 1f)
        scroll.scrollTo(with(density) { (HourWidth * firstHour.coerceAtLeast(0f)).roundToPx() })
    }

    val description = buildString {
        append("시간대별 요금 그래프, 오늘과 내일 48시간. ")
        append("최저 ${TariffText.won(lowest)}, 최고 ${TariffText.won(highest)}.")
        window?.let { append(" 경부하 $it.") }
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
        Legend(window, lowest, reservation)
    }
}

/** Band bar, reservation bar and hour labels; scrolls with the chart (same ScrollState). */
@Composable
private fun Axis(hours: List<HourPrice>, span: ClosedFloatingPointRange<Float>?, chartWidth: androidx.compose.ui.unit.Dp, scroll: ScrollState) {
    Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Row(Modifier.width(chartWidth).padding(top = 12.dp)) {
            hours.forEach { h ->
                Box(
                    Modifier
                        .width(HourWidth)
                        .height(10.dp)
                        .background(if (h.band == "light") Palette.Teal else Palette.BandOther),
                )
            }
        }
        BoxWithConstraints(Modifier.width(chartWidth).padding(top = 8.dp).height(6.dp).background(Palette.Track)) {
            if (span != null) {
                val start = HourWidth * span.start
                Box(
                    Modifier
                        .offset(x = start)
                        .width(HourWidth * (span.endInclusive - span.start))
                        .fillMaxHeight()
                        .background(Palette.Navy),
                )
            }
        }
        Row(Modifier.width(chartWidth).padding(top = 10.dp)) {
            hours.forEachIndexed { i, h ->
                val label = when {
                    i == 24 -> "내일"
                    h.hour % 2 == 0 -> "%02d".format(h.hour)
                    else -> ""
                }
                // Centered under the hour, where the line's point for that hour is drawn.
                Text(
                    label,
                    style = text(16, FontWeight.SemiBold, Palette.Muted).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.width(HourWidth),
                )
            }
        }
    }
}

@Composable
private fun Legend(window: String?, lowest: Double, reservation: Reservation?) {
    val light = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).background(Palette.Teal))
            Spacer(Modifier.width(12.dp))
            val windowText = window?.let { "경부하 $it · " } ?: ""
            Text("${windowText}최저 ${TariffText.won(lowest)}/kWh", style = text(19, FontWeight.Medium, Palette.TealText), maxLines = 1)
        }
    }
    val reserved = @Composable { r: Reservation ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(22.dp).height(6.dp).background(Palette.Navy))
            Spacer(Modifier.width(12.dp))
            Text("예약 ${r.startHhmm}–${r.endHhmm}", style = text(19, FontWeight.Medium, Palette.Navy), maxLines = 1)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp)) {
        // Both items need about 580 dp on one line; on a narrow (portrait) panel they stack.
        if (maxWidth >= 580.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                light()
                reservation?.let { reserved(it) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                light()
                reservation?.let { reserved(it) }
            }
        }
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
