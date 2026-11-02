package com.example.gpslogger.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 記録中、他のアプリを使っているときも画面上部（ステータスバーの位置）に緑の帯を重ねて表示する。
 * 「他のアプリの上に重ねて表示」の許可がないときは何もしない。
 * 帯はタッチを受け取らず、下の画面の操作を妨げない。
 */
class RecordingOverlay(private val context: Context) {

    companion object {
        const val COLOR = 0xFF2E7D32.toInt()

        fun canShow(context: Context) = Settings.canDrawOverlays(context)
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null || !canShow(context)) return
        val bar = View(context).apply { setBackgroundColor(COLOR) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            // Android 12以降、不透明度0.8を超える他アプリの重ね表示はその下へのタッチを遮るので0.8にする
            alpha = 0.8f
        }
        try {
            windowManager.addView(bar, params)
            view = bar
        } catch (e: Exception) {
            // 許可が直前に取り消された場合など。帯なしで記録は続ける
        }
    }

    fun hide() {
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // すでに外れている
            }
        }
        view = null
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id)
        else (24 * context.resources.displayMetrics.density).toInt()
    }
}
