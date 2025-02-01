package ru.anixon.uikit.components.simple

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import ru.anixon.uikit.R

/**
 * Компонент микшер звука (моя волна)
 *
 * @param context контект
 * @param attributeSet набор аттрибутов
 */
@SuppressLint("ClickableViewAccessibility")
internal class MixerSoundView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet), Simple {

    private val staticWhiteColor = Color.WHITE
    private val staticBlackColor = Color.BLACK

    private val background =
        ResourcesCompat.getDrawable(
            resources,
            R.drawable.il_mixer_sound_view_background,
            context.theme
        )
    private var backgroundShape: RoundedBitmapDrawable? = null
    private val backgroundShapeSize = resources.getDimensionPixelSize(R.dimen.size_16)

    private val dot =
        ResourcesCompat.getDrawable(resources, R.drawable.bg_oval, context.theme)
    private val dotTintColor = staticWhiteColor
    private val dotMargins = resources.getDimensionPixelSize(R.dimen.size_64)
    private val dotSpaceDefault = resources.getDimensionPixelSize(R.dimen.size_40)
    private val dotSize = resources.getDimensionPixelSize(R.dimen.size_2)
    private val dotAreaKeys = mutableSetOf<KeyAreaData>()
    private var dotSpaceColumn = dotSpaceDefault
    private var dotSpaceRow = dotSpaceDefault

    private val textColor = staticWhiteColor
    private val textMargins = resources.getDimensionPixelSize(R.dimen.size_32)
    private val textLeftTop = resources.getString(R.string.mixer_sound_default_energy).uppercase()
    private val textRightTop = resources.getString(R.string.mixer_sound_default_fun).uppercase()
    private val textLeftBottom = resources.getString(R.string.mixer_sound_default_sad).uppercase()
    private val textRightBottom =
        resources.getString(R.string.mixer_sound_default_relax).uppercase()
    private var textPaint: Paint = getTextStyleDefault()
    private var textBoundsRightTop: Rect = Rect()
    private var textBoundsRightBottom: Rect = Rect()

    private val buttonMask =
        ResourcesCompat.getDrawable(resources, R.drawable.il_mixer_sound_view_mask, context.theme)
    private val buttonDrawable =
        ResourcesCompat.getDrawable(resources, R.drawable.bg_oval, context.theme)
    private val buttonSize = resources.getDimensionPixelSize(R.dimen.size_32)
    private val buttonRect: Rect =
        Rect(COORDINATES_NULL, COORDINATES_NULL, COORDINATES_NULL, COORDINATES_NULL)
    private var buttonX: Int = COORDINATES_NULL
    private var buttonY: Int = COORDINATES_NULL
    private var buttonOnChange: ((x: Double, y: Double) -> Unit)? = null

    private val confirmTimer = ConfirmTimer(TIMER_SHOW_TIME, TIMER_SHOW_TIME_INTERVAL)
    private val confirmIcon =
        ResourcesCompat.getDrawable(resources, R.drawable.ic_24_done, context.theme)
    private val confirmBackground =
        ResourcesCompat.getDrawable(resources, R.drawable.bg_oval, context.theme)
    private val confirmBackgroundAlpha = 122
    private val confirmSize = resources.getDimensionPixelSize(R.dimen.size_64)
    private var isConfirmVisible = false

    private var bufferData: Data? = null

    init {
        val bitmapBackground =
            BitmapFactory.decodeResource(resources, R.drawable.il_mixer_sound_view_background)
        backgroundShape = RoundedBitmapDrawableFactory.create(
            resources,
            bitmapBackground
        )

        TextView(context)
            .apply { setTextAppearance(R.style.TextStyle) }
            .let { textView ->
                textPaint = textView.paint
                textPaint.color = textColor
                textPaint.getTextBounds(
                    textRightTop,
                    0,
                    textRightTop.length,
                    textBoundsRightTop
                )
                textPaint.getTextBounds(
                    textRightBottom,
                    0,
                    textRightBottom.length,
                    textBoundsRightBottom
                )
            }

        setOnTouchListener { _, event -> mixerOnTouch(event) }
    }

