package com.pantrypulse.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the adaptive launcher icon inside a circular mask, as most launchers show it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class IconRenderTest {
    @Test fun renderLauncherIcon() {
        val context = ApplicationProvider.getApplicationContext<TestApp>()
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertNotNull(icon)
        val size = 432
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
        icon!!.setBounds(0, 0, size, size)
        icon.draw(canvas)
        File("build/screenshots").mkdirs()
        File("build/screenshots/launcher_icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
