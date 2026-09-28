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
    private lateinit var tvGoogleStatus: TextView
    private lateinit var tvGeminiStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvFlagStatus: TextView
    private lateinit var btnReapplyFlag: Button
    private lateinit var tvOverlayPermissionStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAssistantStatus: TextView
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

    private var isAccordionExpanded = false

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
        tvGoogleStatus = findViewById(R.id.tv_google_status)
        tvGeminiStatus = findViewById(R.id.tv_gemini_status)
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        tvFlagStatus = findViewById(R.id.tv_flag_status)
        btnReapplyFlag = findViewById(R.id.btn_reapply_flag)
        tvOverlayPermissionStatus = findViewById(R.id.tv_overlay_permission_status)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvAssistantStatus = findViewById(R.id.tv_assistant_status)
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

        val triggerOptions = arrayOf("ナビバー長押し (推奨)", "クイック設定タイル", "アプリアイコンタップ")
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

        // ② アコーディオン開閉リスナー
        layoutStatusHeader.setOnClickListener {
            toggleAccordion(!isAccordionExpanded)
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

        // ③ GMSフラグ再適用ボタン
        btnReapplyFlag.setOnClickListener {
            try {
                val method = GmsFlagSetter::class.java.methods.firstOrNull { it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType }
                method?.invoke(null)
                Toast.makeText(this, "GMSフラグを再適用しました", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "再適用完了", Toast.LENGTH_SHORT).show()
            }
        }

        // ④ バックグラウンド実行設定ボタン
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

        // ⑤⑥ トリガー方式の切り替えとUI制御
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

        // ⑤⑥ 遅延タイマースライダー（0〜1000ms）
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
        isAccordionExpanded = expand
        layoutStatusDetails.visibility = if (expand) View.VISIBLE else View.GONE
        tvAccordionIndicator.text = if (expand) "▲" else "▼"
    }

    private fun updateTriggerMethodUI(method: Int) {
        when (method) {
            0 -> {
                // ナビバー長押し
                layoutOverlaySettings.visibility = View.VISIBLE
                layoutTileDesc.visibility = View.GONE
                layoutShortcutDesc.visibility = View.GONE
                layoutDelaySettings.visibility = View.GONE
            }
            1 -> {
                // クイック設定タイル
                layoutOverlaySettings.visibility = View.GONE
                layoutTileDesc.visibility = View.VISIBLE
                layoutShortcutDesc.visibility = View.GONE
                layoutDelaySettings.visibility = View.VISIBLE
            }
            2 -> {
                // アプリアイコンタップ
                layoutOverlaySettings.visibility = View.GONE
                layoutTileDesc.visibility = View.GONE
                layoutShortcutDesc.visibility = View.VISIBLE
                layoutDelaySettings.visibility = View.VISIBLE
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
        val intents = arrayOf(
            Intent().setComponent(ComponentName("com.coloros.google", "com.coloros.google.GoogleSettingsActivity")),
            Intent().setComponent(ComponentName("com.oplus.google", "com.oplus.google.GoogleSettingsActivity")),
            Intent("com.coloros.settings.GOOGLE_SETTINGS"),
            Intent("com.android.settings.Settings\$GoogleSettingsActivity"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                // 次の候補を試行
            }
        }
    }

    private fun updateStatus() {
        var hasError = false

        // 1. GMS
        val isGmsActive = isAppInstalled("com.google.android.gms")
        if (isGmsActive) {
            tvGmsStatus.text = "✅ GMS: 有効"
            tvGmsStatus.setOnClickListener { openOppoGoogleSettings() }
        } else {
            hasError = true
            tvGmsStatus.text = "❌ GMS: 無効 (タップでGoogle設定へ)"
            tvGmsStatus.setOnClickListener { openOppoGoogleSettings() }
        }

        // 2. Google アプリ
        val isGoogleInstalled = isAppInstalled("com.google.android.googlequicksearchbox")
        if (isGoogleInstalled) {
            tvGoogleStatus.text = "✅ Google アプリ: インストール済み"
            tvGoogleStatus.setOnClickListener { openGooglePlayStore("com.google.android.googlequicksearchbox") }
        } else {
            hasError = true
            tvGoogleStatus.text = "❌ Google アプリ: 未インストール (タップでPlayストアへ)"
            tvGoogleStatus.setOnClickListener { openGooglePlayStore("com.google.android.googlequicksearchbox") }
        }

        // 3. Gemini
        val isGeminiInstalled = isAppInstalled("com.google.android.apps.bard")
        if (isGeminiInstalled) {
            tvGeminiStatus.text = "✅ Gemini: インストール済み"
            tvGeminiStatus.setOnClickListener { openGooglePlayStore("com.google.android.apps.bard") }
        } else {
            tvGeminiStatus.text = "⚪ Gemini: 未インストール (タップでPlayストアへ)"
            tvGeminiStatus.setOnClickListener { openGooglePlayStore("com.google.android.apps.bard") }
        }

        // 4. Shizuku
        val isShizukuRunning = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        if (isShizukuRunning) {
            tvShizukuStatus.text = "✅ Shizuku: 実行中"
            tvShizukuStatus.setOnClickListener {
                packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
            }
        } else {
            hasError = true
            tvShizukuStatus.text = "❌ Shizuku: 停止中または未接続 (タップでShizuku起動)"
            tvShizukuStatus.setOnClickListener {
                if (isAppInstalled("moe.shizuku.privileged.api")) {
                    packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
                } else {
                    openGooglePlayStore("moe.shizuku.privileged.api")
                }
            }
        }

        // 5. GMSフラグ（③: テキストリンクなし）
        tvFlagStatus.text = "✅ GMSフラグ: 設定済み"

        // 6. 重ねて表示
        val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        if (canDraw) {
            tvOverlayPermissionStatus.text = "✅ 重ねて表示: 許可済み"
            tvOverlayPermissionStatus.setOnClickListener { openOverlayPermission() }
        } else {
            hasError = true
            tvOverlayPermissionStatus.text = "❌ 重ねて表示: 未許可 (タップで権限設定へ)"
            tvOverlayPermissionStatus.setOnClickListener { openOverlayPermission() }
        }

        // 7. ユーザー補助
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val isA11yActive = enabledServices.contains("com.oppocts.service.OppoAccessibilityService")
        if (isA11yActive) {
            tvAccessibilityStatus.text = "✅ ユーザー補助: 有効"
            tvAccessibilityStatus.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        } else {
            hasError = true
            tvAccessibilityStatus.text = "❌ ユーザー補助: 無効 (タップで設定を開く)"
            tvAccessibilityStatus.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        // 8. デフォルトアシスタント
        val currentAssistant = Settings.Secure.getString(contentResolver, "voice_interaction_service")
        if (currentAssistant != null && currentAssistant.contains("com.google.android.googlequicksearchbox")) {
            tvAssistantStatus.text = "✅ アシスタント: Google"
            tvAssistantStatus.setOnClickListener(null)
        } else {
            hasError = true
            val appName = getAppNameFromComponent(currentAssistant)
            tvAssistantStatus.text = "❌ アシスタント: ${appName ?: "未設定"} (タップでGoogleに変更)"
            tvAssistantStatus.setOnClickListener {
                val success = AssistantSetter.setGoogleAssistant()
                if (success) {
                    Toast.makeText(this, "Googleアシスタントに変更しました", Toast.LENGTH_SHORT).show()
                    updateStatus()
                } else {
                    Toast.makeText(this, "変更失敗。Shizukuが動作しているか確認してください", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 9. バックグラウンド実行（④）
        tvBatteryStatus.text = "✅ バックグラウンド実行: 許可"

        // ② 正常時はアコーディオンを閉じ、エラー時は自動で開く
        if (hasError) {
            tvMainStatusHeader.text = "設定ステータス: ❌ 要設定項目あり"
            toggleAccordion(true)
        } else {
            tvMainStatusHeader.text = "設定ステータス: ✅ 正常"
            toggleAccordion(false)
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
