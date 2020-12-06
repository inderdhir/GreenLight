package com.inderdhir.watchfaces.greenlight

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.preference.PreferenceIconHelper
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference


/**
 * Created by inderdhir on 6/11/17.
 */

class GreenLightSettingsFragment : PreferenceFragmentCompat() {

    private val requestCode = 42

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(context)
        addDataPreference()
        addLargeImageBackgroundPreference()
        add24HourPreference()
        addSecondsPreference()
        addNotificationIndicatorPreference()
    }

    private fun addDataPreference() {
        Preference(requireContext()).apply {
            order = 1
            title = getString(R.string.settings_data_title)
            PreferenceIconHelper.wrapIcon(this)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(Intent(activity, GreenLightDataActivity::class.java))
                true
            }
            preferenceScreen.addPreference(this)
        }
    }

    private fun addLargeImageBackgroundPreference() {
        Preference(requireContext()).apply {
            order = 2
            title = getString(R.string.settings_background_title)
            PreferenceIconHelper.wrapIcon(this)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                        context,
                        ComponentName(requireContext(), GreenLightWatchFace::class.java),
                        BACKGROUND_COMPLICATION_ID,
                        ComplicationData.TYPE_LARGE_IMAGE
                    ),
                    requestCode
                )
                true
            }
            preferenceScreen.addPreference(this)
        }
    }

    private fun add24HourPreference() {
        SwitchPreference(requireContext()).apply {
            order = 3
            title = getString(R.string.settings_24_hour_mode)
            key = twentyFourHourMode
            setDefaultValue(false)
            preferenceScreen.addPreference(this)
        }
    }

    private fun addSecondsPreference() {
        SwitchPreference(requireContext()).apply {
            order = 4
            title = getString(R.string.settings_seconds)
            key = secondsEnabled
            setDefaultValue(false)
            preferenceScreen.addPreference(this)
        }
    }

    private fun addNotificationIndicatorPreference() {
        SwitchPreference(requireContext()).apply {
            order = 5
            title = getString(R.string.settings_notification_indicator)
            key = notificationIndicator
            setDefaultValue(true)
            preferenceScreen.addPreference(this)
        }
    }

    companion object {
        const val secondsEnabled = "SECONDS_ENABLED"
        const val twentyFourHourMode = "TWENTY_FOUR_HOUR_MODE"
        const val notificationIndicator = "NOTIFICATION_INDICATOR"
    }
}
