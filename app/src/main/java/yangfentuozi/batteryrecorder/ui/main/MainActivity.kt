package yangfentuozi.batteryrecorder.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.databinding.ActivityMainBinding
import yangfentuozi.batteryrecorder.databinding.DialogAboutBinding
import yangfentuozi.batteryrecorder.ui.main.adapter.HomeAdapter
import yangfentuozi.batteryrecorder.ui.settings.SettingsActivity
import java.util.Locale

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

//        adapter = HomeAdapter()
//        binding.recyclerView.apply {
//            adapter = this@MainActivity.adapter
//        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_about -> {
                val binding = DialogAboutBinding.inflate(layoutInflater, null, false)
                binding.designAboutTitle.setText(R.string.app_name)
                binding.designAboutInfo.movementMethod = LinkMovementMethod.getInstance()
                binding.designAboutInfo.text = HtmlCompat.fromHtml(
                    getString(
                        R.string.about_view_source_code,
                        "<b><a href=\"https://github.com/yangFenTuoZi/BatteryRecorder\">GitHub</a></b>"
                    ), HtmlCompat.FROM_HTML_MODE_LEGACY
                )
                binding.designAboutVersion.text = String.format(
                    Locale.getDefault(),
                    "%s (%d)",
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
                MaterialAlertDialogBuilder(this)
                    .setView(binding.root)
                    .show()
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}