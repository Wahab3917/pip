package one.mindly.pip

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import one.mindly.pip.core.PIP_LOG_TAG
import one.mindly.pip.quick.PipWidget
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

/**
 * Renders the real widget (the layout inflated through RemoteViews, as the launcher does) at several
 * sizes, states and themes, saves PNGs, and checks the text inside the round button stays clear of its edge.
 * Pictures go to the app's files folder, widget-render (pull them with adb and run-as).
 * Sizes are in dp. A 2 x 2 widget on Samsung's launcher is a tall rectangle, about 180 x 260 on the
 * user's phone; sizes from 170 x 245 up are asserted. Smaller or squarer boxes (150 x 215, or a launcher
 * with square cells giving ~180 x 180) make the circle under ~105 dp and crowd the label: those are
 * rendered and logged but not asserted (known limitation: the widget is designed for the tall shape).
 */
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = context.resources.displayMetrics.density
    private val outDir = File(context.filesDir, "widget-render").apply { mkdirs() }

    private val tallSizes = listOf(170 to 245, 180 to 260, 190 to 275)
    private val squareSizes = listOf(150 to 215, 180 to 180)

    @Test
    fun textStaysInsideTheRoundButton() {
        val problems = mutableListOf<String>()
        for ((w, h) in tallSizes + squareSizes) for (dark in listOf(false, true)) for (catching in listOf(false, true)) {
            val asserted = (w to h) in tallSizes
            val root = render(w, h, catching, dark)
            save(root, "${if (dark) "dark" else "light"}-${if (catching) "catching" else "idle"}-${w}x$h")
            val group = root.findViewById<View>(if (catching) R.id.widget_live else R.id.widget_idle) as ViewGroup
            val circle = group.getChildAt(0) as ImageView
            val content = group.getChildAt(1) as LinearLayout
            // The circle is the largest centred circle that fits its box (fitCenter).
            val diameter = minOf(circle.width, circle.height)
            val cx = circle.left + circle.width / 2f
            val cy = circle.top + circle.height / 2f
            val radius = diameter / 2f
            for (i in 0 until content.childCount) {
                val child = content.getChildAt(i)
                val r = Rect(content.left + child.left, content.top + child.top, content.left + child.right, content.top + child.bottom)
                // For text use the actual text lines, not the padded view box.
                val inner = if (child is TextView) {
                    val layout = child.layout
                    val textWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
                    val cxText = r.centerX().toFloat()
                    Rect((cxText - textWidth / 2).toInt(), r.top, (cxText + textWidth / 2).toInt(), r.bottom)
                } else r
                val farthest = listOf(
                    hypot(inner.left - cx, inner.top - cy), hypot(inner.right - cx, inner.top - cy),
                    hypot(inner.left - cx, inner.bottom - cy), hypot(inner.right - cx, inner.bottom - cy),
                ).max()
                val margin = (radius - farthest) / density
                Log.i(PIP_LOG_TAG, "[WidgetTest] ${w}x$h dark=$dark catching=$catching child=$i circle=${(diameter / density).toInt()}dp margin=${"%.1f".format(margin)}dp")
                if (asserted && margin < 4f) problems += "${w}x$h dark=$dark catching=$catching child $i is ${"%.1f".format(margin)}dp from the circle's edge"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun render(wDp: Int, hDp: Int, catching: Boolean, dark: Boolean): View {
        val parent = FrameLayout(context)
        val views = PipWidget.build(context, catching = catching, dark = dark, heyPipOn = true)
        val root = views.apply(context, parent)
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        return root
    }

    private fun save(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
