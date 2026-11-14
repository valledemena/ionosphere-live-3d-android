package com.valledemena.ionosphere.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Popup de estacion R3.4 (pieza C). Presenta el modelo puro; no consulta red. */
@Composable
fun StationPopup(
    content: StationPopupModel.Content,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(300.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE0B1220)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = content.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (content.stale) {
                    Text(
                        text = "STALE",
                        color = Color(0xFFFDE047),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0x33FDE047), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("x", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            }
            Text(text = content.code, color = Color(0xFF94A3B8), fontSize = 11.sp)
            Spacer(Modifier.width(1.dp))
            content.lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = line.label,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = line.value,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
