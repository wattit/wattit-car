package app.wattit.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.wattit.car.data.Reservation
import app.wattit.car.domain.KoreanTime
import app.wattit.car.domain.PlanState
import app.wattit.car.domain.TariffState
import app.wattit.car.domain.TariffText
import java.time.ZonedDateTime

/**
 * Side by side from this width up on a landscape display (the 14.6-inch landscape form factor is
 * 1360 dp wide). Narrower or portrait displays (12.9-inch portrait is 628 dp wide) stack the plan
 * above the chart and scroll.
 */
val WideLayoutMinWidth: Dp = 1000.dp

private val PanelPadding = 44.dp
private val StackedChartHeight = 560.dp

internal fun text(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Palette.Ink) =
    TextStyle(fontFamily = Pretendard, fontSize = size.sp, fontWeight = weight, color = color, lineHeight = (size * 1.35).sp)

@Composable
fun ChargePlanScreen(state: ScreenState, onRetryPlan: () -> Unit, onRetryTariff: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Frame)
            .padding(14.dp)
            .background(Palette.Canvas),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WideLayoutMinWidth && maxWidth > maxHeight
            val planWidth = if (wide) maxWidth * 0.48f else maxWidth
            // The large time fits its panel: "22:00" needs about 3.4 dp of width per sp of type.
            val bigSize = ((planWidth - PanelPadding * 2).value / 3.4f).coerceIn(80f, 150f).sp
            // The longest headline ("배터리가 85%라 충전하지 않아도 돼요.") is about 16.5 em wide; keep it on one line.
            val headlineSize = ((planWidth - PanelPadding * 2).value / 16.5f).coerceIn(24f, 34f).sp
            val sizes = PlanTextSizes(headline = headlineSize, big = bigSize)
            val footer = tariffFooter(state)
            val reservation = reservationOf(state.plan)
            Column(Modifier.fillMaxSize()) {
                TopBar(state, compact = !wide)
                HorizontalLine()
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        PlanPanel(state, onRetryPlan, sizes, stacked = false, Modifier.weight(0.48f).fillMaxHeight())
                        VerticalLine()
                        TariffPanel(state.tariff, reservation, footer, state.now, onRetryTariff, Modifier.weight(0.52f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PlanPanel(state, onRetryPlan, sizes, stacked = true, Modifier.fillMaxWidth())
                        HorizontalLine()
                        TariffPanel(state.tariff, reservation, footer, state.now, onRetryTariff, Modifier.fillMaxWidth().height(StackedChartHeight))
                    }
                }
            }
        }
    }
}

/** Type sizes that follow the plan panel's width. */
private data class PlanTextSizes(val headline: TextUnit, val big: TextUnit)