    override fun onDraw(canvas: Canvas) {
        background?.let { bg ->
            backgroundShape?.let { bgShape ->
                bgShape.cornerRadius = backgroundShapeSize.toFloat()
                bgShape.setBounds(0, 0, width, height)
                bgShape.draw(canvas)
            } ?: run {
                bg.setBounds(0, 0, width, height)
                bg.draw(canvas)
            }
        }

        drawDots(canvas)
        drawButton(canvas)
        drawTexts(canvas)
        drawConfirm(canvas)

        super.onDraw(canvas)
    }

    /**
     * Установить данные
     *
     * @param data данные
     */
    fun set(data: Data) {
        bufferData = data
        setButtonLocationToKeys(data.x, data.y)
    }

    /**
     * Установить слушателя изменений
     *
     * @param onChange событие обратного вызова изменений выбора
     */
    fun setOnChange(onChange: ((x: Double, y: Double) -> Unit)?) {
        buttonOnChange = onChange
    }

    /**
     * Очистить установленные парамеры
     */
    fun clear() {
        bufferData = null
        buttonX = COORDINATES_NULL
        buttonY = COORDINATES_NULL
        invalidate()
    }

    private fun drawDots(canvas: Canvas) {
        dotSpaceColumn = (canvas.width - (dotMargins * 2)) / DEFAULT_COLUMN_COUNT
        dotSpaceRow = (canvas.height - (dotMargins * 2)) / DEFAULT_ROW_COUNT

        dotAreaKeys.clear()
        for (i in 0..DEFAULT_ROW_COUNT) {
            for (j in 0..DEFAULT_COLUMN_COUNT) {
                dot?.let { drawable ->
                    val left = j * dotSpaceColumn + dotMargins
                    val top = i * dotSpaceRow + dotMargins
                    val right = left + dotSize
                    val bottom = top + dotSize
                    drawable.setBounds(left, top, right, bottom)
                    drawable.setTint(dotTintColor)
                    drawable.draw(canvas)

                    val areaKeyRect = Rect(
                        left - dotSpaceColumn / 2,
                        top - dotSpaceRow / 2,
                        right + dotSpaceColumn / 2,
                        bottom + dotSpaceRow / 2
                    )
                    dotAreaKeys.add(
                        KeyAreaData(
                            areaRect = areaKeyRect,
                            keyX = j,
                            keyY = i
                        )
                    )

                    buttonRect.left =
                        if (left < buttonRect.left || buttonRect.left == COORDINATES_NULL) left else buttonRect.left
                    buttonRect.top =
                        if (top < buttonRect.top || buttonRect.top == COORDINATES_NULL) top else buttonRect.top
                    buttonRect.right =
                        if (right > buttonRect.right || buttonRect.right == COORDINATES_NULL) right else buttonRect.right
                    buttonRect.bottom =
                        if (bottom > buttonRect.bottom || buttonRect.bottom == COORDINATES_NULL) bottom else buttonRect.bottom
                }
            }
        }
    }

    private fun drawTexts(canvas: Canvas) {
        canvas.drawText(
            textLeftTop,
            textMargins.toFloat(),
            textMargins.toFloat(),
            textPaint
        )
        canvas.drawText(
            textRightTop,
            canvas.width - textBoundsRightTop.width() - textMargins.toFloat(),
            textMargins.toFloat(),
            textPaint
        )
        canvas.drawText(
            textLeftBottom,
            textMargins.toFloat(),
            height - textMargins.toFloat(),
            textPaint
        )
        canvas.drawText(
            textRightBottom,
            canvas.width - textBoundsRightBottom.width() - textMargins.toFloat(),
            height.toFloat() - textMargins.toFloat(),
            textPaint
        )
    }

    private fun drawButton(canvas: Canvas) {
        bufferData?.let { data ->
            setButtonLocationToKeys(data.x, data.y)
            return
        }

        if (buttonX == COORDINATES_NULL || buttonY == COORDINATES_NULL) {
            buttonX = (width / 2)
            buttonY = (height / 2)
        }

        val offset = buttonSize / 2

        buttonDrawable?.let { drawable ->
            drawable.setBounds(
                buttonX - offset,
                buttonY - offset,
                buttonX + offset,
                buttonY + offset
            )
            drawable.setTint(dotTintColor)
            drawable.draw(canvas)
        }

        buttonMask?.let { drawable ->
            val offset2 = this.width
            drawable.setBounds(
                buttonX - offset2,
                buttonY - offset2,
                buttonX + offset2,
                buttonY + offset2
            )
            drawable.draw(canvas)
        }
    }

