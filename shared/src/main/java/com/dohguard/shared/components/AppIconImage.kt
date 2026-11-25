package com.dohguard.shared.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.theme.DoHSageDark
import com.dohguard.shared.theme.DoHSageLight
import com.dohguard.shared.theme.DoHSagePastel
import com.dohguard.shared.theme.DoHTextSecondary

/**
 * Exibe o ícone real do aplicativo instalado via PackageManager quando disponível
 * no dispositivo local, com fallback elegante para o ícone de categoria Sage Green
 * quando o pacote não está instalado localmente (ex: visualização remota no ParentApp).
 */
@Composable
fun AppIconImage(
    packageName: String,
    appName: String,
    category: AppCategory,
    isBlocked: Boolean = false,
    size: Dp = 46.dp,
    cornerRadius: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appBitmap: ImageBitmap? = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToImageBitmap(drawable)
        } catch (_: Exception) {
            null
        }
    }

    val containerColor: Color = if (isBlocked) {
        DoHSagePastel.copy(alpha = 0.3f)
    } else {
        DoHSageLight
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (appBitmap != null) {
            Image(
                bitmap = appBitmap,
                contentDescription = appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
            )
        } else {
            Icon(
                imageVector = when (category) {
                    AppCategory.VIDEOS -> Icons.Default.SmartDisplay
                    AppCategory.GAMES -> Icons.Default.SportsEsports
                    AppCategory.EDUCATION -> Icons.Default.AutoStories
                    AppCategory.COMMUNICATION -> Icons.AutoMirrored.Filled.Chat
                    AppCategory.NAVIGATION -> Icons.Default.Explore
                    else -> Icons.Default.Apps
                },
                contentDescription = appName,
                tint = if (isBlocked) DoHTextSecondary else DoHSageDark,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
