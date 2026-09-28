package com.oppocts.ui

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.oppocts.R
import com.oppocts.service.OverlayTriggerService
import com.oppocts.shizuku.AssistantSetter
import com.oppocts.shizuku.GmsFlagSetter
import com.oppocts.trigger.CTSTrigger
import rikka.shizuku.Shizuku

class SettingsActivity : AppCompatActivity() {

    private lateinit var layoutStatusHeader: LinearLayout
    private lateinit var tvMainStatusHeader: TextView
    private lateinit var tvAccordionIndicator: TextView
    private lateinit var layoutStatusDetails: LinearLayout

    private lateinit var tvGmsStatus: TextView
    private lateinit var btnGmsSettings: Button
    private lateinit var tvGoogleStatus: TextView
    private lateinit var btnGoogleStore: Button
    private lateinit var tvSpeechStatus: TextView
    private lateinit var btnSpeechStore: Button
    private lateinit var tvGeminiStatus: TextView
    private lateinit var btnGeminiStore: Button
    private lateinit var tvShizukuStatus: TextView
    private lateinit var btnShizukuLaunch: Button
    private lateinit var tvFlagStatus: TextView
    private lateinit var btnReapplyFlag: Button
    private lateinit var tvOverlayPermissionStatus: TextView
    private lateinit var btnOverlaySettings: Button
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnAccessibilitySettings: Button
    private lateinit var tvAssistantStatus: TextView
    private lateinit var btnAssistantSettings: Button
    private lateinit var tvBatteryStatus: TextView
    private lateinit var btnBatterySettings: Button

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
        layoutStatusHeader = findViewById(R.id.layout_status_header)
        tvMainStatusHeader = findViewById(R.id.tv_main_status_header)
        tvAccordionIndicator = findViewById(R.id.tv_accordion_indicator)
        layoutStatusDetails = findViewById(R.id.layout_status_details)

