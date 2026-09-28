package com.oppocts.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.oppocts.R
import com.oppocts.service.OverlayTriggerService
import com.oppocts.shizuku.AssistantSetter
import com.oppocts.shizuku.GmsFlagSetter
import com.oppocts.shizuku.ShizukuHelper
import com.oppocts.trigger.CTSTrigger
import com.oppocts.util.PackageUtils

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvGoogleStatus: TextView
    private lateinit var tvGmsStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAssistantStatus: TextView
    private lateinit var tvFlagStatus: TextView

    private lateinit var btnTestCts: Button
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnRerunSetup: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnKeyTest: Button
    private lateinit var btnIntentTest: Button
    private lateinit var tvKeyLog: TextView

    private lateinit var spinnerTriggerMethod: Spinner
    private lateinit var switchOverlayDebug: SwitchMaterial
    private lateinit var seekbarOverlayOffset: SeekBar
    private lateinit var seekbarOverlayHeight: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupListeners()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun initViews() {
        tvGoogleStatus = findViewById(R.id.tv_google_status)
        tvGmsStatus = findViewById(R.id.tv_gms_status)
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvAssistantStatus = findViewById(R.id.tv_assistant_status)
        tvFlagStatus = findViewById(R.id.tv_flag_status)

        btnTestCts = findViewById(R.id.btn_test_cts)
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility)
        btnRerunSetup = findViewById(R.id.btn_rerun_setup)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnKeyTest = findViewById(R.id.btn_key_test)
        btnIntentTest = findViewById(R.id.btn_intent_test)
        tvKeyLog = findViewById(R.id.tv_key_log)

        spinnerTriggerMethod = findViewById(R.id.spinner_trigger_method)
        switchOverlayDebug = findViewById(R.id.switch_overlay_debug)
        seekbarOverlayOffset = findViewById(R.id.seekbar_overlay_offset)
        seekbarOverlayHeight = findViewById(R.id.seekbar_overlay_height)

        val triggerOptions = arrayOf("ナビバー長押し (推奨)", "クイック設定タイル", "アプリアイコンタップ")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, triggerOptions)
        spinnerTriggerMethod.adapter = adapter

        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)
        spinnerTriggerMethod.setSelection(prefs.getInt("trigger_method", 0))
        seekbarOverlayHeight.progress = prefs.getInt("trigger_height_px", 70)
        seekbarOverlayOffset.progress = prefs.getInt("trigger_y_offset_px", 0)
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)

        btnRefresh.setOnClickListener { updateStatus() }

        btnTestCts.setOnClickListener {
            val success = CTSTrigger.trigger(this)
            if (success) {
                Toast.makeText(this, "CTSを起動しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "起動失敗。アシスタントとGMS設定を確認してください", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnRerunSetup.setOnClickListener {
            prefs.edit().putBoolean("setup_completed", false).apply()
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
        }

        spinnerTriggerMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("trigger_method", position).apply()
                if (position == 0) {
                    checkAndStartOverlay()
                } else {
                    stopService(Intent(this@SettingsActivity, OverlayTriggerService::class.java))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        seekbarOverlayHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    prefs.edit().putInt("trigger_height_px", Math.max(progress, 30)).apply()
                    restartOverlayService()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarOverlayOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    prefs.edit().putInt("trigger_y_offset_px", progress).apply()
                    restartOverlayService()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun checkAndStartOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "オーバーレイ表示権限を許可してください", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        val intent = Intent(this, OverlayTriggerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun restartOverlayService() {
        stopService(Intent(this, OverlayTriggerService::class.java))
        checkAndStartOverlay()
    }

    private fun updateStatus() {
        // Googleアプリ状態
        val isGoogleInstalled = PackageUtils.isPackageInstalled(this, "com.google.android.googlequicksearchbox")
        tvGoogleStatus.text = "Googleアプリ: " + if (isGoogleInstalled) "インストール済み" else "未インストール"

        // GMS状態
        val isGmsActive = PackageUtils.isPackageInstalled(this, "com.google.android.gms")
        tvGmsStatus.text = "GMS: " + if (isGmsActive) "有効" else "無効/未検出"

        // Shizuku状態
        val isShizukuRunning = ShizukuHelper.isShizukuRunning()
        tvShizukuStatus.text = "Shizuku: " + if (isShizukuRunning) "実行中" else "停止中"

        // ユーザー補助状態
        val isA11yActive = PackageUtils.isAccessibilityServiceEnabled(this, "com.oppocts.service.OppoAccessibilityService")
        tvAccessibilityStatus.text = "ユーザー補助: " + if (isA11yActive) "有効" else "無効"

        // アシスタント状態
        val currentAssistant = Settings.Secure.getString(contentResolver, "voice_interaction_service")
        tvAssistantStatus.text = "デフォルトアシスタント: " + (currentAssistant ?: "未設定")

        tvFlagStatus.text = "GMSフラグ: 設定済み"
    }
}
