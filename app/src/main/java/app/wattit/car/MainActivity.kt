package app.wattit.car

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.wattit.car.car.PleosCarSource
import app.wattit.car.car.PleosSpeaker
import app.wattit.car.data.WattitApi
import app.wattit.car.ui.ChargePlanScreen
import app.wattit.car.ui.PlanController

class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val car = PleosCarSource(application)
    private val speaker = PleosSpeaker(application)
    private val api = WattitApi(BuildConfig.API_BASE_URL)

    val controller = PlanController(
        scope = viewModelScope,
        car = car,
        planCall = api::plan,
        timelineCall = api::timeline,
        speaker = speaker,
    ).also { it.start() }

    override fun onCleared() {
        speaker.release()
        car.release()
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: PlanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.controller.state.collectAsStateWithLifecycle()
            ChargePlanScreen(
                state = state,
                onRetryPlan = viewModel.controller::refreshPlan,
                onRetryTariff = viewModel.controller::refreshTariff,
            )
        }
    }
}
