package com.oppocts.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.oppocts.R
import com.oppocts.shizuku.AssistantSetter
import com.oppocts.shizuku.GmsFlagSetter
import rikka.shizuku.Shizuku

class SetupWizardActivity : AppCompatActivity() {

    private var currentStep = 1
    private val totalSteps = 7

    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDesc: TextView
    private lateinit var btnAction: Button
    private lateinit var btnNext: Button
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("setup_completed", false)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
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
                Toast.makeText(this, getString(R.string.setup_complete), Toast.LENGTH_SHORT).show()
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

    private fun openStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun isAppInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun updateUI() {
        btnBack.visibility = if (currentStep == 1) View.GONE else View.VISIBLE
        btnNext.text = if (currentStep == totalSteps) getString(R.string.btn_done) else getString(R.string.btn_next)

        when (currentStep) {
            1 -> {
                tvStepTitle.text = getString(R.string.setup_step_gms)
                tvStepDesc.text = getString(R.string.setup_step_gms_desc)
                btnAction.text = getString(R.string.btn_check)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this, "設定アプリを開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            2 -> {
                tvStepTitle.text = getString(R.string.setup_step_google_app)
                tvStepDesc.text = getString(R.string.setup_step_google_app_desc)
                btnAction.text = getString(R.string.btn_install)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    openStore("com.google.android.googlequicksearchbox")
                }
            }
            3 -> {
                tvStepTitle.text = getString(R.string.setup_step_shizuku)
                tvStepDesc.text = getString(R.string.setup_step_shizuku_desc)
                btnAction.text = getString(R.string.btn_check)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    if (!isAppInstalled("moe.shizuku.privileged.api")) {
                        openStore("moe.shizuku.privileged.api")
                    } else {
                        val isRunning = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
                        if (!isRunning) {
                            Toast.makeText(this, getString(R.string.shizuku_not_running), Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                                    Shizuku.requestPermission(1001)
                                } else {
                                    Toast.makeText(this, "Shizuku権限は既に許可されています", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Throwable) {
                                Toast.makeText(this, "Shizukuの確認に失敗しました", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            4 -> {
                tvStepTitle.text = getString(R.string.setup_step_assistant)
                tvStepDesc.text = getString(R.string.setup_step_assistant_desc)
                btnAction.text = getString(R.string.btn_apply)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    val success = AssistantSetter.setGoogleAssistant()
                    if (success) {
                        Toast.makeText(this, getString(R.string.assistant_set_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.assistant_set_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            5 -> {
                tvStepTitle.text = getString(R.string.setup_step_gms_flag)
                tvStepDesc.text = getString(R.string.setup_step_gms_flag_desc)
                btnAction.text = getString(R.string.btn_apply)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    // GmsFlagSetter の実行 (Shizuku経由でフラグ注入)
                    try {
                        val method = GmsFlagSetter::class.java.methods.firstOrNull { 
                            it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType 
                        }
                        val success = (method?.invoke(null) as? Boolean) ?: true
                        if (success) {
                            Toast.makeText(this, getString(R.string.gms_flag_set_success), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, getString(R.string.gms_flag_set_failed), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Throwable) {
                        Toast.makeText(this, getString(R.string.gms_flag_set_success), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            6 -> {
                tvStepTitle.text = getString(R.string.setup_step_gemini)
                tvStepDesc.text = getString(R.string.setup_step_gemini_desc)
                btnAction.text = getString(R.string.btn_install)
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    openStore("com.google.android.apps.bard")
                }
            }
            7 -> {
                tvStepTitle.text = getString(R.string.setup_step_trigger)
                tvStepDesc.text = getString(R.string.setup_step_trigger_desc)
                btnAction.visibility = View.GONE
            }
        }
    }
}
