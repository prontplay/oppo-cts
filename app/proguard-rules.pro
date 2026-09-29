# OPPO CTS ProGuard Rules

# Shizuku — 리플렉션 사용하므로 난독화 제외
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# VIS 리플렉션 호출 대상 — 난독화하면 안 됨
-keep class com.android.internal.app.IVoiceInteractionManagerService** { *; }
-keep class android.os.ServiceManager { *; }

# CTSTrigger — 리플렉션으로 시스템 API 호출
-keep class com.oppocts.trigger.CTSTrigger { *; }

# 접근성 서비스 — 시스템이 직접 인스턴스화
-keep class com.oppocts.service.OppoAccessibilityService { *; }

# Quick Settings 타일 — 시스템이 직접 인스턴스화
-keep class com.oppocts.trigger.CTSTileService { *; }
