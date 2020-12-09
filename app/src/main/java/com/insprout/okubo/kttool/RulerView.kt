package com.insprout.okubo.kttool

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.round


class RulerView: View {
    companion object {
        private const val LINE_LENGTH_SCALE_10 = 7.0f     // 10の目盛りの長さ。ミリ単位
        private const val LINE_WIDTH_SCALE_10 = 0.15f     // 10の目盛りの太さ。ミリ単位
        private const val LINE_LENGTH_SCALE_05 = 5.5f     // 5の目盛りの長さ。ミリ単位
        private const val LINE_WIDTH_SCALE_05 = 0.15f     // 5の目盛りの太さ。ミリ単位
        private const val LINE_LENGTH_SCALE_01 = 4.0f     // 1の目盛りの長さ。ミリ単位
        private const val LINE_WIDTH_SCALE_01 = 0.10f     // 1の目盛りの太さ。ミリ単位

        private val WIDTH_LENGTH_SCALE_10 = Pair(LINE_LENGTH_SCALE_10, LINE_WIDTH_SCALE_10)
        private val WIDTH_LENGTH_SCALE_05 = Pair(LINE_LENGTH_SCALE_05, LINE_WIDTH_SCALE_05)
        private val WIDTH_LENGTH_SCALE_01 = Pair(LINE_LENGTH_SCALE_01, LINE_WIDTH_SCALE_01)

        private const val SIZE_SCALE_LABEL = 3.5f
        private const val MARGIN_SCALE_LABEL = LINE_LENGTH_SCALE_10 + 2.0f
    }


    private val mPaintLine = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val mPaintText by lazy {
        // 他のメンバー変数(mYDotsPer1Millimeter)を参照しているので、変数の宣言順序に注意
        // mYDotsPer1Millimeterより前に宣言する場合は、by lazyなど使用する
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 1f
            textSize = SIZE_SCALE_LABEL * mYDotsPer1Millimeter
        }
    }
    private val mYDotsPer1Millimeter = context.resources.displayMetrics.ydpi / 25.4f

    // property
    var lineColor: Int
        get() = mPaintLine.color
        set(value) { mPaintLine.color = value }
    var textColor: Int
        get() = mPaintText.color
        set(value) { mPaintText.color = value }
    var adjustRate: Float = 1f
        set(value) { if (value in 0.9f..1.1f) field = value }

    // コンストラクタ
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)


    // 描画処理を記述
    override fun onDraw(canvas: Canvas) {
        //val minX = paddingStart
        val maxX = canvas.width - 1 - paddingEnd
        val minY = paddingTop
        val maxY = canvas.height - 1 - paddingBottom

        var y = minY.toFloat()
        var mm = 0                                             // 描画する目盛りの ミリメートル値
        while (y <= maxY) {
            y = mm * mYDotsPer1Millimeter * adjustRate + minY

            val (lineLength, lineWidth) = when {
                mm % 10 == 0 -> {
                    // 10mmの目盛りには 数値ラベルを表示するので、その設定
                    // 数字ラベルの描画 (0は 領域からはみ出てしまうので、表記しない)
                    if (mm > 0) {
                        Integer.toString(mm / 10).let {
                            // 目盛りの 表記
                            canvas.drawText(
                                    it,
                                    maxX - MARGIN_SCALE_LABEL * mYDotsPer1Millimeter - getTextWidth(mPaintText, it),
                                    y + getTextHeight(mPaintText) / 3,
                                    mPaintText)
                        }
                    }
                    // 10mmの目盛り線情報
                    WIDTH_LENGTH_SCALE_10
                }

                mm % 5 == 0 -> {
                    // 5mmの目盛り線情報
                    WIDTH_LENGTH_SCALE_05
                }

                else -> {
                    // 1mmの通常の目盛り線情報
                    WIDTH_LENGTH_SCALE_01
                }
            }

            mPaintLine.strokeWidth = round(lineWidth * mYDotsPer1Millimeter)
            canvas.drawLine(maxX - lineLength * mYDotsPer1Millimeter, y, maxX.toFloat(), y, mPaintLine)

            mm++
        }
    }


    private val getTextWidth: (Paint, String) -> Float = { paint, text ->
        paint.measureText(text)
    }

    private val getTextHeight: (Paint) -> Float = { paint: Paint ->
        paint.fontMetrics.let { abs(it.ascent) + abs(it.descent) + abs(it.leading) }
    }

}