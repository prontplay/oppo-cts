package com.ctslauncher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ctslauncher.R
import com.ctslauncher.service.OverlayTriggerService
import com.ctslauncher.shizuku.GmsFlagSetter
import com.ctslauncher.trigger.CTSTrigger
import rikka.shizuku.Shizuku

class SettingsActivity : AppCompatActivity() {

    // メインステータス
    private lateinit var layoutStatusHeader: LinearLayout
    private lateinit var tvMainStatusHeader: TextView
    private lateinit var tvAccordionIndicator: TextView
    private lateinit var layoutStatusDetails: LinearLayout

    private lateinit var tvGoogleStatus: TextView
    private lateinit var btnGoogleStore: Button
    private lateinit var tvSpeechStatus: TextView
    private lateinit var btnSpeechStore: Button
    private lateinit var tvGeminiStatus: TextView
    private lateinit var btnGeminiStore: Button
    private lateinit var tvAssistantStatus: TextView
    private lateinit var btnAssistantSettings: Button
    private lateinit var tvOverlayPermissionStatus: TextView
    private lateinit var btnOverlaySettings: Button
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnAccessibilitySettings: Button
    private lateinit var tvBatteryStatus: TextView
    private lateinit var btnBatterySettings: Button

    // 高度な設定
    private lateinit var layoutAdvancedHeader: LinearLayout
    private lateinit var tvAdvancedAccordionIndicator: TextView
    private lateinit var layoutAdvancedDetails: LinearLayout
    private lateinit var tvShizukuStatus: TextView
    private lateinit var btnShizukuLaunch: Button
    private lateinit var tvFlagStatus: TextView
    private lateinit var btnReapplyFlag: Button

    // トリガー調整・スライダー
    private lateinit var tvHeightLabel: TextView
    private lateinit var tvOffsetLabel: TextView
    private lateinit var tvDelayLabel: TextView
    private lateinit var layoutOverlaySettings: LinearLayout
    private lateinit var layoutTileDesc: LinearLayout
    private lateinit var layoutShortcutDesc: LinearLayout
    private lateinit var layoutDelaySettings: LinearLayout
    private lateinit var seekbarTriggerDelay: SeekBar
    private lateinit var btnTestCts: Button
    private lateinit var btnRerunSetup: Button
    private lateinit var btnRefresh: Button
    private lateinit var spinnerTriggerMethod: Spinner
    private lateinit var switchOverlayDebug: SwitchMaterial
    private lateinit var seekbarOverlayOffset: SeekBar
    private lateinit var seekbarOverlayHeight: SeekBar

