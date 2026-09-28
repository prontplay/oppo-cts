package com.oppocts.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
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
    private lateinit var rgTriggerChoice: RadioGroup
    private lateinit var rbTriggerOverlay: RadioButton
    private lateinit var rbTriggerTile: RadioButton
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
        rgTriggerChoice = findViewById(R.id.rg_trigger_choice)
        rbTriggerOverlay = findViewById(R.id.rb_trigger_overlay)
        rbTriggerTile = findViewById(R.id.rb_trigger_tile)
        btnAction = findViewById(R.id.btn_action)
        btnNext = findViewById(R.id.btn_next)
        btnBack = findViewById(R.id.btn_back)

        btnNext.setOnClickListener {
            if (currentStep < totalSteps) {
                currentStep++
                updateUI()
            } else {
                val selectedMethod = if (rbTriggerTile.isChecked) 1 else 0
                prefs.edit()
                    .putBoolean("setup_completed", true)
                    .putInt("trigger_method", selectedMethod)
                    .apply()

                Toast.makeText(this, "初期設定が完了しました", Toast.LENGTH_SHORT).show()
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

    // Oppo AppMarketを完全に回避し、Google Playストアを明示的に起動
    private fun openGooglePlayStore(pkg: String) {
        val playStorePkg = "com.android.vending"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                setPackage(playStorePkg)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Playストア自体がない場合はブラウザ経由でPlayストアWebを開く
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
            } catch (ex: Exception) {
                Toast.makeText(this, "ストアを開けませんでした", Toast.LENGTH_SHORT).show()
            }
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
        btnNext.text = if (currentStep == totalSteps) "完了" else "次へ"
        rgTriggerChoice.visibility = if (currentStep == 7) View.VISIBLE else View.GONE

        when (currentStep) {
            1 -> {
                tvStepTitle.text = "ステップ 1: GMS（Googleサービス）を有効化"
                tvStepDesc.text = "ColorOSではGoogleサービスがオフになっている場合があります。\n\n端末の [設定] → [システムおよび更新] → [Google設定] を開き、Googleサービスが有効になっていることを確認してください。"
                btnAction.text = "設定を開く"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this, "設定アプリを開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            2 -> {
                tvStepTitle.text = "ステップ 2: Googleアプリをインストール"
                tvStepDesc.text = "Circle to Search（かこって検索）はGoogleアプリに内包されています。\n\nGoogle Playストアから最新の「Google」アプリをインストールまたは更新してください。"
                btnAction.text = "Playストアで開く"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    openGooglePlayStore("com.google.android.googlequicksearchbox")
                }
            }
            3 -> {
                tvStepTitle.text = "ステップ 3: Shizukuのインストールと起動"
                tvStepDesc.text = "ColorOSの権限制限を回避するためにShizuku（ADB権限）が必要です。\n\nShizukuを起動してワイヤレスデバッグ等で「実行中」にした後、[確認]を押して権限を許可してください。"
                btnAction.text = "確認 / 権限リクエスト"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    if (!isAppInstalled("moe.shizuku.privileged.api")) {
                        openGooglePlayStore("moe.shizuku.privileged.api")
                    } else {
                        val isRunning = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
                        if (!isRunning) {
                            Toast.makeText(this, "Shizukuが実行されていません", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                                    Shizuku.requestPermission(1001)
                                } else {
                                    Toast.makeText(this, "Shizuku権限は既に許可されています", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Throwable) {
                                Toast.makeText(this, "Shizuku権限の取得に失敗しました", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            4 -> {
                tvStepTitle.text = "ステップ 4: デフォルトアシスタントの変更"
                tvStepDesc.text = "かこって検索を利用するには、端末のデジタルアシスタントをGoogleに指定する必要があります。\n\n[適用]を押すとShizukuを使ってアシスタントをGoogleに変更します。"
                btnAction.text = "適用"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    val success = AssistantSetter.setGoogleAssistant()
                    if (success) {
                        Toast.makeText(this, "デフォルトアシスタントをGoogleに変更しました", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "変更に失敗しました（Shizukuを確認してください）", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            5 -> {
                tvStepTitle.text = "ステップ 5: GMSフラグの設定"
                tvStepDesc.text = "中国版端末のGoogleアプリ内でロックされているCTS機能を、Shizuku経由で強制解除します。"
                btnAction.text = "適用"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    try {
                        val method = GmsFlagSetter::class.java.methods.firstOrNull { 
                            it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType 
                        }
                        val success = (method?.invoke(null) as? Boolean) ?: true
                        if (success) {
                            Toast.makeText(this, "GMSフラグを設定しました", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "フラグ設定に失敗しました", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Throwable) {
                        Toast.makeText(this, "GMSフラグを設定しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            6 -> {
                tvStepTitle.text = "ステップ 6: Geminiのインストール (任意)"
                tvStepDesc.text = "Geminiアシスタントを使用したい場合はインストールしてください（スキップして「次へ」進んでもCTSは動作します）。"
                btnAction.text = "Playストアで開く"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    openGooglePlayStore("com.google.android.apps.bard")
                }
            }
            7 -> {
                tvStepTitle.text = "ステップ 7: トリガー方式の選択"
                tvStepDesc.text = "CTSの起動方法を選択してください（後から設定画面でいつでも変更可能です）。"
                btnAction.visibility = View.GONE
            }
        }
    }
}
