package com.inderdhir.watchfaces.greenlight

import android.app.PendingIntent
import android.content.*
import android.graphics.*
import android.graphics.Paint.FILTER_BITMAP_FLAG
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.complications.SystemProviders
import android.support.wearable.complications.rendering.ComplicationDrawable
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.view.WindowInsets
import androidx.preference.PreferenceManager
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.min

private const val INTERACTIVE_UPDATE_RATE_MS = 1000
private const val MSG_UPDATE_TIME = 0

const val BACKGROUND_COMPLICATION_ID = 1
const val TOP_COMPLICATION_ID = 2
const val BOTTOM_COMPLICATION_ID = 3

class GreenLightWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine() = Engine()

    private class EngineHandler(reference: GreenLightWatchFace.Engine) : Handler(Looper.getMainLooper()) {
        private val weakReference: WeakReference<GreenLightWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            weakReference.get()?.let {
                if (msg.what == MSG_UPDATE_TIME) {
                    it.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine(true) {

        private val calendar = Calendar.getInstance()
        private var hasRegisteredTimeZoneReceiver = false

        /** Paints **/
        private val largeImagePaint by lazy { Paint(FILTER_BITMAP_FLAG) }
        private val timePaint = Paint().apply {
            textSize = resources.getDimension(R.dimen.digital_text_size)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.SANS_SERIF
            strokeWidth = 2f
        }
        private val compBackgroundPaint = Paint().apply {
            color = getColor(R.color.black)
            alpha = 152
        }
        private val notificationIndicatorPaint by lazy { Paint() }

        /** Modes **/
        private var ambient = false
        private var lowBitAmbient = false
        private var burnInProtection = false

        /** Drawing **/
        private var bottomInset = 0
        private var screenRect = Rect()
        private var scale = 1f

        /** Other **/
        private var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
        private val updateTimeHandler = EngineHandler(this)
        private val componentName by lazy { ComponentName(baseContext, GreenLightWatchFace::class.java) }
        private val noPermissionRequestCode = 42

        /** Complications (Default for round) **/
        private val smallImageAndIconBounds by lazy { Rect() }
        private val longTextBounds by lazy { Rect() }
        private val shortTextAndRVBounds = Rect()
        private val topComplicationTapRect by lazy { Rect() }
        private val topComplicationLongTextTapRect by lazy { Rect() }
        private val bottomComplicationTapRect by lazy { Rect() }
        private val bottomComplicationLongTextTapRect by lazy { Rect() }
        private var cornerRadius = 15f
        private var longTextMargin = 55f
        private var margin = 15f
        private var notificationIndicatorRadius = 5f

        private val topComplicationDrawable = GreenLightComplicationDrawable(baseContext)
        private val bottomComplicationDrawable = GreenLightComplicationDrawable(baseContext)

        private var backgroundComplicationData: ComplicationData? = null
        private var topComplicationData: ComplicationData? = null
        private var bottomComplicationData: ComplicationData? = null

        /** Time **/
        private val timeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@GreenLightWatchFace)
                .setAcceptsTapEvents(true)
                .build())

            // Complications
            setActiveComplications(BACKGROUND_COMPLICATION_ID, TOP_COMPLICATION_ID, BOTTOM_COMPLICATION_ID)
            setDefaultSystemComplicationProvider(TOP_COMPLICATION_ID, SystemProviders.DATE,
                ComplicationData.TYPE_SHORT_TEXT)
            setDefaultSystemComplicationProvider(BOTTOM_COMPLICATION_ID,
                SystemProviders.WATCH_BATTERY, ComplicationData.TYPE_RANGED_VALUE)

            updateWatchHandStyle()
        }

        override fun onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onApplyWindowInsets(insets: WindowInsets?) {
            super.onApplyWindowInsets(insets)

            bottomInset =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    insets?.getInsets(WindowInsets.Type.systemBars())?.bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets?.systemWindowInsetBottom
                } ?: 0
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)

            val lowBitAmbient = properties.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            val isBurnIn = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false)
            topComplicationDrawable.apply {
                setLowBitAmbient(lowBitAmbient)
                setBurnInProtection(isBurnIn)
            }
            bottomComplicationDrawable.apply {
                setLowBitAmbient(lowBitAmbient)
                setBurnInProtection(isBurnIn)
            }
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            ambient = inAmbientMode

            updateWatchHandStyle()
            updateTimer()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            screenRect.set(0, 0, width, height)
            scale = min(width, height) / 480f

            val longTextHeight = 150f
            if(resources.configuration.isScreenRound) {
                margin = 15f
                longTextBounds.set(0, 0, (280f * scale).toInt(),
                    (longTextHeight * 0.5f).toInt())
            } else {
                margin = 10f
                longTextBounds.set(0, 0, (380f * scale).toInt(),
                    (longTextHeight * 0.5f).toInt())
                longTextMargin = 30f
            }
            margin *= scale

            /** Complications **/
            cornerRadius *= scale
            longTextMargin *= scale

            val shortTextWidthHeight = 150f * scale
            shortTextAndRVBounds.set(0, 0, shortTextWidthHeight.toInt(),
                shortTextWidthHeight.toInt())

            val iconWidthHeight = 120f * scale
            smallImageAndIconBounds.set(0, 0, iconWidthHeight.toInt(), iconWidthHeight.toInt())

            // Taps
            val centerX = screenRect.width() * 0.5f
            val halfWidth = shortTextAndRVBounds.width() * 0.5f
            topComplicationTapRect.set((centerX - halfWidth).toInt(),
                margin.toInt(), (centerX + halfWidth).toInt(),
                (margin + shortTextAndRVBounds.height()).toInt())
            bottomComplicationTapRect.set((centerX - halfWidth).toInt(),
                screenRect.height() - margin.toInt() - shortTextAndRVBounds.height(),
                (centerX + halfWidth).toInt(), (screenRect.height() - margin).toInt())

            /** Notifications **/
            notificationIndicatorRadius *= scale
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            if(tapType == TAP_TYPE_TAP) { onTap(x, y) }
            invalidate()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }
            updateTimer()
        }

        override fun onComplicationDataUpdate(watchFaceComplicationId: Int, data: ComplicationData?) {
            super.onComplicationDataUpdate(watchFaceComplicationId, data)
            when (watchFaceComplicationId) {
                BACKGROUND_COMPLICATION_ID -> backgroundComplicationData = data
                TOP_COMPLICATION_ID -> topComplicationData = data
                BOTTOM_COMPLICATION_ID -> bottomComplicationData = data
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            drawBackground(canvas)
            drawTime(canvas, bounds)
            drawComplication(canvas, TOP_COMPLICATION_ID)
            drawComplication(canvas, BOTTOM_COMPLICATION_ID)
            drawNotificationIndicator(canvas)
        }

        /** Private **/

        private fun updateWatchHandStyle() {
            topComplicationDrawable.setInAmbientMode(ambient)
            bottomComplicationDrawable.setInAmbientMode(ambient)

            if (ambient) {
                timePaint.apply {
                    style = Paint.Style.STROKE
                    color = Color.WHITE
                }
                notificationIndicatorPaint.color = Color.WHITE
            } else {
                timePaint.apply {
                    style = Paint.Style.FILL
                    color = getColor(R.color.digital_text)
                }
                notificationIndicatorPaint.color = getColor(R.color.digital_text)
            }
        }

        private fun drawBackground(canvas: Canvas) {
            if (isComplicationEnabled(backgroundComplicationData) &&
                !ambient && !lowBitAmbient && !burnInProtection) {
                val mutableBitmap = Bitmap.createBitmap(screenRect.width(), screenRect.width(),
                    Bitmap.Config.RGB_565)
                val bitmapCanvas = Canvas(mutableBitmap)
                backgroundComplicationData?.largeImage?.loadDrawable(baseContext)?.let {
                    it.setBounds(0, 0, screenRect.width(), screenRect.width())
                    it.draw(bitmapCanvas)
                }
                canvas.drawBitmap(mutableBitmap, null, screenRect, largeImagePaint)
            } else {
                canvas.drawColor(Color.BLACK)
            }
        }

        private fun drawTime(canvas: Canvas, bounds: Rect) {
            calendar.timeInMillis = System.currentTimeMillis()

            val is24HourModeEnabled = sharedPreferences.getBoolean(GreenLightSettingsFragment.twentyFourHourMode, false)
            val isSecondsEnabled = sharedPreferences.getBoolean(GreenLightSettingsFragment.secondsEnabled, false)

            val hourText = if (is24HourModeEnabled) calendar.get(Calendar.HOUR_OF_DAY) else calendar.get(Calendar.HOUR)
            val hourPlaceholderString = if(is24HourModeEnabled) "%02d" else "%d"
            val timeText = if (!isInAmbientMode && isSecondsEnabled) {
                String.format(
                    "$hourPlaceholderString:%02d:%02d",
                    hourText, calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND))
            } else {
                String.format(
                    "$hourPlaceholderString:%02d",
                    hourText, calendar.get(Calendar.MINUTE))
            }

            val timeTextBounds = Rect()
            timePaint.getTextBounds(timeText, 0, timeText.length, timeTextBounds)
            canvas.drawText(timeText, bounds.centerX().toFloat(),
                bounds.centerY() + timeTextBounds.height() * 0.5f, timePaint)
        }

        private fun drawComplication(canvas: Canvas, complicationId: Int) {
            val isTopComplication = complicationId == TOP_COMPLICATION_ID
            val complicationData = if (isTopComplication) topComplicationData else bottomComplicationData

            if (!isComplicationEnabled(complicationData)) return

            val isBackgroundEnabled = isComplicationEnabled(backgroundComplicationData)
            val complicationDrawable = if (isTopComplication) topComplicationDrawable else bottomComplicationDrawable
            when (complicationData?.type) {
                ComplicationData.TYPE_ICON ->
                    drawIcon(canvas, complicationDrawable, complicationData, isTopComplication, isBackgroundEnabled)
                ComplicationData.TYPE_SMALL_IMAGE ->
                    drawSmallImage(canvas, complicationDrawable, complicationData, isTopComplication, isBackgroundEnabled)
                ComplicationData.TYPE_LONG_TEXT ->
                    drawLongText(canvas, complicationDrawable, complicationData, isTopComplication, isBackgroundEnabled)
                ComplicationData.TYPE_SHORT_TEXT ->
                    drawShortTextOrRangedValue(canvas, complicationDrawable, complicationData, isTopComplication, isBackgroundEnabled)
                ComplicationData.TYPE_RANGED_VALUE ->
                    drawShortTextOrRangedValue(canvas, complicationDrawable, complicationData, isTopComplication, isBackgroundEnabled)
                ComplicationData.TYPE_NO_DATA ->
                    drawShortTextOrRangedValue(canvas, complicationDrawable, complicationData, isTopComplication, isBackgroundEnabled)
            }
        }

        private fun drawNotificationIndicator(canvas: Canvas) {
            val isNotificationIndicatorEnabled = sharedPreferences.getBoolean(
                GreenLightSettingsFragment.notificationIndicator, true
            )
            if (isNotificationIndicatorEnabled && unreadCount > 0) {
                canvas.drawCircle(screenRect.width() * 0.5f,
                    screenRect.height() - notificationIndicatorRadius - 2 - bottomInset,
                    notificationIndicatorRadius, notificationIndicatorPaint)
            }
        }

        private fun registerReceiver() {
            if (hasRegisteredTimeZoneReceiver) return

            hasRegisteredTimeZoneReceiver = true
            registerReceiver(timeZoneReceiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
        }

        private fun unregisterReceiver() {
            if (!hasRegisteredTimeZoneReceiver) return

            hasRegisteredTimeZoneReceiver = false
            unregisterReceiver(timeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning() = isVisible && !ambient

        private fun onTap(x: Int, y: Int) {
            val isTopLongTextComp = topComplicationData != null &&
                    topComplicationData?.type == ComplicationData.TYPE_LONG_TEXT
            val isBottomLongTextComp = bottomComplicationData != null &&
                    bottomComplicationData?.type == ComplicationData.TYPE_LONG_TEXT

            if (topComplicationTapRect.contains(x, y) ||
                (isTopLongTextComp && topComplicationLongTextTapRect.contains(x, y))) {
                if (topComplicationData == null ||
                    topComplicationData?.type == ComplicationData.TYPE_EMPTY) {
                    launchProviderChooser(TOP_COMPLICATION_ID)
                } else if (topComplicationData?.type == ComplicationData.TYPE_NO_PERMISSION) {
                    PendingIntent.getActivity(baseContext, noPermissionRequestCode,
                        ComplicationHelperActivity.createPermissionRequestHelperIntent(
                            baseContext, componentName), PendingIntent.FLAG_UPDATE_CURRENT)
                }
                else {
                    topComplicationData?.tapAction?.send()
                }
            } else if (bottomComplicationTapRect.contains(x, y) ||
                (isBottomLongTextComp && bottomComplicationLongTextTapRect.contains(x, y))) {
                if (bottomComplicationData == null ||
                    bottomComplicationData?.type == ComplicationData.TYPE_EMPTY) {
                    launchProviderChooser(BOTTOM_COMPLICATION_ID)
                } else if (bottomComplicationData?.type == ComplicationData.TYPE_NO_PERMISSION) {
                    PendingIntent.getActivity(baseContext, noPermissionRequestCode,
                        ComplicationHelperActivity.createPermissionRequestHelperIntent(
                            baseContext, componentName), PendingIntent.FLAG_UPDATE_CURRENT)
                } else {
                    bottomComplicationData?.tapAction?.send()
                }
            }
        }

        private fun drawIcon(
            canvas: Canvas,
            complicationDrawable: ComplicationDrawable,
            complicationData: ComplicationData?,
            isTopComplication: Boolean,
            isBackgroundEnabled: Boolean
        ) {
            canvas.save()

            complicationDrawable.setComplicationData(complicationData)

            complicationDrawable.bounds = smallImageAndIconBounds
            if (isTopComplication) {
                canvas.translate(
                    screenRect.width() * 0.5f - smallImageAndIconBounds.width() * 0.5f, margin)
            } else {
                canvas.translate(
                    screenRect.width() * 0.5f - smallImageAndIconBounds.width() * 0.5f,
                    screenRect.height() - margin - smallImageAndIconBounds.height())
            }

            if (!isInAmbientMode && isBackgroundEnabled) {
                val iconBoundsRect = RectF(0f, 0f, smallImageAndIconBounds.width().toFloat(),
                    smallImageAndIconBounds.height().toFloat())
                canvas.drawArc(iconBoundsRect, -90f, 360f, false, compBackgroundPaint)
            }
            complicationDrawable.draw(canvas, System.currentTimeMillis())

            canvas.restore()
        }

        private fun drawSmallImage(
            canvas: Canvas,
            complicationDrawable: ComplicationDrawable,
            complicationData: ComplicationData?,
            isTopComplication: Boolean,
            isBackgroundEnabled: Boolean
        ) {
            if (!isInAmbientMode) {
                canvas.save()

                complicationDrawable.setComplicationData(complicationData)
                complicationDrawable.bounds = smallImageAndIconBounds

                if (isTopComplication) {
                    canvas.translate(
                        screenRect.width() * 0.5f - smallImageAndIconBounds.width() * 0.5f, margin)
                } else {
                    canvas.translate(
                        screenRect.width() * 0.5f - smallImageAndIconBounds.width() * 0.5f,
                        screenRect.height() - margin - smallImageAndIconBounds.height())
                }
                if (!isInAmbientMode && isBackgroundEnabled) {
                    val iconBoundsRect = RectF(0f, 0f, smallImageAndIconBounds.width().toFloat(),
                        smallImageAndIconBounds.height().toFloat())
                    canvas.drawArc(iconBoundsRect, -90f, 360f, false, compBackgroundPaint)
                }
                complicationDrawable.draw(canvas, System.currentTimeMillis())

                canvas.restore()
            }
        }

        private fun drawShortTextOrRangedValue(
            canvas: Canvas,
            complicationDrawable: ComplicationDrawable,
            complicationData: ComplicationData?,
            isTopComplication: Boolean,
            isBackgroundEnabled: Boolean
        ) {
            canvas.save()

            complicationDrawable.setComplicationData(complicationData)
            complicationDrawable.bounds = shortTextAndRVBounds

            if (isTopComplication) {
                canvas.translate(
                    screenRect.width() * 0.5f - shortTextAndRVBounds.width() * 0.5f, margin)
            } else {
                canvas.translate(
                    screenRect.width() * 0.5f - shortTextAndRVBounds.width() * 0.5f,
                    screenRect.height() - margin - shortTextAndRVBounds.height())
            }
            if (!isInAmbientMode && isBackgroundEnabled) {
                val shortTextBounds = RectF(0f, 0f, shortTextAndRVBounds.width().toFloat(),
                    shortTextAndRVBounds.height().toFloat())
                canvas.drawArc(shortTextBounds, -90f, 360f, false, compBackgroundPaint)
            }
            complicationDrawable.draw(canvas, System.currentTimeMillis())

            canvas.restore()
        }

        private fun drawLongText(
            canvas: Canvas,
            complicationDrawable: ComplicationDrawable,
            complicationData: ComplicationData?,
            isTopComplication: Boolean,
            isBackgroundEnabled: Boolean
        ) {
            val centerX = screenRect.width() * 0.5f

            canvas.save()

            complicationDrawable.setComplicationData(complicationData)
            complicationDrawable.bounds = longTextBounds

            if (isTopComplication) {
                canvas.translate(centerX - longTextBounds.width() * 0.5f, longTextMargin)
            } else {
                canvas.translate(centerX - longTextBounds.width() * 0.5f,
                    screenRect.height() - longTextMargin - longTextBounds.height())
            }
            if (!isInAmbientMode && isBackgroundEnabled) {
                canvas.drawRoundRect(0f, 0f, longTextBounds.width().toFloat(),
                    longTextBounds.height().toFloat(), cornerRadius, cornerRadius, compBackgroundPaint)
            }
            complicationDrawable.draw(canvas, System.currentTimeMillis())

            canvas.restore()
        }

        private fun isComplicationEnabled(complicationData: ComplicationData?): Boolean {
            return complicationData != null &&
                    complicationData.type != ComplicationData.TYPE_NO_PERMISSION
                    && complicationData.type != ComplicationData.TYPE_NOT_CONFIGURED
                    && complicationData.type != ComplicationData.TYPE_EMPTY
        }

        private fun launchProviderChooser(complicationId: Int) {
            baseContext.startActivity(ComplicationHelperActivity.createProviderChooserHelperIntent(
                baseContext, componentName, complicationId, ComplicationData.TYPE_LONG_TEXT,
                ComplicationData.TYPE_RANGED_VALUE, ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_SMALL_IMAGE, ComplicationData.TYPE_ICON))
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}