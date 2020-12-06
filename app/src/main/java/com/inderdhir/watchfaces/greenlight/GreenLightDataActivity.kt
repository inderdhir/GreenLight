package com.inderdhir.watchfaces.greenlight

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.wearable.complications.*
import android.view.View
import kotlinx.android.synthetic.main.activity_data.*
import java.util.concurrent.Executors


/**
 * Created by inderdhir on 6/12/17.
 */

class GreenLightDataActivity : Activity(), View.OnClickListener {

    private val complicationConfigRequestCode = 42
    private var currentSelectedComplicationId = -1
    private var providerInfoRetriever: ProviderInfoRetriever? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        top_complication.setOnClickListener(this)
        bottom_complication.setOnClickListener(this)
        background_watchface_view.setImageResource(
                if(resources.configuration.isScreenRound)
                    R.drawable.preview_digital_circular else R.drawable.preview_digital
        )

        loadProviderIconsForComplications()
    }

    override fun onClick(p0: View?) {
        currentSelectedComplicationId =
                if(top_complication == p0) TOP_COMPLICATION_ID
                else BOTTOM_COMPLICATION_ID
        startActivityForResult(
                ComplicationHelperActivity.createProviderChooserHelperIntent(
                        this,
                        ComponentName(this, GreenLightWatchFace::class.java),
                        currentSelectedComplicationId,
                        ComplicationData.TYPE_RANGED_VALUE,
                        ComplicationData.TYPE_LONG_TEXT,
                        ComplicationData.TYPE_SHORT_TEXT,
                        ComplicationData.TYPE_SMALL_IMAGE,
                        ComplicationData.TYPE_ICON
                ),
                complicationConfigRequestCode
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == complicationConfigRequestCode && resultCode == RESULT_OK) {
            val complicationProviderInfo =
                    data?.getParcelableExtra<ComplicationProviderInfo>(ProviderChooserIntent.EXTRA_PROVIDER_INFO)
            val selectedView =
                    if (currentSelectedComplicationId == TOP_COMPLICATION_ID) {
                        top_complication
                    } else {
                        bottom_complication
                    }
            if(complicationProviderInfo?.providerIcon != null) {
                selectedView.setImageIcon(complicationProviderInfo.providerIcon)
            } else {
                selectedView.setImageResource(android.R.drawable.ic_menu_add)
            }
        }
    }

    override fun onDestroy() {
        providerInfoRetriever?.release()
        super.onDestroy()
    }

    private fun loadProviderIconsForComplications() {
        // Initialization of code to retrieve active complication data for the watch face.
        providerInfoRetriever = ProviderInfoRetriever(this, Executors.newCachedThreadPool()).apply {
            init()
            retrieveProviderInfo(
                    object : ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                        override fun onProviderInfoReceived(
                                watchFaceComplicationId: Int,
                                complicationProviderInfo: ComplicationProviderInfo?) {
                            val selectedView =
                                    if (watchFaceComplicationId == TOP_COMPLICATION_ID)
                                        top_complication else bottom_complication
                            complicationProviderInfo?.providerIcon?.let { selectedView.setImageIcon(it) }
                        }
                    },
                    ComponentName(baseContext, GreenLightWatchFace::class.java),
                    TOP_COMPLICATION_ID,
                    BOTTOM_COMPLICATION_ID
            )
        }
    }
}
