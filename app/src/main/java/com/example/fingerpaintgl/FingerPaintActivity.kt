/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.fingerpaintgl

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.EmbossMaskFilter
import android.graphics.MaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.util.AttributeSet
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fingerpaintgl.databinding.ActivityMainBinding
import timber.log.Timber
import kotlin.math.abs

private const val MENU_ID_COLOR = 0
private const val MENU_ID_EMBOSS = 1
private const val MENU_ID_BLUR = 2
private const val MENU_ID_ERASE = 3
private const val MENU_ID_SRCATOP = 4

class FingerPaintActivity : AppCompatActivity(), PopupMenu.OnMenuItemClickListener,
    ColorPickerDialog.OnColorChangedListener {
    private lateinit var binding: ActivityMainBinding

    private val popupMenu by lazy {
        PopupMenu(this, binding.fab).apply {
            menu.add(0, MENU_ID_COLOR, 0, "Color")
            menu.add(0, MENU_ID_EMBOSS, 0, "Emboss")
            menu.add(0, MENU_ID_BLUR, 0, "Blur")
            menu.add(0, MENU_ID_ERASE, 0, "Erase")
            menu.add(0, MENU_ID_SRCATOP, 0, "SrcATop")
            setOnMenuItemClickListener(this@FingerPaintActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.plant(Timber.DebugTree())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fab.setOnClickListener { popupMenu.show() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        MENU_ID_COLOR -> {
            binding.myView.resetPaint()
            ColorPickerDialog(this, this, binding.myView.paintColor).show()
            true
        }
        MENU_ID_EMBOSS -> {
            binding.myView.resetPaint()
            binding.myView.emboss()
            true
        }
        MENU_ID_BLUR -> {
            binding.myView.resetPaint()
            binding.myView.blur()
            true
        }
        MENU_ID_ERASE -> {
            binding.myView.resetPaint()
            binding.myView.erase()
            true
        }
        MENU_ID_SRCATOP -> {
            binding.myView.resetPaint()
            binding.myView.srcATop()
            true
        }

        else -> false
    }

    override fun colorChanged(color: Int) {
        binding.myView.setPaintColor(color)
    }
}


private const val TOUCH_TOLERANCE = 4f

class MyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private val path = Path()
    private val bitmapPaint = Paint(Paint.DITHER_FLAG)
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    private val emboss: MaskFilter = EmbossMaskFilter(floatArrayOf(1f, 1f, 1f), 0.4f, 6f, 3.5f)
    private val blur: MaskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)

    private val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = -0x10000
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }

    val paintColor: Int
        get() = paint.color

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(-0x555556)
        canvas.drawBitmap(bitmap!!, 0f, 0f, bitmapPaint)
        canvas.drawPath(path, paint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap!!)
        Timber.i("onSizeChanged: %d x %d", w, h)
    }

    fun resetPaint() {
        paint.xfermode = null
        paint.alpha = 0xFF
    }

    fun setPaintColor(color: Int) {
        paint.color = color
    }

    fun emboss() {
        if (paint.maskFilter != emboss) {
            paint.maskFilter = emboss
        } else {
            paint.maskFilter = null
        }
    }

    fun blur() {
        if (paint.maskFilter != blur) {
            paint.maskFilter = blur
        } else {
            paint.maskFilter = null
        }
    }

    fun erase() {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun srcATop() {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        paint.alpha = 0x80
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart(event.x, event.y)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                touchMove(event.x, event.y)
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                touchUp()
                invalidate()
            }
        }
        return true
    }

    private var x = 0f
    private var y = 0f

    private fun touchMove(x: Float, y: Float) {
        val dx = abs(x - this.x)
        val dy = abs(y - this.y)
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            path.quadTo(this.x, this.y, (x + this.x) / 2, (y + this.y) / 2)
            this.x = x
            this.y = y
        }
    }

    private fun touchStart(x: Float, y: Float) {
        path.reset()
        path.moveTo(x, y)
        this.x = x
        this.y = y
    }

    private fun touchUp() {
        path.lineTo(x, y)
        canvas!!.drawPath(path, paint)
        path.reset()
    }

}
