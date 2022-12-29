package com.inderdhir.watchfaces.greenlight

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.support.wearable.complications.rendering.ComplicationDrawable

class GreenLightComplicationDrawable(context: Context): ComplicationDrawable(context) {

    init {
        val titleColor = context.getColor(R.color.digital_text_title)
        val textColor =context.getColor(R.color.digital_text)
        val boldTypeface = Typeface.defaultFromStyle(Typeface.BOLD)

        setTextColorActive(textColor)
        setTextColorAmbient(Color.WHITE)

        setTitleColorActive(titleColor)
        setTitleColorAmbient(Color.WHITE)
        setTitleTypefaceActive(boldTypeface)
        setTitleTypefaceAmbient(boldTypeface)

        setIconColorActive(textColor)
        setIconColorAmbient(Color.WHITE)

        setBorderColorActive(Color.TRANSPARENT)

        setRangedValuePrimaryColorActive(textColor)
        setRangedValuePrimaryColorAmbient(Color.WHITE)
        setRangedValueSecondaryColorActive(titleColor)
        setRangedValueSecondaryColorAmbient(Color.WHITE)
        setRangedValueRingWidthActive(2)
        setRangedValueRingWidthAmbient(2)
    }
}