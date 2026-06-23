package com.musicmood

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.musicmood.library.LibraryFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var engineStatus: TextView
    private lateinit var btnGrantPermissions: Button
    private lateinit var setupPanel: LinearLayout

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val multiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[audioPermission] == true
        if (audioGranted) onPermissionGranted() else showSetupPanel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engineStatus        = findViewById(R.id.engineStatus)
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions)
        setupPanel          = findViewById(R.id.setupPanel)

        btnGrantPermissions.setOnClickListener { requestAllPermissions() }
        initializePython()
    }

    private fun requestAllPermissions() {
        val toRequest = mutableListOf(audioPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            toRequest += Manifest.permission.POST_NOTIFICATIONS
        }
        multiPermissionLauncher.launch(toRequest.toTypedArray())
    }

    private fun initializePython() {
        engineStatus.text = getString(R.string.engine_status_loading)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(this@MainActivity))
                    }
                    Python.getInstance().getModule("music_analyzer")
                    true
                }.getOrElse { e ->
                    e.printStackTrace()
                    engineStatus.text =
                        "${getString(R.string.engine_status_error)}\n${e.message}"
                    false
                }
            }
            if (ok) {
                engineStatus.text = getString(R.string.engine_status_ready)
                checkPermissionsAndProceed()
            }
        }
    }

    private fun checkPermissionsAndProceed() {
        val audioOk = ContextCompat.checkSelfPermission(
            this, audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (audioOk) onPermissionGranted() else showSetupPanel()
    }

    private fun showSetupPanel() { setupPanel.visibility = View.VISIBLE }

    private fun onPermissionGranted() {
        setupPanel.visibility = View.GONE
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LibraryFragment())
                .commit()
        }
    }
}
