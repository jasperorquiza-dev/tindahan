package com.example.sarisaristore.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import java.io.File

@Composable
fun LocalThumbnailImage(
    imagePath: String,
    contentDescription: String?,
    imageSizePx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val imageRequest = remember(imagePath, imageSizePx, context) {
        ImageRequest.Builder(context)
            .data(File(imagePath))
            .size(imageSizePx)
            .precision(Precision.INEXACT)
            .memoryCacheKey("thumb:$imagePath:$imageSizePx")
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
fun LocalPreviewImage(
    imagePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    previewSizePx: Int = 1600,
) {
    val context = LocalContext.current
    val imageRequest = remember(imagePath, previewSizePx, context) {
        ImageRequest.Builder(context)
            .data(File(imagePath))
            .size(previewSizePx)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
