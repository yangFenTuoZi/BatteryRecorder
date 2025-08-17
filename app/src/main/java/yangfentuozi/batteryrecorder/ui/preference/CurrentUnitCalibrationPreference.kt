package yangfentuozi.batteryrecorder.ui.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import yangfentuozi.batteryrecorder.R

class CurrentUnitCalibrationPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {
    init {
        widgetLayoutResource = R.layout.preference_current_unit_calibration
    }

    var value: Int = -1
    val maxValue: Int = 100000000

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getInt(index, -1)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val persisted = getPersistedInt(defaultValue as? Int ?: -1)
        value = persisted
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val root = holder.itemView as? LinearLayout ?: return

        val valueText = root.findViewById<TextView>(R.id.value).apply {
            text = value.toString()
        }

        root.findViewById<MaterialButton>(R.id.subtract).apply {
            setOnClickListener {
                if (value < 0) value*=10
                else value/=10
                if (value == 0) value = -1
                if (value < -maxValue) value = -maxValue
                valueText.text = value.toString()
                persistInt(value)
            }
        }

        root.findViewById<MaterialButton>(R.id.add).apply {
            setOnClickListener {
                if (value > 0) value*=10
                else value/=10
                if (value == 0) value = 1
                if (value > maxValue) value = maxValue
                valueText.text = value.toString()
                persistInt(value)
            }
        }
    }

}