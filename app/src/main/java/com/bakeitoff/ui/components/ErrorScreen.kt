package com.bakeitoff.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakeitoff.R

@Composable
fun ErrorScreen(message: String, onDismiss: () -> Unit) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.erro_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error // Usa a cor de erro padrão do Material 3
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, textAlign = TextAlign.Center)
    }
}
