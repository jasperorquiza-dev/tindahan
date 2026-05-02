package com.example.sarisaristore.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ImagePreviewCard(
    title: String,
    imagePath: String?,
    primaryButtonLabel: String,
    modifier: Modifier = Modifier,
    previewEnabled: Boolean = false,
    secondaryButtonLabel: String? = null,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null,
) {
    var showPreview by remember(imagePath) { mutableStateOf(false) }

    if (showPreview && !imagePath.isNullOrBlank()) {
        AppDialogSurface(onDismissRequest = { showPreview = false }) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            LocalPreviewImage(
                imagePath = imagePath,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            )
            Button(
                onClick = { showPreview = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Close")
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (imagePath.isNullOrBlank()) {
                Text(
                    text = "No image captured yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LocalThumbnailImage(
                    imagePath = imagePath,
                    contentDescription = title,
                    imageSizePx = 640,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (previewEnabled) {
                                Modifier.clickable { showPreview = true }
                            } else {
                                Modifier
                            }
                        ),
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useVerticalActions = maxWidth < 340.dp

                if (useVerticalActions) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPrimaryClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = primaryButtonLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!secondaryButtonLabel.isNullOrBlank() && onSecondaryClick != null) {
                            TextButton(
                                onClick = onSecondaryClick,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = secondaryButtonLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onPrimaryClick,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = primaryButtonLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!secondaryButtonLabel.isNullOrBlank() && onSecondaryClick != null) {
                            TextButton(
                                onClick = onSecondaryClick,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = secondaryButtonLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
