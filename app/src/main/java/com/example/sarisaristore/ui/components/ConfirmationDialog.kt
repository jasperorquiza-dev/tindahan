package com.example.sarisaristore.ui.components

import androidx.compose.runtime.Composable

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = title,
        message = message,
        onDismissRequest = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        dismissLabel = dismissLabel,
        onDismiss = onDismiss,
        confirmIsDestructive = confirmLabel.equals("Delete", ignoreCase = true) ||
            confirmLabel.equals("Restore", ignoreCase = true),
    )
}