    private fun drawConfirm(canvas: Canvas) {
        if (!isConfirmVisible) return
        val rect = Rect(
            canvas.width / 2 - confirmSize / 2,
            canvas.height / 2 - confirmSize / 2,
            width / 2 + confirmSize / 2,
            height / 2 + confirmSize / 2
        )

        confirmBackground?.let { drawable ->
            drawable.bounds = rect
            drawable.setTint(staticBlackColor)
            drawable.alpha = confirmBackgroundAlpha
            drawable.draw(canvas)
        }
        confirmIcon?.let { drawable ->
            drawable.bounds = rect
            drawable.draw(canvas)
        }

        confirmTimer.start()
    }

    private fun mixerOnTouch(event: MotionEvent?): Boolean {
        event?.let { e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    setButtonLocationOnTouch(e.x, e.y)
                }

                MotionEvent.ACTION_MOVE -> {
                    setButtonLocationOnTouch(e.x, e.y)
                }

                MotionEvent.ACTION_UP -> {
                    fixedButtonLocation()
                }

                else -> return false
            }
        }
        return true
    }

    private fun setButtonLocationOnTouch(x: Float, y: Float) {
        var bufferX = x.toInt()
        var bufferY = y.toInt()

        if (!buttonRect.isEmpty) {
            if (bufferX <= buttonRect.left || bufferX >= buttonRect.right) {
                bufferX = buttonX
            }
            if (bufferY <= buttonRect.top || bufferY >= buttonRect.bottom) {
                bufferY = buttonY
            }
        }

        buttonX = bufferX
        buttonY = bufferY
        invalidate()
    }

    private fun setButtonLocationToKeys(x: Double, y: Double) {
        if (dotAreaKeys.isEmpty()) return
        val castX = Math.round(x * DEFAULT_ROW_COUNT).toInt()
        val castY = Math.round(DEFAULT_COLUMN_COUNT - (y * DEFAULT_COLUMN_COUNT)).toInt()
        dotAreaKeys.forEach { area ->
            if (area.keyX == castX && area.keyY == castY) {
                buttonX = area.areaRect.centerX()
                buttonY = area.areaRect.centerY()
                return@forEach
            }
        }
        bufferData = null
        invalidate()
    }

    private fun fixedButtonLocation() {
        isConfirmVisible = true
        if (dotAreaKeys.isNotEmpty()) {
            dotAreaKeys.forEach { area ->
                if (area.areaRect.contains(buttonX, buttonY)) {
                    buttonX = area.areaRect.centerX()
                    buttonY = area.areaRect.centerY()

                    val energyMood =
                        try {
                            val revers = DEFAULT_COLUMN_COUNT - area.keyY
                            Math.round((revers / DEFAULT_COLUMN_COUNT.toDouble()) * 100.00) / 100.00
                        } catch (ex: Exception) {
                            0.0
                        }

                    val funMood =
                        try {
                            Math.round((area.keyX / DEFAULT_ROW_COUNT.toDouble()) * 100.00) / 100.00
                        } catch (ex: Exception) {
                            0.0
                        }

                    buttonOnChange?.invoke(energyMood, funMood)
                    return@forEach
                }
            }
        }
        invalidate()
    }

    private fun getTextStyleDefault() = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, R.style.TextStyle)
    }

    private data class KeyAreaData(
        val areaRect: Rect,
        val keyX: Int,
        val keyY: Int
    )

    private inner class ConfirmTimer(
        millisInFuture: Long,
        countDownInterval: Long
    ) : CountDownTimer(millisInFuture, countDownInterval) {

        override fun onTick(millisUntilFinished: Long) {}

        override fun onFinish() {
            isConfirmVisible = false
            invalidate()
        }
    }

    /**
     * Данные для компонента MixerSound
     *
     * @property x координата колонки
     * @property y координатта строки
     */
    data class Data(
        val x: Double = COORDINATES_NULL.toDouble(),
        val y: Double = COORDINATES_NULL.toDouble(),
    )

    companion object {
        private const val COORDINATES_NULL = -1
        private const val TIMER_SHOW_TIME = 1000L
        private const val TIMER_SHOW_TIME_INTERVAL = 1000L
        private const val DEFAULT_COLUMN_COUNT = 7
        private const val DEFAULT_ROW_COUNT = 7
    }
}