@Composable
private fun TopBar(state: ScreenState, compact: Boolean) {
    val chips = listOf(
        "배터리" to batteryText(state),
        "차량" to vehicleText(state),
        "환경" to "자가 완속 7kW",
        "요금" to tariffText(state.tariff),
    )
    if (compact) {
        Column(Modifier.fillMaxWidth()) {
            Title(Modifier.padding(start = 28.dp, top = 20.dp, bottom = 16.dp))
            HorizontalLine()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                chips.forEachIndexed { i, (label, value) ->
                    if (i > 0) VerticalLine()
                    Chip(label, value, compact = true, modifier = Modifier.weight(1f))
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Title(Modifier.padding(start = PanelPadding).weight(1f))
            chips.forEach { (label, value) ->
                VerticalLine()
                Chip(label, value, compact = false)
            }
        }
    }
}

@Composable
private fun Title(modifier: Modifier) {
    Text("충전 계획", style = text(24, FontWeight.Bold), modifier = modifier.semantics { heading() })
}

private fun batteryText(state: ScreenState): String {
    val car = state.car ?: return "확인 중"
    return car.socPercent?.let { "$it%" } ?: "읽지 못함"
}

private fun vehicleText(state: ScreenState): String {
    val car = state.car ?: return "확인 중"
    if (car.capacityWh == null) return "용량 확인 필요"
    return car.model ?: "배터리 ${TariffText.kwh(car.capacityWh)}"
}

private fun tariffText(tariff: TariffState): String = when (tariff) {
    is TariffState.Ready -> "${TariffText.seasonName(tariff.days.first().season)} TOU"
    TariffState.Loading -> "확인 중"
    TariffState.Failed -> "확인 못 함"
}

@Composable
private fun Chip(label: String, value: String, compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = if (compact) 16.dp else 30.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = text(14, FontWeight.Medium, Palette.Faint))
        Text(
            value,
            style = text(if (compact) 18 else 21, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlanPanel(state: ScreenState, onRetry: () -> Unit, sizes: PlanTextSizes, stacked: Boolean, modifier: Modifier) {
    Column(modifier.padding(start = PanelPadding, end = PanelPadding, top = 44.dp, bottom = 32.dp)) {
        when (val plan = state.plan) {
            PlanState.Loading -> Notice("차량 정보와 요금을 확인하고 있어요.", null, null)
            PlanState.VehicleUnavailable -> Notice("차량 정보를 읽지 못했어요.", "배터리 잔량을 받지 못해 계획을 세우지 않았어요.", onRetry)
            PlanState.CapacityUnknown -> Notice("배터리 용량을 확인해야 해요.", "차량이 배터리 용량을 알려 주지 않아 계획을 세우지 않았어요.", onRetry)
            PlanState.ServerNotReady -> Notice("서버 준비 중이에요.", "충전 계획 기능이 아직 서버에 올라가지 않았어요.\n잠시 후 다시 시도해 주세요.", onRetry)
            PlanState.Offline -> Notice("서버에 연결하지 못했어요.", "인터넷 연결을 확인한 뒤 다시 시도해 주세요.", onRetry)
            is PlanState.Refused -> Notice("계획을 세우지 못했어요.", plan.message, onRetry)
            is PlanState.Failed -> Notice(
                "계획을 받지 못했어요.",
                plan.code?.let { "서버 오류(코드 $it)가 났어요.\n잠시 후 다시 시도해 주세요." } ?: "서버 응답을 읽지 못했어요.",
                onRetry,
            )
            is PlanState.NotNeeded -> NotNeededPlan(plan, state, sizes, stacked)
            is PlanState.Ready -> ReadyPlan(plan, state, sizes, stacked)
        }
    }
}

@Composable
private fun Eyebrow(value: String) {
    Text(value, style = text(16, FontWeight.SemiBold, Palette.TealText).copy(letterSpacing = 1.6.sp))
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun Notice(title: String, body: String?, onRetry: (() -> Unit)?) {
    Eyebrow("언제 꽂을지, 대신 정했습니다")
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(title, style = text(32, FontWeight.SemiBold))
        if (body != null) {
            Spacer(Modifier.height(12.dp))
            // Android wraps Korean between any two syllables, so each sentence gets its own line.
            Text(body, style = text(19, color = Palette.Muted))
        }
        if (onRetry != null) {
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy, contentColor = Palette.Canvas),
                modifier = Modifier.heightIn(min = 64.dp),
            ) {
                Text("다시 시도", style = text(20, FontWeight.SemiBold, Palette.Canvas))
            }
        }
    }
}

@Composable
private fun Headline(before: String, emphasis: String, after: String, size: TextUnit) {
    Text(
        buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(color = Palette.Navy, fontWeight = FontWeight.Bold)) { append(emphasis) }
            append(after)
        },
        style = text(34, FontWeight.SemiBold).copy(fontSize = size, lineHeight = size * 1.3f),
    )
}

