package com.muzrc.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muzrc.R
import com.muzrc.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

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

@Composable
fun AppPackageIcon(
    packageName: String,
    appName: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToImageBitmap(drawable)
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.cd_app_icon, appName),
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.take(1).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun VolumeBarIndicator(
    volumePercent: Int,
    modifier: Modifier = Modifier
) {
    val clamped = volumePercent.coerceIn(0, 100)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kelantangan Media Semasa",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$clamped%",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = if (clamped == 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { clamped / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = if (clamped == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

fun formatTime24h(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

fun formatDaysSummary(days: List<Int>): String {
    val clean = days.distinct().sorted()
    if (clean.size == 7) return "Setiap Hari"
    if (clean == listOf(1, 2, 3, 4, 5)) return "Hari Bekerja (Isn–Jum)"
    if (clean == listOf(6, 7)) return "Hujung Minggu (Sab–Aha)"
    val labels = mapOf(
        1 to "Isn",
        2 to "Sel",
        3 to "Rab",
        4 to "Kha",
        5 to "Jum",
        6 to "Sab",
        7 to "Aha"
    )
    return clean.mapNotNull { labels[it] }.joinToString(", ")
}
