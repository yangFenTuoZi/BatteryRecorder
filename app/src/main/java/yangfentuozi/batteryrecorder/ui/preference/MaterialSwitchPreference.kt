package yangfentuozi.batteryrecorder.ui.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
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
}
