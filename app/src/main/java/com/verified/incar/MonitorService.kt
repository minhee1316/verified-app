package com.verified.incar

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** 판정 런타임을 화면과 분리해 앱 전체에서 하나만 둔다. */
class VerifiedApp : Application() {
    val runtime by lazy { VerifiedRuntime(this) }
}

/**
 * 내비 등 다른 앱이 앞에 있어도 판정이 이어지도록 프로세스를 살려 두는 포그라운드 서비스.
 * 일은 하지 않고 런타임을 붙잡기만 한다(신호는 이벤트로만 받아 CPU 부담을 줄인다).
 */
class MonitorService : Service() {
    private lateinit var n: Notification

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "차 상태 확인", NotificationManager.IMPORTANCE_MIN))
        n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mono)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("차 상태를 확인하고 있어요")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        (application as VerifiedApp).runtime
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 내비가 앞에 있어도 위치를 받으려면 위치 유형이 필요하다. 화면이 보일 때 부른 경우에만 붙인다
        // (부팅·재시작 같은 배경에서 붙이면 Android 14+는 권한이 있어도 SecurityException → 특수 용도로 남김)
        if (Build.VERSION.SDK_INT >= 34 && intent?.getBooleanExtra(EXTRA_UI, false) == true &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) runCatching { startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "monitor"
        private const val EXTRA_UI = "ui"

        /** [ui]: 화면이 보이는 상태에서 부를 때 true(위치 유형을 붙일 수 있다). */
        fun start(ctx: Context, ui: Boolean = false) {
            // 폰 시연은 화면 안에서만 실행한다. 부팅 수신·다른 진입점도 같은 정책을 따른다.
            if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
            runCatching { ctx.startForegroundService(Intent(ctx, MonitorService::class.java).putExtra(EXTRA_UI, ui)) }
        }
    }
}

/**
 * 시동이 켜져 차량 화면이 부팅되면 사용자가 앱을 열지 않아도 감시를 시작한다.
 * 앱을 업데이트하면 시스템이 실행 중이던 프로세스를 끝내므로, 업데이트 직후(MY_PACKAGE_REPLACED)에도 같은 방식으로 다시 시작한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ((intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        ) MonitorService.start(context)
    }
}
