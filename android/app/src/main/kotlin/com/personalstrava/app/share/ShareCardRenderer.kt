package com.personalstrava.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Captures the on-screen [ShareCard] composable (via its [GraphicsLayer],
 * recorded by the caller's `Modifier.drawWithContent` — see
 * ActivitySummaryScreen) to a PNG and hands it to the system share sheet.
 * A content:// URI through FileProvider, not a file:// one — see the
 * manifest provider's own comment for why.
 */
object ShareCardRenderer {
    suspend fun shareImage(context: Context, graphicsLayer: GraphicsLayer) {
        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "ride_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(sendIntent, "Share ride").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