@Composable
private fun BigValue(value: String, size: TextUnit) {
    Text(
        value,
        style = TextStyle(
            fontFamily = Pretendard,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            color = Palette.Navy,
            letterSpacing = (-0.04).em,
            lineHeight = size * 1.1f,
        ),
        maxLines = 1,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun ReadyPlan(plan: PlanState.Ready, state: ScreenState, sizes: PlanTextSizes, stacked: Boolean) {
    val response = plan.response
    val reservation = response.reservation
    val today = state.today
    Column(if (stacked) Modifier else Modifier.fillMaxHeight()) {
        Eyebrow("언제 꽂을지, 대신 정했습니다")
        if (reservation != null && today != null) {
            val day = KoreanTime.withTopic(KoreanTime.dayWord(ZonedDateTime.parse(reservation.startAt), today))
            Headline("$day ", KoreanTime.spoken(reservation.startHhmm), "에 꽂으세요.", sizes.headline)
            BigValue(reservation.startHhmm, sizes.big)
        } else {
            // No single reservation (too short, or 24 h or longer): the server's sentence says what to do.
            Text(response.speech, style = text(24, FontWeight.Medium))
        }
        Text(departureLine(state), style = text(19, color = Palette.Muted), modifier = Modifier.padding(top = 10.dp))
        PushDown(stacked)
        if (reservation != null) {
            ValueRow("예약 충전", "${reservation.startHhmm}–${reservation.endHhmm}", Palette.TealText)
        }
        ValueRow("목표 충전", "${plan.socTarget}%", Palette.Ink)
        if (reservation != null) Foot("이 시간으로 차 예약충전을 설정하세요.")
    }
}

@Composable
private fun NotNeededPlan(plan: PlanState.NotNeeded, state: ScreenState, sizes: PlanTextSizes, stacked: Boolean) {
    Column(if (stacked) Modifier else Modifier.fillMaxHeight()) {
        Eyebrow("오늘 밤은 쉬어도 됩니다")
        Headline("배터리가 ${plan.socNow}%라 ", "충전하지 않아도", " 돼요.", sizes.headline)
        BigValue("${plan.socNow}%", sizes.big)
        Text("목표 ${plan.socTarget}% 이상", style = text(19, color = Palette.Muted), modifier = Modifier.padding(top = 10.dp))
        PushDown(stacked)
        ValueRow("목표 충전", "${plan.socTarget}%", Palette.Ink)
        val depart = state.departAt
        val today = state.today
        if (depart != null && today != null) {
            ValueRow(
                "${KoreanTime.dayWord(depart, today)} 출발",
                "${KoreanTime.spoken(depart.hour, depart.minute)} (가정)",
                Palette.Ink,
                valueSize = 24,
            )
        }
    }
}

private fun departureLine(state: ScreenState): String {
    val depart = state.departAt
    val today = state.today
    return if (depart != null && today != null) {
        "${KoreanTime.dayWord(depart, today)} ${KoreanTime.spoken(depart.hour, depart.minute)} 출발 기준"
    } else {
        "출발 시각 기준"
    }
}

private fun reservationOf(plan: PlanState): Reservation? = (plan as? PlanState.Ready)?.response?.reservation

private fun tariffFooter(state: ScreenState): TariffFooter = when (val plan = state.plan) {
    is PlanState.NotNeeded -> {
        // Chosen from the server's hourly prices, so "가장 싼" holds on discount days too.
        val now = state.now
        val run = (state.tariff as? TariffState.Ready)?.let { t -> now?.let { TariffText.cheapestAhead(t.days, it) } }
        TariffFooter("가장 싼 시간", run?.label ?: "-", Palette.TealText, 30, run?.let { "${TariffText.won(it.price)}/kWh" })
    }
    is PlanState.Ready -> TariffFooter(
        "바로 충전 대비 절감",
        plan.response.savingsWon?.let { TariffText.wonAmount(it) } ?: "-",
        Palette.Navy,
        34,
    )
    else -> TariffFooter("바로 충전 대비 절감", "-", Palette.Navy, 34)
}

@Composable
private fun ValueRow(label: String, value: String, color: Color, valueSize: Int = 34) {
    HorizontalLine()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = text(19, color = Palette.Muted))
        Text(value, style = text(valueSize, FontWeight.SemiBold, color))
    }
}

/** Pushes the rows to the bottom of a side-by-side panel; a fixed gap when stacked (no height to fill). */
@Composable
private fun ColumnScope.PushDown(stacked: Boolean) {
    if (stacked) Spacer(Modifier.height(36.dp)) else Spacer(Modifier.weight(1f))
}

@Composable
private fun Foot(value: String) {
    HorizontalLine()
    Text(value, style = text(19, color = Palette.Muted), modifier = Modifier.padding(top = 18.dp))
}

@Composable
fun HorizontalLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
}

@Composable
fun VerticalLine() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(Palette.Line))
}
