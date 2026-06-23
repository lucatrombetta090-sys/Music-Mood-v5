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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.musicmood.bubblemap.BubbleMapFragment
import com.musicmood.library.LibraryFragment
import com.musicmood.player.MiniPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var engineStatus: TextView
    private lateinit var btnGrantPermissions: Button
    private lateinit var setupPanel: LinearLayout
    private lateinit var bottomNav: BottomNavigationView

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
        bottomNav           = findViewById(R.id.bottomNav)

        btnGrantPermissions.setOnClickListener { requestAllPermissions() }

        // Mini-player auto-osserva PlayerController
        findViewById<MiniPlayerView>(R.id.miniPlayer).observe(this)

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

    private fun showSetupPanel() {
        setupPanel.visibility = View.VISIBLE
        bottomNav.visibility = View.GONE
    }

    private fun onPermissionGranted() {
        setupPanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE

        // Fragment iniziale = Library
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            swapFragment(LibraryFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_library   -> { swapFragment(LibraryFragment()); true }
                R.id.nav_bubblemap -> { swapFragment(BubbleMapFragment()); true }
                else               -> false
            }
        }
    }

    private fun swapFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
