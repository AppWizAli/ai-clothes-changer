package com.ai.tryonclothes.photo.generator.app.presentation.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import coil3.load
import com.ai.tryonclothes.photo.generator.app.R
import java.io.File

/**
 * Draws the original under the result and reveals the result to the right of a draggable divider,
 * like the reference app's "Original | AI Result" comparison.
 */
class BeforeAfterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val before = AppCompatImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val after = AppCompatImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val divider = View(context).apply { setBackgroundColor(0xCCFFFFFF.toInt()) }
    private val handle = AppCompatImageView(context).apply {
        setBackgroundResource(R.drawable.bg_handle)
        setImageResource(R.drawable.ic_compare)
        imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
    }
    private var fraction = 0.5f
    private val handleSize = (44 * resources.displayMetrics.density).toInt()
    private val dividerWidth = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    init {
        addView(before, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(after, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(divider, LayoutParams(dividerWidth, LayoutParams.MATCH_PARENT))
        addView(handle, LayoutParams(handleSize, handleSize))
        clipChildren = true
        contentDescription = context.getString(R.string.slider_description)
    }

    fun setImages(original: File?, result: File?) {
        before.load(original)
        after.load(result)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        applyFraction()
    }

    private fun applyFraction() {
        if (width == 0) return
        val x = (width * fraction).toInt()
        after.clipBounds = Rect(x, 0, width, height)
        divider.translationX = (x - dividerWidth / 2).toFloat()
        handle.translationX = (x - handleSize / 2).toFloat()
        handle.translationY = ((height - handleSize) / 2).toFloat()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                fraction = (event.x / width).coerceIn(0.02f, 0.98f)
                applyFraction()
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
