package com.tools.overlay.xiaokui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tools.overlay.xiaokui.ui.theme.MyApplicationTheme

/**
 * 主活动类
 * 负责请求悬浮窗权限并启动悬浮窗服务
 */
class MainActivity : ComponentActivity() {
    
    // 悬浮窗权限请求结果处理器
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 检查是否已获取悬浮窗权限
        if (checkOverlayPermission()) {
            // 已获取权限，启动悬浮窗服务
            startFloatingWindowService()
        } else {
            // 未获取权限，显示提示信息
            showPermissionDeniedDialog()
        }
    }

    /**
     * 活动创建时调用
     * 检查悬浮窗权限，并根据权限状态执行相应操作
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 检查是否已获取悬浮窗权限
        if (checkOverlayPermission()) {
            // 已获取权限，启动悬浮窗服务
            startFloatingWindowService()
        } else {
            // 未获取权限，显示权限请求对话框
            showPermissionRequestDialog()
        }
        
        // 设置UI内容
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "悬浮窗应用",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    /**
     * 检查是否已获取悬浮窗权限
     * @return 是否已获取权限
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0及以上使用Settings.canDrawOverlays方法检查权限
            Settings.canDrawOverlays(this)
        } else {
            // Android 6.0以下默认有权限
            true
        }
    }

    /**
     * 显示权限请求对话框
     * 使用Material Design风格的对话框
     */
    private fun showPermissionRequestDialog() {
        AlertDialog.Builder(this)
            .setTitle("权限请求")  // 对话框标题
            .setMessage("使用说明：\n可以随意拖拽点击可以切换横版竖版样式\n长按悬浮窗1.5秒后退出应用\n\n本应用需要申请悬浮窗权限以运行")  // 对话框内容
            .setPositiveButton("确认") { _, _ ->
                // 点击确认按钮，跳转到系统权限设置页面
                requestOverlayPermission()
            }
            .setNegativeButton("取消") { dialog, _ ->
                // 点击取消按钮，关闭对话框
                dialog.dismiss()
                // 退出应用
                finish()
            }
            .setCancelable(false)  // 不允许点击对话框外部关闭
            .show()  // 显示对话框
    }

    /**
     * 显示权限被拒绝对话框
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage("悬浮窗权限已被拒绝，应用无法正常运行。请前往设置手动开启权限。")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到应用设置页面
                openAppSettings()
            }
            .setNegativeButton("退出") { _, _ ->
                // 退出应用
                finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 请求悬浮窗权限
     * 跳转到系统权限设置页面
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0及以上需要请求悬浮窗权限
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // 使用ActivityResultLauncher启动权限请求
            overlayPermissionLauncher.launch(intent)
        } else {
            // Android 6.0以下默认有权限，直接启动悬浮窗服务
            startFloatingWindowService()
        }
    }

    /**
     * 打开应用设置页面
     */
    private fun openAppSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    /**
     * 启动悬浮窗服务
     */
    private fun startFloatingWindowService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        // Android 8.0及以上需要使用startForegroundService启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // 完成当前活动，关闭主活动
        finish()
    }

    /**
     * 活动恢复时调用
     * 检查权限状态，如果已获取权限则启动悬浮窗服务
     */
    override fun onResume() {
        super.onResume()
        // 检查是否已获取悬浮窗权限
        if (checkOverlayPermission()) {
            // 已获取权限，启动悬浮窗服务
            startFloatingWindowService()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "小葵悬浮助手",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("悬浮窗应用")
    }
}