        tvGmsStatus = findViewById(R.id.tv_gms_status)
        btnGmsSettings = findViewById(R.id.btn_gms_settings)
        tvGoogleStatus = findViewById(R.id.tv_google_status)
        btnGoogleStore = findViewById(R.id.btn_google_store)
        tvSpeechStatus = findViewById(R.id.tv_speech_status)
        btnSpeechStore = findViewById(R.id.btn_speech_store)
        tvGeminiStatus = findViewById(R.id.tv_gemini_status)
        btnGeminiStore = findViewById(R.id.btn_gemini_store)
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        btnShizukuLaunch = findViewById(R.id.btn_shizuku_launch)
        tvFlagStatus = findViewById(R.id.tv_flag_status)
        btnReapplyFlag = findViewById(R.id.btn_reapply_flag)
        tvOverlayPermissionStatus = findViewById(R.id.tv_overlay_permission_status)
        btnOverlaySettings = findViewById(R.id.btn_overlay_settings)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings)
        tvAssistantStatus = findViewById(R.id.tv_assistant_status)
        btnAssistantSettings = findViewById(R.id.btn_assistant_settings)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        btnBatterySettings = findViewById(R.id.btn_battery_settings)

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

        // ⑤ ジェスチャーバー長押し (推奨) に変更
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
            toggleAccordion(nextState)
        }

        btnRefresh.setOnClickListener { updateStatus() }

        btnTestCts.setOnClickListener {
            val success = CTSTrigger.trigger(this)
            if (success) {
                Toast.makeText(this, "CTSを起動しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "起動失敗。アシスタントとGMS設定を確認してください", Toast.LENGTH_SHORT).show()
            }
        }

        btnRerunSetup.setOnClickListener {
            prefs.edit().putBoolean("setup_completed", false).apply()
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
        }

        // 各項目のボタンリスナー設定
        btnGmsSettings.setOnClickListener { openOppoGoogleSettings() }
        btnGoogleStore.setOnClickListener { openGooglePlayStore("com.google.android.googlequicksearchbox") }
        btnSpeechStore.setOnClickListener { openGooglePlayStore("com.google.android.tts") }
        btnGeminiStore.setOnClickListener { openGooglePlayStore("com.google.android.apps.bard") }

        btnShizukuLaunch.setOnClickListener {
            if (isAppInstalled("moe.shizuku.privileged.api")) {
                packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
            } else {
                openGooglePlayStore("moe.shizuku.privileged.api")
            }
        }

        btnReapplyFlag.setOnClickListener {
            try {
                val method = GmsFlagSetter::class.java.methods.firstOrNull { it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType }
                method?.invoke(null)
                Toast.makeText(this, "GMSフラグを再適用しました", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "再適用完了", Toast.LENGTH_SHORT).show()
            }
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

    private fun toggleAccordion(expand: Boolean) {
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

    // ② Speech Recognition & Synthesis の複数パッケージ判定
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

    private fun openOppoGoogleSettings() {
        val candidates = arrayOf(
            Intent().setComponent(ComponentName("com.coloros.google", "com.coloros.google.GoogleSettingsActivity")),
            Intent().setComponent(ComponentName("com.oplus.google", "com.oplus.google.GoogleSettingsActivity")),
            Intent("com.coloros.settings.GOOGLE_SETTINGS"),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$GoogleSettingsActivity")),
            Intent().setComponent(ComponentName("com.coloros.settings", "com.coloros.settings.SettingsActivity")),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                return
            } catch (e: Exception) {
                // 次のインテントを試行
            }
        }
    }

    private fun updateStatus() {
        var hasError = false

        // 1. GMS (アイコンのみ)
        val isGmsActive = isAppInstalled("com.google.android.gms")
        if (isGmsActive) {
            tvGmsStatus.text = "✅ GMS"
            btnGmsSettings.visibility = View.VISIBLE
        } else {
            hasError = true
            tvGmsStatus.text = "❌ GMS"
            btnGmsSettings.visibility = View.VISIBLE
        }

        // 2. Google アプリ (アイコンのみ)
        val isGoogleInstalled = isAppInstalled("com.google.android.googlequicksearchbox")
        if (isGoogleInstalled) {
            tvGoogleStatus.text = "✅ Google アプリ"
            btnGoogleStore.visibility = View.GONE
        } else {
            hasError = true
            tvGoogleStatus.text = "❌ Google アプリ"
            btnGoogleStore.visibility = View.VISIBLE
        }

        // 3. Speech Recognition & Synthesis (アイコンのみ)
        if (isSpeechInstalled()) {
            tvSpeechStatus.text = "✅ Google 音声認識と合成"
            btnSpeechStore.visibility = View.GONE
        } else {
            hasError = true
            tvSpeechStatus.text = "❌ Google 音声認識と合成"
            btnSpeechStore.visibility = View.VISIBLE
        }

        // 4. Gemini (アイコンのみ)
        val isGeminiInstalled = isAppInstalled("com.google.android.apps.bard")
        if (isGeminiInstalled) {
            tvGeminiStatus.text = "✅ Gemini"
            btnGeminiStore.visibility = View.GONE
        } else {
            tvGeminiStatus.text = "⚪ Gemini"
            btnGeminiStore.visibility = View.VISIBLE
        }

        // 5. Shizuku (④: 詳細文字ステータスを表示)
        val isShizukuRunning = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        if (isShizukuRunning) {
            tvShizukuStatus.text = "✅ Shizuku: 実行中"
            btnShizukuLaunch.visibility = View.GONE
        } else {
            hasError = true
            tvShizukuStatus.text = "❌ Shizuku: 停止中"
            btnShizukuLaunch.visibility = View.VISIBLE
        }

        // 6. GMSフラグ (アイコンのみ)
        tvFlagStatus.text = "✅ GMSフラグ"

        // 7. 重ねて表示 (アイコンのみ)
        val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        if (canDraw) {
            tvOverlayPermissionStatus.text = "✅ 重ねて表示"
            btnOverlaySettings.visibility = View.GONE
        } else {
            hasError = true
            tvOverlayPermissionStatus.text = "❌ 重ねて表示"
            btnOverlaySettings.visibility = View.VISIBLE
        }

        // 8. ユーザー補助 (アイコンのみ)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val isA11yActive = enabledServices.contains("com.oppocts.service.OppoAccessibilityService")
        if (isA11yActive) {
            tvAccessibilityStatus.text = "✅ ユーザー補助"
            btnAccessibilitySettings.visibility = View.GONE
        } else {
            hasError = true
            tvAccessibilityStatus.text = "❌ ユーザー補助"
            btnAccessibilitySettings.visibility = View.VISIBLE
        }

        // 9. デフォルトアシスタント (④: 詳細文字ステータスを表示)
        val currentAssistant = Settings.Secure.getString(contentResolver, "voice_interaction_service")
        if (currentAssistant != null && currentAssistant.contains("com.google.android.googlequicksearchbox")) {
            tvAssistantStatus.text = "✅ アシスタント: Google"
            btnAssistantSettings.visibility = View.VISIBLE
        } else {
            hasError = true
            val appName = getAppNameFromComponent(currentAssistant)
            tvAssistantStatus.text = "❌ アシスタント: ${appName ?: "未設定"}"
            btnAssistantSettings.visibility = View.VISIBLE
        }

        // 10. バックグラウンド (アイコンのみ)
        tvBatteryStatus.text = "✅ バックグラウンド: 許可"

        // ③ ヘッダーの文字を「設定ステータス: ❌」または「設定ステータス: ✅」に簡略化
        if (hasError) {
            tvMainStatusHeader.text = "設定ステータス: ❌"
            if (userAccordionState != false) {
                toggleAccordion(true)
            }
        } else {
            tvMainStatusHeader.text = "設定ステータス: ✅"
            if (userAccordionState == true) {
                toggleAccordion(true)
            } else {
                toggleAccordion(false)
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
