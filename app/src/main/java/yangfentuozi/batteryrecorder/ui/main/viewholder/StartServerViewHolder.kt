package yangfentuozi.batteryrecorder.ui.main.viewholder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import rikka.recyclerview.BaseViewHolder
import yangfentuozi.batteryrecorder.databinding.HomeItemContainerBinding
import yangfentuozi.batteryrecorder.databinding.HomeStartServerBinding

class StartServerViewHolder(binding: HomeStartServerBinding, root: View) :
    BaseViewHolder<Any?>(root) {

    companion object {
        val CREATOR: Creator<Any?> =
            Creator<Any?> { inflater: LayoutInflater?, parent: ViewGroup? ->
                val outer = HomeItemContainerBinding.inflate(
                    inflater!!, parent, false
                )
                val inner = HomeStartServerBinding.inflate(inflater, outer.getRoot(), true)
                StartServerViewHolder(inner, outer.getRoot())
            }
    }
}