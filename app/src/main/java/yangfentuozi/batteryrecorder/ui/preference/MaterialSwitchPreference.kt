package yangfentuozi.batteryrecorder.ui.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.materialswitch.MaterialSwitch
import yangfentuozi.batteryrecorder.R

class MaterialSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.switchPreferenceCompatStyle,
    defStyleRes: Int = 0
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes) {

    init {
        widgetLayoutResource = R.layout.preference_switch_md3
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val switchView = holder.findViewById(android.R.id.switch_widget) as? MaterialSwitch
        switchView?.apply {
            isChecked = this@MaterialSwitchPreference.isChecked

            setOnCheckedChangeListener { buttonView, newValue ->
                if (callChangeListener(newValue)) {
                    this@MaterialSwitchPreference.isChecked = newValue
                } else {
                    isChecked = this@MaterialSwitchPreference.isChecked
                }
            }
        }

        holder.itemView.setOnClickListener {
            switchView?.performClick()
        }
    }
}
