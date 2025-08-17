package androidx.preference

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import java.lang.reflect.Field

class SeekBarPreferenceX(
    context: Context,
    attrs: AttributeSet? = null
) : SeekBarPreference(context, attrs) {
    val field: Field = SeekBarPreference::class.java.getDeclaredField("mSeekBarValueTextView")

    init {
        field.isAccessible = true
    }

    @SuppressLint("SetTextI18n")
    override fun updateLabelValue(value: Int) {
        val textView = field.get(this) as? TextView
        textView?.text = "${value.toFloat() / 10}s"
    }
}