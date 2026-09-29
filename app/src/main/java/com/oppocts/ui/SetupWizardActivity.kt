package com.ctslauncher.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.ctslauncher.R
import com.ctslauncher.trigger.CTSTrigger

class SetupWizardActivity : AppCompatActivity() {

    private var currentStep = 1
    private val totalSteps = 8

    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDesc: TextView
    private lateinit var btnAction: Button
    private lateinit var btnNext: Button
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)

        if (prefs.getBoolean("setup_completed", false)) {
            val triggerMethod = prefs.getInt("trigger_method", 0)
            if (triggerMethod == 2) {
                val delayMs = prefs.getInt("trigger_delay_ms", 200).toLong()
                Handler(Looper.getMainLooper()).postDelayed({
                    CTSTrigger.trigger(this)
                }, delayMs)
                finish()
                return
            } else {
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
                return
            }
        }

        setContentView(R.layout.activity_setup_wizard)

        tvStepTitle = findViewById(R.id.tv_step_title)
        tvStepDesc = findViewById(R.id.tv_step_desc)
        btnAction = findViewById(R.id.btn_action)
        btnNext = findViewById(R.id.btn_next)
        btnBack = findViewById(R.id.btn_back)

        btnNext.setOnClickListener {
            if (currentStep < totalSteps) {
                currentStep++
                updateUI()
            } else {
                prefs.edit().putBoolean("setup_completed", true).apply()
                Toast.makeText(this, getString(R.string.toast_setup_complete), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            }
        }

        btnBack.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                updateUI()
            }
        }

        updateUI()
    }

    private fun openGooglePlayStore(pkg: String) {
        val playStorePkg = "com.android.vending"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                setPackage(playStorePkg)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
            } catch (ex: Exception) {
                Toast.makeText(this, getString(R.string.toast_play_store_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        btnBack.visibility = if (currentStep == 1) View.GONE else View.VISIBLE
        btnNext.text = if (currentStep == totalSteps) getString(R.string.btn_done) else getString(R.string.btn_next)

        when (currentStep) {
            1 -> {
                tvStepTitle.text = getString(R.string.wizard_step1_title)
                tvStepDesc.text = getString(R.string.wizard_step1_desc)
                btnAction.text = getString(R.string.btn_store)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener { openGooglePlayStore("com.google.android.googlequicksearchbox") }
            }
            2 -> {
                tvStepTitle.text = getString(R.string.wizard_step2_title)
                tvStepDesc.text = getString(R.string.wizard_step2_desc)
                btnAction.text = getString(R.string.btn_store)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener { openGooglePlayStore("com.google.android.tts") }
            }
            3 -> {
                tvStepTitle.text = getString(R.string.wizard_step3_title)
                tvStepDesc.text = getString(R.string.wizard_step3_desc)
                btnAction.text = getString(R.string.btn_store)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener { openGooglePlayStore("com.google.android.apps.bard") }
            }
            4 -> {
                tvStepTitle.text = getString(R.string.wizard_step4_title)
                tvStepDesc.text = getString(R.string.wizard_step4_desc)
                btnAction.text = getString(R.string.btn_settings)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        } catch (ex: Exception) {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                }
            }
            5 -> {
                tvStepTitle.text = getString(R.string.wizard_step5_title)
                tvStepDesc.text = getString(R.string.wizard_step5_desc)
                btnAction.text = getString(R.string.btn_settings)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                    }
                }
            }
            6 -> {
                tvStepTitle.text = getString(R.string.wizard_step6_title)
                tvStepDesc.text = getString(R.string.wizard_step6_desc)
                btnAction.text = getString(R.string.btn_settings)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            7 -> {
                tvStepTitle.text = getString(R.string.wizard_step7_title)
                tvStepDesc.text = getString(R.string.wizard_step7_desc)
                btnAction.text = getString(R.string.btn_settings)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
            8 -> {
                tvStepTitle.text = getString(R.string.wizard_step8_title)
                tvStepDesc.text = getString(R.string.wizard_step8_desc)
                btnAction.visibility = View.GONE
            }
        }
    }
}
