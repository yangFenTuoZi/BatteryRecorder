package yangfentuozi.batteryrecorder.ui.settings

import android.os.Bundle
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.databinding.ActivitySettingsBinding
import yangfentuozi.batteryrecorder.ui.BaseActivity

class SettingsActivity: BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        theme.applyStyle(R.style.ThemeOverlay_Rikka_Material3_Preference, true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
        }
    }
}