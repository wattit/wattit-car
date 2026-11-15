package app.wattit.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wattit.car.domain.KoreanTime
import app.wattit.car.domain.PlanState
import app.wattit.car.domain.TariffState
import app.wattit.car.domain.TariffText
import java.time.ZonedDateTime

private fun text(size: Int, weight: FontWeight = FontWeight.Normal, color: androidx.compose.ui.graphics.Color = Palette.Ink) =
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
        Column(Modifier.fillMaxSize()) {
            TopBar(state)
            HorizontalLine()
            Row(Modifier.fillMaxSize()) {
                PlanPanel(state, onRetryPlan, Modifier.weight(0.48f).fillMaxHeight())
                VerticalLine()
                TariffPanel(
                    tariff = state.tariff,
                    savings = savingsText(state.plan),
                    onRetry = onRetryTariff,
                    modifier = Modifier.weight(0.52f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TopBar(state: ScreenState) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "충전 계획",
            style = text(22, FontWeight.SemiBold),
            modifier = Modifier.padding(start = 40.dp).weight(1f).semantics { heading() },
        )
        Chip("차량", vehicleText(state))
        Chip("환경", "자가 완속 7kW")
        Chip("요금", tariffText(state.tariff))
    }
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
private fun Chip(label: String, value: String) {
    Row(Modifier.fillMaxHeight()) {
        VerticalLine()
        Column(Modifier.padding(horizontal = 30.dp, vertical = 18.dp)) {
            Text(label, style = text(13, color = Palette.Faint))
            Text(value, style = text(20, FontWeight.SemiBold))
        }
    }
}

@Composable
private fun PlanPanel(state: ScreenState, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(start = 44.dp, end = 44.dp, top = 40.dp, bottom = 32.dp)) {
        Text("언제 꽂을지, 대신 정했습니다", style = text(14, FontWeight.Bold, Palette.Teal).copy(letterSpacing = 1.sp))
        Spacer(Modifier.height(12.dp))
        when (val plan = state.plan) {
            PlanState.Loading -> Notice("차량 정보와 요금을 확인하고 있어요.", null, null)
            PlanState.VehicleUnavailable -> Notice("차량 정보를 읽지 못했어요.", "배터리 잔량을 받지 못해 계획을 세우지 않았어요.", onRetry)
            PlanState.CapacityUnknown -> Notice("배터리 용량을 확인해야 해요.", "차량이 배터리 용량을 알려 주지 않아 계획을 세우지 않았어요.", onRetry)
            PlanState.ServerNotReady -> Notice("서버 준비 중이에요.", "충전 계획 기능이 아직 서버에 올라가지 않았어요. 잠시 후 다시 시도해 주세요.", onRetry)
            PlanState.Offline -> Notice("서버에 연결하지 못했어요.", "인터넷 연결을 확인한 뒤 다시 시도해 주세요.", onRetry)
            is PlanState.Refused -> Notice("계획을 세우지 못했어요.", plan.message, onRetry)
            is PlanState.Failed -> Notice(
                "계획을 받지 못했어요.",
                plan.code?.let { "서버 오류(코드 $it)가 났어요. 잠시 후 다시 시도해 주세요." } ?: "서버 응답을 읽지 못했어요.",
                onRetry,
            )
            is PlanState.Ready -> ReadyPlan(plan, state)
        }
    }
}

@Composable
private fun Notice(title: String, body: String?, onRetry: (() -> Unit)?) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(title, style = text(30, FontWeight.SemiBold))
        if (body != null) {
            Spacer(Modifier.height(12.dp))
            Text(body, style = text(17, color = Palette.Muted))
        }
        if (onRetry != null) {
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy, contentColor = Palette.Canvas),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text("다시 시도", style = text(18, FontWeight.SemiBold, Palette.Canvas))
            }
        }
    }
}

@Composable
private fun ReadyPlan(plan: PlanState.Ready, state: ScreenState) {
    val response = plan.response
    val reservation = response.reservation
    val today = state.today
    Column(Modifier.fillMaxHeight()) {
        when {
            !response.needsCharging -> {
                Text("지금은 충전하지 않아도 돼요.", style = text(30, FontWeight.SemiBold))
                Text("${plan.socNow}%", style = text(140, FontWeight.Bold, Palette.Navy).copy(lineHeight = 150.sp))
            }
            reservation != null && today != null -> {
                val day = KoreanTime.withTopic(KoreanTime.dayWord(ZonedDateTime.parse(reservation.startAt), today))
                val spoken = KoreanTime.spoken(reservation.startHhmm)
                Text(
                    buildAnnotatedString {
                        append("$day ")
                        withStyle(SpanStyle(color = Palette.Navy, fontWeight = FontWeight.Bold)) { append(spoken) }
                        append("에 꽂으세요.")
                    },
                    style = text(30, FontWeight.SemiBold),
                )
                Text(reservation.startHhmm, style = text(140, FontWeight.Bold, Palette.Navy).copy(lineHeight = 150.sp))
            }
            else -> Text(response.speech, style = text(22, FontWeight.Medium))
        }
        Text(contextLine(plan, state), style = text(15, color = Palette.Muted))
        Spacer(Modifier.weight(1f))
        windowText(plan)?.let { window ->
            HorizontalLine()
            ValueRow("충전 구간", window, Palette.Teal)
        }
        HorizontalLine()
        ValueRow("목표 충전", "${plan.socTarget}%", Palette.Ink)
        Spacer(Modifier.height(18.dp))
        if (response.needsCharging && reservation != null) {
            Text("이 구간에 맞춰 차 예약충전을 설정하세요.", style = text(17, color = Palette.Muted))
        }
    }
}

private fun contextLine(plan: PlanState.Ready, state: ScreenState): String {
    val depart = state.departAt
    val today = state.today
    val departText = if (depart != null && today != null) {
        "${KoreanTime.dayWord(depart, today)} ${KoreanTime.spoken(depart.hour, depart.minute)} 출발 기준"
    } else {
        "출발 시각 기준"
    }
    // When no charging is needed the large number is already the state of charge.
    return if (plan.response.needsCharging) "$departText · 지금 잔량 ${plan.socNow}%" else departText
}

/** Reservation window as sent ("23:00–06:00"); falls back to the plan's own start and end. */
private fun windowText(plan: PlanState.Ready): String? {
    val response = plan.response
    response.reservation?.let { return "${it.startHhmm}–${it.endHhmm}" }
    val p = response.plan ?: return null
    return "${p.start.substring(11, 16)}–${p.end.substring(11, 16)}"
}

private fun savingsText(plan: PlanState): String? {
    if (plan !is PlanState.Ready) return null
    return plan.response.savingsWon?.let { TariffText.wonAmount(it) }
}

@Composable
private fun ValueRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = text(17, color = Palette.Muted))
        Text(value, style = text(34, FontWeight.SemiBold, color))
    }
}

@Composable
fun HorizontalLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
}

@Composable
fun VerticalLine() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(Palette.Line))
}
