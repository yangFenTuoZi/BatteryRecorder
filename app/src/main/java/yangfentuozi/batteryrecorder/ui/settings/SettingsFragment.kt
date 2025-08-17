package yangfentuozi.batteryrecorder.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.recyclerview.widget.RecyclerView
import yangfentuozi.batteryrecorder.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.preferences)

        findPreference<SeekBarPreference>("test")?.setOnPreferenceChangeListener { preference, newValue ->
            val progress = newValue as Int
            val step = 100  // 你的步进值
            val min = 0
            val newValue = ((progress - min + step / 2) / step) * step + min
            if (newValue != progress) {
                (preference as SeekBarPreference).value = newValue
            }
            true
        }

    }

    private fun setAllPreferencesToAvoidHavingExtraSpace(preference: Preference) {
        preference.isIconSpaceReserved = false
        if (preference is PreferenceGroup)
            for (i in 0 until preference.preferenceCount)
                setAllPreferencesToAvoidHavingExtraSpace(preference.getPreference(i))
    }

    override fun setPreferenceScreen(preferenceScreen: PreferenceScreen?) {
        if (preferenceScreen != null)
            setAllPreferencesToAvoidHavingExtraSpace(preferenceScreen)
        super.setPreferenceScreen(preferenceScreen)

    }

    @SuppressLint("RestrictedApi")
    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        return object : PreferenceGroupAdapter(preferenceScreen) {
            override fun onPreferenceHierarchyChange(preference: Preference) {
                setAllPreferencesToAvoidHavingExtraSpace(preference)
                super.onPreferenceHierarchyChange(preference)
            }
        }
    }
}