    private var userAccordionState: Boolean? = null
    private var isAdvancedExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        initViews()
        setupListeners()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun initViews() {
        // メインステータス
        layoutStatusHeader = findViewById(R.id.layout_status_header)
        tvMainStatusHeader = findViewById(R.id.tv_main_status_header)
        tvAccordionIndicator = findViewById(R.id.tv_accordion_indicator)
        layoutStatusDetails = findViewById(R.id.layout_status_details)

        tvGoogleStatus = findViewById(R.id.tv_google_status)
        btnGoogleStore = findViewById(R.id.btn_google_store)
        tvSpeechStatus = findViewById(R.id.tv_speech_status)
        btnSpeechStore = findViewById(R.id.btn_speech_store)
        tvGeminiStatus = findViewById(R.id.tv_gemini_status)
        btnGeminiStore = findViewById(R.id.btn_gemini_store)
        tvAssistantStatus = findViewById(R.id.tv_assistant_status)
        btnAssistantSettings = findViewById(R.id.btn_assistant_settings)
        tvOverlayPermissionStatus = findViewById(R.id.tv_overlay_permission_status)
        btnOverlaySettings = findViewById(R.id.btn_overlay_settings)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        btnBatterySettings = findViewById(R.id.btn_battery_settings)

        // 高度な設定
        layoutAdvancedHeader = findViewById(R.id.layout_advanced_header)
        tvAdvancedAccordionIndicator = findViewById(R.id.tv_advanced_accordion_indicator)
        layoutAdvancedDetails = findViewById(R.id.layout_advanced_details)
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        btnShizukuLaunch = findViewById(R.id.btn_shizuku_launch)
        tvFlagStatus = findViewById(R.id.tv_flag_status)
        btnReapplyFlag = findViewById(R.id.btn_reapply_flag)

        tvHeightLabel = findViewById(R.id.tv_height_label)
        tvOffsetLabel = findViewById(R.id.tv_offset_label)
        tvDelayLabel = findViewById(R.id.tv_delay_label)

        layoutOverlaySettings = findViewById(R.id.layout_overlay_settings)
        layoutTileDesc = findViewById(R.id.layout_tile_desc)
        layoutShortcutDesc = findViewById(R.id.layout_shortcut_desc)
        layoutDelaySettings = findViewById(R.id.layout_delay_settings)
        seekbarTriggerDelay = findViewById(R.id.seekbar_trigger_delay)

        btnTestCts = findViewById(R.id.btn_test_cts)
        btnRerunSetup = findViewById(R.id.btn_rerun_setup)
        btnRefresh = findViewById(R.id.btn_refresh)

        spinnerTriggerMethod = findViewById(R.id.spinner_trigger_method)
        switchOverlayDebug = findViewById(R.id.switch_overlay_debug)
        seekbarOverlayOffset = findViewById(R.id.seekbar_overlay_offset)
        seekbarOverlayHeight = findViewById(R.id.seekbar_overlay_height)

        val triggerOptions = arrayOf("ジェスチャーバー長押し (推奨)", "クイック設定タイル", "アプリアイコンタップ")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, triggerOptions)
        spinnerTriggerMethod.adapter = adapter

        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)
        val method = prefs.getInt("trigger_method", 0)
        spinnerTriggerMethod.setSelection(method)
        updateTriggerMethodUI(method)

        val currentHeight = prefs.getInt("trigger_height_px", 70)
        seekbarOverlayHeight.progress = currentHeight
        tvHeightLabel.text = "バーの厚み (高さ): ${currentHeight}px"

        val currentOffset = prefs.getInt("trigger_y_offset_px", 0)
        seekbarOverlayOffset.progress = currentOffset + 100
        tvOffsetLabel.text = "トリガー位置の調整: ${currentOffset}px"

        val delayMs = prefs.getInt("trigger_delay_ms", 200)
        seekbarTriggerDelay.progress = (delayMs / 100).coerceIn(0, 10)
        tvDelayLabel.text = "起動遅延時間: ${delayMs}ms"

        switchOverlayDebug.isChecked = prefs.getBoolean("overlay_debug", false)
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)

        layoutStatusHeader.setOnClickListener {
            val nextState = (layoutStatusDetails.visibility != View.VISIBLE)
            userAccordionState = nextState
            toggleStatusAccordion(nextState)
        }

        layoutAdvancedHeader.setOnClickListener {
            isAdvancedExpanded = !isAdvancedExpanded
            layoutAdvancedDetails.visibility = if (isAdvancedExpanded) View.VISIBLE else View.GONE
            tvAdvancedAccordionIndicator.text = if (isAdvancedExpanded) "▲" else "▼"
        }

        btnRefresh.setOnClickListener { updateStatus() }

        btnTestCts.setOnClickListener {
            val success = CTSTrigger.trigger(this)
            if (success) {
                Toast.makeText(this, "CTSを起動しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "起動失敗。アシスタントとGMSフラグを確認してください", Toast.LENGTH_SHORT).show()
            }
        }

        btnRerunSetup.setOnClickListener {
            prefs.edit().putBoolean("setup_completed", false).apply()
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
        }

        btnGoogleStore.setOnClickListener { openGooglePlayStore("com.google.android.googlequicksearchbox") }
        btnSpeechStore.setOnClickListener { openGooglePlayStore("com.google.android.tts") }
        btnGeminiStore.setOnClickListener { openGooglePlayStore("com.google.android.apps.bard") }

        // ② Shizukuボタン: 権限リクエスト・状態確認
        btnShizukuLaunch.setOnClickListener {
            if (!isAppInstalled("moe.shizuku.privileged.api")) {
                openGooglePlayStore("moe.shizuku.privileged.api")
            } else {
                val isRunning = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
                if (!isRunning) {
                    Toast.makeText(this, "Shizukuが実行されていません。Shizukuアプリを起動してください", Toast.LENGTH_SHORT).show()
                    packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
                } else {
                    try {
                        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                            Shizuku.requestPermission(1001)
                        } else {
                            Toast.makeText(this, "Shizuku権限は既に許可されています", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Throwable) {
                        Toast.makeText(this, "Shizuku権限の要求に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnReapplyFlag.setOnClickListener {
            showGmsFlagDialog()
        }

        btnOverlaySettings.setOnClickListener { openOverlayPermission() }
        btnAccessibilitySettings.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        btnAssistantSettings.setOnClickListener {
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

        btnBatterySettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        spinnerTriggerMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("trigger_method", position).apply()
                updateTriggerMethodUI(position)
                if (position == 0) {
                    checkAndStartOverlay()
                } else {
                    stopService(Intent(this@SettingsActivity, OverlayTriggerService::class.java))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        switchOverlayDebug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("overlay_debug", isChecked).apply()
            sendOverlayUpdate()
        }

        seekbarOverlayHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val realHeight = Math.max(progress, 30)
                    tvHeightLabel.text = "バーの厚み (高さ): ${realHeight}px"
                    prefs.edit().putInt("trigger_height_px", realHeight).apply()
                    sendOverlayUpdate()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarOverlayOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val realOffset = progress - 100
                    tvOffsetLabel.text = "トリガー位置の調整: ${realOffset}px"
                    prefs.edit().putInt("trigger_y_offset_px", realOffset).apply()
                    sendOverlayUpdate()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarTriggerDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val ms = progress * 100
                    tvDelayLabel.text = "起動遅延時間: ${ms}ms"
                    prefs.edit().putInt("trigger_delay_ms", ms).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showGmsFlagDialog() {
        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)
        val items = arrayOf(
            "かこって検索 (Circle to Search)",
            "オムニ検索プロバイダ有効化",
            "ColorOSアシスタント制限解除"
        )
        val checkedItems = booleanArrayOf(
            prefs.getBoolean("flag_cts_enabled", true),
            prefs.getBoolean("flag_omni_enabled", true),
            prefs.getBoolean("flag_coloros_bypass", true)
        )

        AlertDialog.Builder(this)
            .setTitle("GMSフラグ設定")
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("適用") { _, _ ->
                prefs.edit()
                    .putBoolean("flag_cts_enabled", checkedItems[0])
                    .putBoolean("flag_omni_enabled", checkedItems[1])
                    .putBoolean("flag_coloros_bypass", checkedItems[2])
                    .apply()

                if (checkedItems[0]) {
                    try {
                        val method = GmsFlagSetter::class.java.methods.firstOrNull { it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType }
                        method?.invoke(null)
                        Toast.makeText(this, "選択したフラグを適用しました", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "フラグを適用しました", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "かこって検索フラグをOFFにしました", Toast.LENGTH_SHORT).show()
                }
                updateStatus()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun toggleStatusAccordion(expand: Boolean) {
        layoutStatusDetails.visibility = if (expand) View.VISIBLE else View.GONE
        tvAccordionIndicator.text = if (expand) "▲" else "▼"
    }

    private fun updateTriggerMethodUI(method: Int) {
        when (method) {
            0 -> {
                layoutOverlaySettings.visibility = View.VISIBLE
                layoutTileDesc.visibility = View.GONE
                layoutShortcutDesc.visibility = View.GONE
                layoutDelaySettings.visibility = View.GONE
                btnTestCts.visibility = View.GONE
            }
            1 -> {
                layoutOverlaySettings.visibility = View.GONE
                layoutTileDesc.visibility = View.VISIBLE
                layoutShortcutDesc.visibility = View.GONE
                layoutDelaySettings.visibility = View.VISIBLE
                btnTestCts.visibility = View.VISIBLE
            }
            2 -> {
                layoutOverlaySettings.visibility = View.GONE
                layoutTileDesc.visibility = View.GONE
                layoutShortcutDesc.visibility = View.VISIBLE
                layoutDelaySettings.visibility = View.VISIBLE
                btnTestCts.visibility = View.VISIBLE
            }
        }
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

    private fun sendOverlayUpdate() {
        val intent = Intent(this, OverlayTriggerService::class.java).apply {
            action = "ACTION_UPDATE_OVERLAY_LAYOUT"
        }
        startService(intent)
    }

    private fun isAppInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isSpeechInstalled(): Boolean {
        val candidates = arrayOf(
            "com.google.android.tts",
            "com.google.android.speech.recognition",
            "com.google.android.googlequicksearchbox"
        )
        return candidates.any { isAppInstalled(it) }
    }

    private fun openGooglePlayStore(pkg: String) {
        val playStorePkg = "com.android.vending"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                setPackage(playStorePkg)
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun updateStatus() {
        var hasError = false
        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)

        // 1. Google アプリ
        val isGoogleInstalled = isAppInstalled("com.google.android.googlequicksearchbox")
        tvGoogleStatus.text = if (isGoogleInstalled) "✅ Google アプリ" else { hasError = true; "❌ Google アプリ" }
        btnGoogleStore.visibility = View.VISIBLE

        // 2. Speech Recognition & Synthesis
        val isSpeech = isSpeechInstalled()
        tvSpeechStatus.text = if (isSpeech) "✅ Google 音声認識と合成" else { hasError = true; "❌ Google 音声認識と合成" }
        btnSpeechStore.visibility = View.VISIBLE

        // 3. Gemini
        val isGeminiInstalled = isAppInstalled("com.google.android.apps.bard")
        tvGeminiStatus.text = if (isGeminiInstalled) "✅ Gemini" else "⚪ Gemini"
        btnGeminiStore.visibility = View.VISIBLE

        // 4. デフォルトアシスタント
        val currentAssistant = Settings.Secure.getString(contentResolver, "voice_interaction_service")
        if (currentAssistant != null && currentAssistant.contains("com.google.android.googlequicksearchbox")) {
            tvAssistantStatus.text = "✅ アシスタント: Google"
        } else {
            hasError = true
            val appName = getAppNameFromComponent(currentAssistant)
            tvAssistantStatus.text = "❌ アシスタント: ${appName ?: "未設定"}"
        }
        btnAssistantSettings.visibility = View.VISIBLE

        // 5. 重ねて表示
        val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        tvOverlayPermissionStatus.text = if (canDraw) "✅ 重ねて表示" else { hasError = true; "❌ 重ねて表示" }
        btnOverlaySettings.visibility = View.VISIBLE

        // 6. ユーザー補助
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val isA11yActive = enabledServices.contains("com.ctslauncher.service.OppoAccessibilityService")
        tvAccessibilityStatus.text = if (isA11yActive) "✅ ユーザー補助" else { hasError = true; "❌ ユーザー補助" }
        btnAccessibilitySettings.visibility = View.VISIBLE

        // 7. バックグラウンド
        tvBatteryStatus.text = "✅ バックグラウンド: 許可"
        btnBatterySettings.visibility = View.VISIBLE

        // --- 高度な設定のステータス ---
        val isShizukuRunning = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        val isShizukuPermitted = if (isShizukuRunning) {
            try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
        } else false

        if (isShizukuPermitted) {
            tvShizukuStatus.text = "✅ Shizuku: 許可済み"
        } else if (isShizukuRunning) {
            tvShizukuStatus.text = "⚠️ Shizuku: 権限未許可"
        } else {
            tvShizukuStatus.text = "❌ Shizuku: 停止中"
        }
        btnShizukuLaunch.visibility = View.VISIBLE

        val isFlagEnabled = prefs.getBoolean("flag_cts_enabled", true)
        tvFlagStatus.text = if (isFlagEnabled) "✅ GMSフラグ" else "❌ GMSフラグ: 無効"
        btnReapplyFlag.visibility = View.VISIBLE
        // ------------------------------

        // メインステータスヘッダー
        if (hasError) {
            tvMainStatusHeader.text = "設定ステータス: ❌"
            if (userAccordionState != false) {
                toggleStatusAccordion(true)
            }
        } else {
            tvMainStatusHeader.text = "設定ステータス: ✅"
            if (userAccordionState == true) {
                toggleStatusAccordion(true)
            } else {
                toggleStatusAccordion(false)
            }
        }
    }

    private fun openOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
    }

    private fun getAppNameFromComponent(componentStr: String?): String? {
        if (componentStr.isNullOrEmpty()) return null
        return try {
            val cn = ComponentName.unflattenFromString(componentStr) ?: return componentStr
            val appInfo = packageManager.getApplicationInfo(cn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            componentStr
        }
    }
}
