package yangfentuozi.batteryrecorder.ui.home

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import yangfentuozi.batteryrecorder.databinding.ActivityMainBinding
import yangfentuozi.batteryrecorder.ui.home.adapter.HomeAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HomeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && DynamicColors.isDynamicColorAvailable())
            DynamicColors.applyToActivityIfAvailable(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.Companion.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.Companion.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        adapter = HomeAdapter()
        binding.recyclerView.apply {
            adapter = this@MainActivity.adapter
        }
    }
}