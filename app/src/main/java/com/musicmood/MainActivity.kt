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
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.musicmood.about.AboutFragment
import com.musicmood.bubblemap.BubbleMapFragment
import com.musicmood.library.LibraryFragment
import com.musicmood.player.MiniPlayerView
import com.musicmood.profile.ProfileFragment
import com.musicmood.settings.SettingsFragment
import com.musicmood.stats.StatsFragment
import com.musicmood.weekly.WeeklyReportFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var engineStatus: TextView
    private lateinit var btnGrantPermissions: Button
    private lateinit var setupPanel: LinearLayout

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val multiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[audioPermission] == true
        if (audioGranted) {
            onPermissionGranted()
        } else {
            showSetupPanel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        bottomNav = findViewById(R.id.bottomNav)
        engineStatus = findViewById(R.id.engineStatus)
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions)
        setupPanel = findViewById(R.id.setupPanel)

        setSupportActionBar(toolbar)

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            handleDrawerSelection(item.itemId)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        btnGrantPermissions.setOnClickListener {
            requestAllPermissions()
        }

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
                }.getOrElse { error ->
                    error.printStackTrace()

                    withContext(Dispatchers.Main) {
                        engineStatus.text =
                            "${getString(R.string.engine_status_error)}\n${error.message}"
                    }

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
            this,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (audioOk) {
            onPermissionGranted()
        } else {
            showSetupPanel()
        }
    }

    private fun showSetupPanel() {
        setupPanel.visibility = View.VISIBLE
        bottomNav.visibility = View.GONE
    }

    private fun onPermissionGranted() {
        setupPanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            swapFragment(LibraryFragment(), R.string.nav_library)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_library -> {
                    swapFragment(LibraryFragment(), R.string.nav_library)
                    true
                }

                R.id.nav_stats -> {
                    swapFragment(StatsFragment(), R.string.nav_stats)
                    true
                }

                R.id.nav_profile -> {
                    swapFragment(ProfileFragment(), R.string.nav_profile)
                    true
                }

                else -> false
            }
        }
    }

    private fun handleDrawerSelection(itemId: Int) {
        when (itemId) {
            R.id.drawer_library -> {
                swapFragment(LibraryFragment(), R.string.nav_library)
            }

            R.id.drawer_bubblemap -> {
                swapFragment(BubbleMapFragment(), R.string.nav_bubblemap)
            }

            R.id.drawer_stats -> {
                swapFragment(StatsFragment(), R.string.nav_stats)
            }

            R.id.drawer_profile -> {
                swapFragment(ProfileFragment(), R.string.nav_profile)
            }

            R.id.drawer_weekly -> {
                swapFragment(WeeklyReportFragment(), R.string.drawer_weekly)
            }

            R.id.drawer_settings -> {
                swapFragment(SettingsFragment(), R.string.drawer_settings)
            }

            R.id.drawer_about -> {
                swapFragment(AboutFragment(), R.string.app_name)
            }
        }
    }

    private fun swapFragment(fragment: Fragment, titleRes: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        toolbar.setTitle(titleRes)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
