package com.bakeitoff.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakeitoff.R

@Composable
fun HeaderSection(onLogoLongPress: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Shrunk the circle from 120.dp to 80.dp
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "🧁",
                    fontSize = 56.sp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onLogoLongPress() })
                    }
                )
            }
        }

        // Shrunk the spacing from 24.dp to 12.dp
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.o_que_vamos_preparar),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.descricao_inicial),
            style = MaterialTheme.typography.bodyMedium, // Changed from bodyLarge to bodyMedium to look more delicate
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
