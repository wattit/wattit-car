package app.wattit.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wattit.car.data.HourPrice
import app.wattit.car.domain.KST
import app.wattit.car.domain.TariffState
import app.wattit.car.domain.TariffText
import java.time.ZonedDateTime

private val HourWidth = 46.dp

private fun style(size: Int, weight: FontWeight = FontWeight.Normal, color: androidx.compose.ui.graphics.Color = Palette.Ink) =
    TextStyle(fontFamily = Pretendard, fontSize = size.sp, fontWeight = weight, color = color)

@Composable
fun TariffPanel(tariff: TariffState, savings: String?, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(start = 44.dp, end = 44.dp, top = 40.dp, bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("시간대별 요금", style = style(19, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("₩/kWh · 좌우로 넘겨 보기", style = style(14, color = Palette.Faint))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tariff) {
                TariffState.Loading -> CenterNote("요금표를 불러오는 중이에요.", null)
                TariffState.Failed -> CenterNote("요금표를 불러오지 못했어요.", onRetry)
                is TariffState.Ready -> Chart(tariff)
            }
        }
        HorizontalLine()
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("바로 충전 대비 절감", style = style(17, color = Palette.Muted))
            Text(savings ?: "-", style = style(32, FontWeight.Bold, Palette.Navy))
        }
    }
}

@Composable
private fun CenterNote(message: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = style(17, color = Palette.Muted))
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy, contentColor = Palette.Canvas),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("다시 불러오기", style = style(17, FontWeight.SemiBold, Palette.Canvas)) }
        }
    }
}

@Composable
private fun Chart(tariff: TariffState.Ready) {
    val hours: List<HourPrice> = tariff.days.flatMap { it.hours.sortedBy { h -> h.hour } }
    val lowest = TariffText.lowest(hours) ?: return
    val highest = TariffText.highest(hours) ?: return
    val today = tariff.days.first()
    val window = TariffText.lightWindow(today)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val nowHour = ZonedDateTime.now(KST).hour

    LaunchedEffect(tariff) {
        val px = with(density) { (HourWidth * (nowHour - 1).coerceAtLeast(0)).roundToPx() }
        scroll.scrollTo(px)
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        Text(TariffText.won(highest), style = style(13, color = Palette.Faint), modifier = Modifier.align(Alignment.End))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val chartWidth = HourWidth * hours.size
            Column(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(scroll)
                    .semantics {
                        contentDescription = "시간대별 요금 그래프. 오늘과 내일 48시간, " +
                            "최저 ${TariffText.won(lowest)}, 최고 ${TariffText.won(highest)}"
                    },
            ) {
                Canvas(Modifier.width(chartWidth).weight(1f)) {
                    val step = size.width / hours.size
                    val top = 8.dp.toPx()
                    val bottom = size.height
                    val span = (highest - lowest).takeIf { it > 0.0 }
                    fun y(price: Double): Float =
                        if (span == null) bottom * 0.6f else (bottom - (bottom - top) * ((price - lowest) / span) * 0.85f - (bottom - top) * 0.05f).toFloat()
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
                    drawPath(fill, Palette.TealFill)
                    drawPath(line, Palette.Teal, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                }
                Row(Modifier.width(chartWidth).padding(top = 10.dp)) {
                    hours.forEach { h ->
                        Box(
                            Modifier
                                .width(HourWidth)
                                .height(8.dp)
                                .background(if (h.band == "light") Palette.Teal else Palette.BandOther),
                        )
                    }
                }
                Row(Modifier.width(chartWidth).padding(top = 8.dp)) {
                    hours.forEachIndexed { i, h ->
                        val label = when {
                            i == 24 -> "내일"
                            h.hour % 2 == 0 -> "%02d".format(h.hour)
                            else -> ""
                        }
                        // Centered under the hour, where the line's point for that hour is drawn.
                        Text(
                            label,
                            style = style(13, FontWeight.Medium, Palette.Muted).copy(textAlign = TextAlign.Center),
                            modifier = Modifier.width(HourWidth),
                        )
                    }
                }
            }
            Text(
                TariffText.won(lowest),
                style = style(13, color = Palette.Faint),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = (-48).dp)
                    .background(Palette.Canvas)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        ScrollIndicator(scroll.value, scroll.maxValue)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
            Box(Modifier.size(14.dp).background(Palette.Teal))
            Spacer(Modifier.width(12.dp))
            val windowText = window?.let { "경부하 $it · " } ?: ""
            Text("${windowText}최저 ${TariffText.won(lowest)}/kWh", style = style(17, FontWeight.Medium, Palette.Teal))
        }
    }
}

@Composable
private fun ScrollIndicator(value: Int, max: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).background(Palette.Track)) {
        if (max <= 0) return@BoxWithConstraints
        val thumb = maxWidth * 0.4f
        val offset = (maxWidth - thumb) * (value.toFloat() / max)
        Box(Modifier.offset(x = offset).width(thumb).height(6.dp).background(Palette.BandOther))
    }
}
