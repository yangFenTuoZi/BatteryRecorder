package yangfentuozi.batteryrecorder.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.databinding.DialogBatchSizeBinding
import yangfentuozi.batteryrecorder.databinding.DialogCurrentUnitCalibrationBinding
import yangfentuozi.batteryrecorder.databinding.DialogIntervalBinding

class SettingsFragment : PreferenceFragmentCompat() {
    @SuppressLint("SetTextI18n")
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.preferences)

        findPreference<Preference>("current_unit_calibration")?.setOnPreferenceClickListener { preference ->
            val binding = DialogCurrentUnitCalibrationBinding.inflate(layoutInflater, null, false)
            val maxValue = 100000000
            var value = preference.sharedPreferences?.getInt(preference.key, -1) ?: -1
            binding.value.text = value.toString()

            binding.subtract.apply {
                setOnClickListener {
                    if (value < 0) value *= 10
                    else value /= 10
                    if (value == 0) value = -1
                    if (value < -maxValue) value = -maxValue
                    binding.value.text = value.toString()
                }
            }

            binding.add.apply {
                setOnClickListener {
                    if (value > 0) value *= 10
                    else value /= 10
                    if (value == 0) value = 1
                    if (value > maxValue) value = maxValue
                    binding.value.text = value.toString()
                }
            }

            fun saveData() {
                preference.sharedPreferences?.edit {
                    putInt(preference.key, value)
                }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preference_current_unit_calibration)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    saveData()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setNeutralButton(R.string.reset) { _, _ ->
                    value = -1
                    saveData()
                }
                .show()
            true
        }

        findPreference<Preference>("interval")?.setOnPreferenceClickListener { preference ->
            val binding = DialogIntervalBinding.inflate(layoutInflater, null, false)
            binding.slider.value = (preference.sharedPreferences?.getInt(preference.key, 900) ?: 900) / 100f

            fun saveData() {
                preference.sharedPreferences?.edit {
                    putInt(preference.key, (binding.slider.value * 100).toInt())
                }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preference_interval)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    saveData()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setNeutralButton(R.string.reset) { _, _ ->
                    binding.slider.value = 0.9f
                    saveData()
                }
                .show()
            return@setOnPreferenceClickListener true
        }

        findPreference<Preference>("batch_size")?.setOnPreferenceClickListener { preference ->
            val binding = DialogBatchSizeBinding.inflate(layoutInflater, null, false)
            binding.editText.setText((preference.sharedPreferences?.getInt(preference.key, 20) ?: 20).toString())

            fun saveData() {
                preference.sharedPreferences?.edit {
                    putInt(preference.key, binding.editText.text.toString().toInt())
                }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preference_batch_size)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    saveData()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setNeutralButton(R.string.reset) { _, _ ->
                    binding.editText.setText("20")
                    saveData()
                }
                .show()
            return@setOnPreferenceClickListener true
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