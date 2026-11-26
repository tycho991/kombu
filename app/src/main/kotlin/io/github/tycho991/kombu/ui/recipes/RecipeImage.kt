package io.github.tycho991.kombu.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.github.tycho991.kombu.R
import java.io.File

/**
 * A stored recipe image, or a neutral placeholder when there is none or it cannot be decoded (design D2).
 *
 * Images are local files that a sync replaces in place, so the memory cache key includes [version], the file's
 * modification time; Coil's disk cache is off, because the files are already on the device.
 */
@Composable
fun RecipeImage(file: File?, version: Long, modifier: Modifier = Modifier) {
    if (file == null) {
        Placeholder(modifier)
        return
    }
    val request = ImageRequest.Builder(LocalContext.current)
        .data(file)
        .memoryCacheKey("${file.path}#$version")
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { Placeholder(Modifier.fillMaxSize()) },
        error = { Placeholder(Modifier.fillMaxSize()) },
    )
}

@Composable
private fun Placeholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_restaurant),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
