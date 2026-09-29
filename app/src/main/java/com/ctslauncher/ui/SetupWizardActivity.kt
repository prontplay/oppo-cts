package com.ctslauncher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    private val totalSteps = 8 // Shizuku・フラグ撤去により全8ステップに短縮

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
                Toast.makeText(this, "Playストアを開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        btnBack.visibility = if (currentStep == 1) View.GONE else View.VISIBLE
        btnNext.text = if (currentStep == totalSteps) "完了" else "次へ"

        when (currentStep) {
            // 1. Googleアプリ
            1 -> {
                tvStepTitle.text = "ステップ 1: Googleアプリのインストール"
                tvStepDesc.text = "かこって検索機能はGoogleアプリに内包されています。\n\nPlayストアから最新の「Google」アプリをインストールまたは更新してください。"
                btnAction.text = "Playストアで開く"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    openGooglePlayStore("com.google.android.googlequicksearchbox")
                }
            }
            // 2. Speech Recognition & Synthesis
            2 -> {
                tvStepTitle.text = "ステップ 2: Speech Recognition & Synthesis の導入"
                tvStepDesc.text = "かこって検索やアシスタントの音声・マルチモーダル処理に必要な「Google 音声認識と合成（Speech Recognition & Synthesis）」をインストールまたは更新してください。"
                btnAction.text = "Playストアで開く"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    openGooglePlayStore("com.google.android.tts")
                }
            }
            // 3. Gemini
            3 -> {
                tvStepTitle.text = "ステップ 3: Geminiのインストール (任意)"
                tvStepDesc.text = "最新のGeminiをアシスタントとして使用したい場合はインストールしてください（スキップして「次へ」進んでもCTSは動作します）。"
                btnAction.text = "Playストアで開く"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    openGooglePlayStore("com.google.android.apps.bard")
                }
            }
            // 4. デフォルトアシスタントの変更
            4 -> {
                tvStepTitle.text = "ステップ 4: デフォルトアシスタントの変更"
                tvStepDesc.text = "かこって検索を利用するには、端末のデジタルアシスタントを「Google」に指定する必要があります。\n\n下のボタンを押してアシスタント設定を開き、デフォルトのアシスタントアプリを「Google」に設定してください。"
                btnAction.text = "アシスタント設定を開く"
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
            // 5. 重ねて表示
            5 -> {
                tvStepTitle.text = "ステップ 5: 重ねて表示（オーバーレイ）の許可"
                tvStepDesc.text = "画面最下部のナビゲーションバー上に透明な長押し判定エリアを常駐させるため、「他のアプリの上に重ねて表示」の権限を許可してください。"
                btnAction.text = "重ねて表示の設定を開く"
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
            // 6. ユーザー補助
            6 -> {
                tvStepTitle.text = "ステップ 6: ユーザー補助の有効化"
                tvStepDesc.text = "ジェスチャーやキー入力を安定して検知させるため、ユーザー補助設定から「CTS Launcher」をONにしてください。"
                btnAction.text = "ユーザー補助設定を開く"
                btnAction.visibility = View.VISIBLE
                btnAction.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            // 7. バックグラウンド
            7 -> {
                tvStepTitle.text = "ステップ 7: バックグラウンド実行の許可"
                tvStepDesc.text = "ColorOSによるタスクキルを防ぎ、常にジェスチャー長押しを有効にするため、電池の最適化を無効化（バックグラウンドでのアクティビティを許可）してください。"
                btnAction.text = "電池設定を開く"
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
            // 8. 初回起動時の注意
            8 -> {
                tvStepTitle.text = "すべての準備が整いました！"
                tvStepDesc.text = "\n⚠️【初回起動時の注意】\n初めてアプリを起動した際、システムから\n「ユーザー補助へのアクセスを付与されています」\nという確認画面が表示される場合があります。\n\nその際は必ず【オンのままにする】を選択してください。（オフにするとジェスチャー検知が停止します）"
                btnAction.visibility = View.GONE
            }
        }
    }
}
