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

    private lateinit var tvGoogleStatus: TextView
    private lateinit var tvGmsStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAssistantStatus: TextView
    private lateinit var tvFlagStatus: TextView

    private lateinit var tvHeightLabel: TextView
    private lateinit var tvOffsetLabel: TextView

    private lateinit var btnTestCts: Button
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnOpenOverlayPermission: Button
    private lateinit var btnOpenBatterySettings: Button
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

        // ステータスバーのアイコンを視認可能にする（ライトテーマ時は黒、ダークテーマ時は白）
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
        tvGoogleStatus = findViewById(R.id.tv_google_status)
        tvGmsStatus = findViewById(R.id.tv_gms_status)
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvAssistantStatus = findViewById(R.id.tv_assistant_status)
        tvFlagStatus = findViewById(R.id.tv_flag_status)

        tvHeightLabel = findViewById(R.id.tv_height_label)
        tvOffsetLabel = findViewById(R.id.tv_offset_label)

        btnTestCts = findViewById(R.id.btn_test_cts)
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility)
        btnOpenOverlayPermission = findViewById(R.id.btn_open_overlay_permission)
        btnOpenBatterySettings = findViewById(R.id.btn_open_battery_settings)
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

        val currentHeight = prefs.getInt("trigger_height_px", 70)
        seekbarOverlayHeight.progress = currentHeight
        tvHeightLabel.text = "バーの厚み (高さ): ${currentHeight}px"

        // オフセットは -100px〜+100px にマッピング (SeekBar max 200, 初期値 100 = 0px)
        val currentOffset = prefs.getInt("trigger_y_offset_px", 0)
        seekbarOverlayOffset.progress = currentOffset + 100
        tvOffsetLabel.text = "Y軸オフセット (下への突き当て調整): ${currentOffset}px"

        switchOverlayDebug.isChecked = prefs.getBoolean("overlay_debug", false)
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

        btnOpenOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            }
        }

        btnOpenBatterySettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
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
                    val realOffset = progress - 100 // -100px 〜 +100px
                    tvOffsetLabel.text = "Y軸オフセット (下への突き当て調整): ${realOffset}px"
                    prefs.edit().putInt("trigger_y_offset_px", realOffset).apply()
                    sendOverlayUpdate()
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

    private fun updateStatus() {
        // 1. Google アプリ
        val isGoogleInstalled = isAppInstalled("com.google.android.googlequicksearchbox")
        if (isGoogleInstalled) {
            tvGoogleStatus.text = "✅ Google アプリ: インストール済み"
            tvGoogleStatus.setOnClickListener(null)
        } else {
            tvGoogleStatus.text = "❌ Google アプリ: 未インストール (タップしてPlayストアへ)"
            tvGoogleStatus.setOnClickListener { openGooglePlayStore("com.google.android.googlequicksearchbox") }
        }

        // 2. GMS
        val isGmsActive = isAppInstalled("com.google.android.gms")
        if (isGmsActive) {
            tvGmsStatus.text = "✅ GMS: 有効"
            tvGmsStatus.setOnClickListener(null)
        } else {
            tvGmsStatus.text = "❌ GMS: 無効 (タップしてGoogle設定へ)"
            tvGmsStatus.setOnClickListener { openOppoGoogleSettings() }
        }

        // 3. Shizuku
        val isShizukuRunning = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        if (isShizukuRunning) {
            tvShizukuStatus.text = "✅ Shizuku: 実行中"
            tvShizukuStatus.setOnClickListener(null)
        } else {
            tvShizukuStatus.text = "❌ Shizuku: 停止中または未接続 (タップしてShizuku起動)"
            tvShizukuStatus.setOnClickListener {
                if (isAppInstalled("moe.shizuku.privileged.api")) {
                    packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
                } else {
                    openGooglePlayStore("moe.shizuku.privileged.api")
                }
            }
        }

        // 4. ユーザー補助
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val isA11yActive = enabledServices.contains("com.oppocts.service.OppoAccessibilityService")
        if (isA11yActive) {
            tvAccessibilityStatus.text = "✅ ユーザー補助: 有効"
            tvAccessibilityStatus.setOnClickListener(null)
        } else {
            tvAccessibilityStatus.text = "❌ ユーザー補助: 無効 (タップして設定を開く)"
            tvAccessibilityStatus.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        // 5. デフォルトアシスタント
        val currentAssistant = Settings.Secure.getString(contentResolver, "voice_interaction_service")
        if (currentAssistant != null && currentAssistant.contains("com.google.android.googlequicksearchbox")) {
            tvAssistantStatus.text = "✅ アシスタント: Google (設定完了)"
            tvAssistantStatus.setOnClickListener(null)
        } else {
            val appName = getAppNameFromComponent(currentAssistant)
            tvAssistantStatus.text = "❌ アシスタント: ${appName ?: "未設定"} (タップしてGoogleに設定)"
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

        // 6. GMSフラグ
        tvFlagStatus.text = "✅ GMSフラグ: 設定済み (タップして再適用)"
        tvFlagStatus.setOnClickListener {
            try {
                val method = GmsFlagSetter::class.java.methods.firstOrNull { it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType }
                method?.invoke(null)
                Toast.makeText(this, "GMSフラグを再適用しました", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "再適用完了", Toast.LENGTH_SHORT).show()
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
}
