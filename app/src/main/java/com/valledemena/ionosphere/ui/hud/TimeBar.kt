package com.valledemena.ionosphere.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valledemena.ionosphere.core.REPLAY_MAX_MINUTES
import com.valledemena.ionosphere.core.ReplaySettings
import com.valledemena.ionosphere.core.TimeMode
import com.valledemena.ionosphere.core.replaySliderOffset
import com.valledemena.ionosphere.core.timeBarClockLabel
import com.valledemena.ionosphere.core.timeBarEffectiveMinutes
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.5, 1.0, 2.0, 4.0, 8.0)
private const val SLIDER_STEP_MINUTES = 5.0

private val CYAN = Color(0xFF67E8F9)
private val GREEN = Color(0xFF6EE7B7)
private val AMBER = Color(0xFFFCD34D)
private val SLATE = Color(0xFF94A3B8)
private val SLATE_DIM = Color(0xFF64748B)

/**
 * TimeBar R4.2: espejo de `hud/TimeBar.tsx`.
 *
 * No posee estado de replay propio: recibe [settings] y emite el estado
 * siguiente. El reloj se actualiza cada segundo aquí porque es solo
 * presentación; el avance de la reproducción vive en `GlobeScreen` con
 * [com.valledemena.ionosphere.core.tick].
 *
 * Los cambios de modo y el reset **no** tocan `replayPlaying`: el patch de la
 * web tampoco lo hace. Solo la restauración de sesión lo resetea.
 */
@Composable
fun TimeBar(
    settings: ReplaySettings,
    onChange: (ReplaySettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val replay = settings.timeMode == TimeMode.REPLAY
    val minutes = settings.replayMinutes.coerceIn(0.0, REPLAY_MAX_MINUTES)
    val sliderPosition = replaySliderOffset(REPLAY_MAX_MINUTES, minutes).toFloat()
    val clock = timeBarClockLabel(
        nowMs = nowMs,
        effMin = timeBarEffectiveMinutes(settings.timeMode, minutes),
    )
    val live = settings.copy(
        timeMode = TimeMode.LIVE,
        replayMinutes = 0.0,
    )
    val toggle = settings.copy(
        timeMode = if (replay) TimeMode.LIVE else TimeMode.REPLAY,
        replayMinutes = 0.0,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xEE0B1220), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = { onChange(toggle) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.background(
                    color = if (replay) AMBER.copy(alpha = 0.16f) else GREEN.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                ),
            ) {
                Text(
                    text = if (replay) "REPLAY" else "LIVE",
                    color = if (replay) AMBER else GREEN,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = clock,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Slider(
                value = sliderPosition,
                onValueChange = { position ->
                    val back = replaySliderOffset(REPLAY_MAX_MINUTES, position.toDouble())
                    val snapped = (back / SLIDER_STEP_MINUTES).roundToInt() *
                        SLIDER_STEP_MINUTES
                    onChange(
                        settings.copy(
                            timeMode = if (snapped > 0.0) TimeMode.REPLAY else TimeMode.LIVE,
                            replayMinutes = snapped,
                        ),
                    )
                },
                valueRange = 0f..REPLAY_MAX_MINUTES.toFloat(),
                enabled = replay,
                modifier = Modifier.weight(1f),
            )
            if (minutes > 0.0) {
                TextButton(
                    onClick = { onChange(live) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("AHORA", color = CYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("−168 h", color = SLATE_DIM, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            if (replay) {
                Control(
                    label = if (settings.replayPlaying) "pausa" else "play",
                    active = settings.replayPlaying,
                    color = if (settings.replayPlaying) AMBER else SLATE,
                ) {
                    onChange(settings.copy(replayPlaying = !settings.replayPlaying))
                }
                Control(speedLabel(settings.replaySpeed), false, CYAN) {
                    val index = SPEEDS.indexOf(settings.replaySpeed)
                    onChange(settings.copy(replaySpeed = SPEEDS[(index + 1).mod(SPEEDS.size)]))
                }
                Control("LOOP", settings.replayLoop, if (settings.replayLoop) CYAN else SLATE_DIM) {
                    onChange(settings.copy(replayLoop = !settings.replayLoop))
                }
            } else {
                Text("ahora", color = SLATE, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun Control(
    label: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        modifier = if (active) {
            Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
        } else {
            Modifier
        },
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun speedLabel(speed: Double): String =
    if (speed % 1.0 == 0.0) "${speed.toInt()}x" else "${speed}x"
