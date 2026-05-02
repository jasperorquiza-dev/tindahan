package com.example.sarisaristore.ui.feature.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.example.sarisaristore.ui.navigation.AppDestinations

@Composable
fun StartupRoute(
    isReady: Boolean,
    passcodeEnabled: Boolean,
    onNavigate: (String) -> Unit,
) {
    LaunchedEffect(isReady, passcodeEnabled) {
        if (isReady) {
            onNavigate(if (passcodeEnabled) AppDestinations.UNLOCK else AppDestinations.HOME)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Loading your store...",
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
