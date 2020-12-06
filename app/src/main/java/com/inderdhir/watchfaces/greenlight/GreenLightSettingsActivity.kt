package com.inderdhir.watchfaces.greenlight

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Created by inderdhir on 6/11/17.
 */

class GreenLightSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .add(R.id.content, GreenLightSettingsFragment())
            .commit()
    }
}
