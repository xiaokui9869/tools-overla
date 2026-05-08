package com.tools.overlay.xiaokui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.graphics.Point
import com.tools.overlay.xiaokui.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 悬浮窗服务类
 * 负责创建和管理悬浮窗，显示实时时间和电量信息
 */
class FloatingWindowService : Service() {

    // 窗口管理器，用于添加和移除悬浮窗
    private lateinit var windowManager: WindowManager
    // 悬浮窗视图
    private lateinit var floatingView: View
    // 时间显示的TextView
    private lateinit var tvTime: TextView
    // 电量显示的TextView
    private lateinit var tvBattery: TextView
    // 用于更新时间的Handler
    private val timeHandler = Handler(Looper.getMainLooper())
    // 用于更新时间的Runnable
    private val timeRunnable = object : Runnable {
        override fun run() {
            updateTime()  // 更新时间显示
            timeHandler.postDelayed(this, 1000)  // 每秒更新一次
        }
    }
    // 长按检测相关变量
    private var isLongPress = false  // 是否正在长按
    private val longPressDuration = 1500  // 长按时间阈值（毫秒），修改为1.5秒
    private val longPressHintDuration = 500  // 长按提示时间阈值（毫秒），0.5秒时震动
    private val longPressHandler = Handler(Looper.getMainLooper())
    // 长按检测的Runnable
    private val longPressRunnable = Runnable {
        isLongPress = true
        // 长按1.5秒后，关闭悬浮窗并退出应用
        closeFloatingWindowAndExit()
    }
    // 长按提示的Runnable
    private val longPressHintRunnable = Runnable {
        // 长按0.5秒时，震动
        vibratePhone()
    }
    // 触摸事件的初始位置
    private var initialX = 0
    private var initialY = 0
    // 触摸事件的初始时间
    private var initialTouchTime = 0L
    // 窗口初始位置
    private var initialWindowX = 0
    private var initialWindowY = 0
    // 窗口参数
    private var windowParams: WindowManager.LayoutParams? = null
    // 电量变化广播接收器
    private var batteryReceiver: BroadcastReceiver? = null
    // 布局方向：true为横版，false为竖版
    private var isHorizontalLayout = true
    // SharedPreferences用于保存布局状态
    private lateinit var sharedPreferences: SharedPreferences

    /**
     * 服务创建时调用
     * 初始化悬浮窗并开始显示
     */
    override fun onCreate() {
        super.onCreate()

        // 创建前台服务通知（Android 8.0+需要）
        createNotificationChannel()
        startForeground(1, createNotification())

        // 初始化窗口管理器
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences("FloatingWindowPrefs", Context.MODE_PRIVATE)
        // 读取保存的布局状态，默认为横版
        isHorizontalLayout = sharedPreferences.getBoolean("isHorizontalLayout", true)

        // 加载悬浮窗布局
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window_layout, null)

        // 获取时间显示的TextView
        tvTime = floatingView.findViewById(R.id.tv_time)
        // 获取电量显示的TextView
        tvBattery = floatingView.findViewById(R.id.tv_battery)

        // 应用布局方向
        applyLayoutOrientation()

        // 设置悬浮窗的触摸事件监听器
        floatingView.setOnTouchListener { view, event ->
            handleTouchEvent(view, event)
        }

        // 添加悬浮窗到窗口
        addFloatingWindow()

        // 开始更新时间和电量
        startUpdatingTimeAndBattery()
    }

    /**
     * 创建通知渠道（Android 8.0+需要）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "floating_window_channel"
            val channelName = "悬浮窗服务"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     * @return 通知对象
     */
    private fun createNotification(): Notification {
        val channelId = "floating_window_channel"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("悬浮窗服务")
                .setContentText("正在显示悬浮窗")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("悬浮窗服务")
                .setContentText("正在显示悬浮窗")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        }
    }

    /**
     * 添加悬浮窗到窗口
     */
    private fun addFloatingWindow() {
        // 设置悬浮窗的布局参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,  // 宽度为内容自适应
            WindowManager.LayoutParams.WRAP_CONTENT,  // 高度为内容自适应
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY  // Android 8.0+使用此类型
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE  // Android 8.0以下使用此类型
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  // 不获取焦点，不影响其他应用操作
            PixelFormat.TRANSLUCENT  // 支持透明背景
        )

        // 设置悬浮窗的位置（屏幕左上角）
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100  // 距离左边的距离
        params.y = 100  // 距离顶部的距离

        // 保存窗口参数
        windowParams = params
        
        // 初始化悬浮窗大小
        updateFloatingWindowSize()
        
        // 添加悬浮窗到窗口
        windowManager.addView(floatingView, params)
    }

    /**
     * 处理悬浮窗的触摸事件
     * @param view 触摸的视图
     * @param event 触摸事件
     * @return 是否消费了事件
     */
    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 手指按下时，记录初始位置和时间
                initialX = event.rawX.toInt()
                initialY = event.rawY.toInt()
                initialTouchTime = System.currentTimeMillis()
                isLongPress = false
                
                // 记录窗口初始位置
                windowParams?.let { params ->
                    initialWindowX = params.x
                    initialWindowY = params.y
                }

                // 启动长按提示检测（0.5秒后震动）
                longPressHandler.postDelayed(longPressHintRunnable, longPressHintDuration.toLong())
                // 启动长按检测（1.5秒后关闭应用）
                longPressHandler.postDelayed(longPressRunnable, longPressDuration.toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // 手指移动时，如果移动距离过大，取消长按检测
                val currentX = event.rawX.toInt()
                val currentY = event.rawY.toInt()
                val dx = Math.abs(currentX - initialX)
                val dy = Math.abs(currentY - initialY)

                if (dx > 30 || dy > 30) {
                    // 移动距离超过30像素，取消长按检测和提示
                    longPressHandler.removeCallbacks(longPressRunnable)
                    longPressHandler.removeCallbacks(longPressHintRunnable)
                    // 停止震动
                    stopVibration()
                    
                    // 更新窗口位置
                    windowParams?.let { params ->
                        // 计算新的位置（X轴和Y轴都使用相同的计算方式）
                        params.x = initialWindowX + (currentX - initialX)
                        params.y = initialWindowY + (currentY - initialY)
                        // 更新窗口位置
                        windowManager.updateViewLayout(floatingView, params)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                // 手指抬起时，取消长按检测和提示
                longPressHandler.removeCallbacks(longPressRunnable)
                longPressHandler.removeCallbacks(longPressHintRunnable)
                // 停止震动
                stopVibration()

                // 如果不是长按，处理点击事件
                if (!isLongPress) {
                    // 检查是否是点击事件（移动距离很小）
                    val currentX = event.rawX.toInt()
                    val currentY = event.rawY.toInt()
                    val dx = Math.abs(currentX - initialX)
                    val dy = Math.abs(currentY - initialY)
                    
                    if (dx < 30 && dy < 30) {
                        // 是点击事件，切换横竖版
                        toggleLayoutOrientation()
                    }
                }
                return true
            }
        }
        return false
    }

    /**
     * 开始更新时间和电量
     */
    private fun startUpdatingTimeAndBattery() {
        // 立即更新一次
        updateTime()
        updateBattery()

        // 每秒更新一次时间
        timeHandler.post(timeRunnable)

        // 注册电量变化广播接收器
        registerBatteryReceiver()
    }

    /**
     * 更新时间显示
     */
    private fun updateTime() {
        // 获取当前时间
        val currentTime = Date()
        // 创建时间格式化器
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        // 格式化时间
        val timeString = dateFormat.format(currentTime)
        // 更新时间显示
        tvTime.text = "🕛 $timeString"
    }

    /**
     * 更新电量显示
     */
    private fun updateBattery() {
        // 获取电池状态
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }

        // 获取电量级别（0-100）
        val batteryLevel: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        // 获取电池电量比例（0-1）
        val batteryScale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        // 计算电量百分比
        val batteryPercent: Float = if (batteryLevel >= 0 && batteryScale > 0) {
            batteryLevel * 100 / batteryScale.toFloat()
        } else {
            50f  // 默认50%
        }

        // 更新电量显示
        tvBattery.text = "🔋 ${batteryPercent.toInt()}%"

        // 根据电量设置颜色
        when {
            batteryPercent > 60 -> {
                // 电量大于60%，显示绿色
                tvBattery.setTextColor(0xFF4CAF50.toInt())
            }
            batteryPercent >= 20 -> {
                // 电量在20%-60%之间，显示黄色
                tvBattery.setTextColor(0xFFFFEB3B.toInt())
            }
            else -> {
                // 电量小于20%，显示红色
                tvBattery.setTextColor(0xFFF44336.toInt())
            }
        }
    }

    /**
     * 注册电量变化广播接收器
     */
    private fun registerBatteryReceiver() {
        // 创建电量变化广播接收器
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    // 电量变化时，更新电量显示
                    updateBattery()
                }
            }
        }

        // 注册广播接收器
        batteryReceiver?.let {
            registerReceiver(it, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
    }

    /**
     * 关闭悬浮窗并退出应用
     */
    private fun closeFloatingWindowAndExit() {
        try {
            // 移除悬浮窗
            windowManager.removeView(floatingView)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 停止服务
        stopSelf()

        // 退出应用
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * 服务销毁时调用
     * 清理资源
     */
    override fun onDestroy() {
        super.onDestroy()

        // 停止震动
        stopVibration()

        // 移除所有回调和消息
        timeHandler.removeCallbacks(timeRunnable)
        longPressHandler.removeCallbacks(longPressRunnable)
        longPressHandler.removeCallbacks(longPressHintRunnable)

        // 取消注册广播接收器
        try {
            batteryReceiver?.let {
                unregisterReceiver(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // 移除悬浮窗
            windowManager.removeView(floatingView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    /**
     * 手机震动
     * 震动幅度为10%
     */
    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上使用VibrationEffect
                // 震动幅度为10% (amplitude = 25, 范围是1-255)
                // 持续震动，直到手指抬起
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 1000), 0)
                vibrator.vibrate(effect)
            } else {
                // Android 8.0以下使用旧方法
                // 持续震动，直到手指抬起
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 1000), 0)
            }
        }
    }

    /**
     * 停止手机震动
     */
    private fun stopVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.cancel()
    }

    /**
     * 切换布局方向
     */
    private fun toggleLayoutOrientation() {
        // 切换布局方向
        isHorizontalLayout = !isHorizontalLayout
        // 保存布局状态
        saveLayoutOrientation()
        // 应用新的布局方向
        applyLayoutOrientation()
    }

    /**
     * 保存布局方向到SharedPreferences
     */
    private fun saveLayoutOrientation() {
        sharedPreferences.edit().putBoolean("isHorizontalLayout", isHorizontalLayout).apply()
    }

    /**
     * 应用布局方向
     */
    private fun applyLayoutOrientation() {
        val linearLayout = floatingView as? android.widget.LinearLayout ?: return
        
        if (isHorizontalLayout) {
            // 1. 设置父容器为横向排列
            linearLayout.orientation = android.widget.LinearLayout.HORIZONTAL
            linearLayout.gravity = android.view.Gravity.CENTER_VERTICAL // 关键：确保子项垂直居中

// 2. 设置统一的内边距（左右 12dp，上下 8dp 让它看起来更像胶囊）
            val paddingSide = (8 * resources.displayMetrics.density).toInt()
            val paddingTopBottom = (6 * resources.displayMetrics.density).toInt()
            linearLayout.setPadding(paddingSide, paddingTopBottom, paddingSide, paddingTopBottom)

// 3. 统一字体大小（确保视觉高度一致）
            val uniformTextSize = 12f
            tvTime.textSize = uniformTextSize
            tvBattery.textSize = uniformTextSize

            // 核心修改 3：移除字体默认留白
// 这样可以精确控制高度，不会让背景框显得臃肿
            tvTime.includeFontPadding = false
            tvBattery.includeFontPadding = false

// 4. 配置时间 TextView 布局（移除 MATCH_PARENT，改用 WRAP_CONTENT）
            val layoutParamsTime = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParamsTime.gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParamsTime.rightMargin = (6 * resources.displayMetrics.density).toInt() // 与电量的间距
            tvTime.layoutParams = layoutParamsTime

// 5. 配置电量 TextView 布局
            val layoutParamsBattery = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParamsBattery.gravity = android.view.Gravity.CENTER_VERTICAL
            tvBattery.layoutParams = layoutParamsBattery
        } else {
            // 1. 设置父容器：垂直排列并强制居中
            linearLayout.orientation = android.widget.LinearLayout.VERTICAL
            linearLayout.gravity = android.view.Gravity.CENTER

            // 核心修改 1：极致压缩内边距
            // 竖版为了消除“下巴”和“额头”，上下 padding 必须为 0，左右仅保留极窄缝隙
            val pxSide = (2 * resources.displayMetrics.density).toInt()
            linearLayout.setPadding(pxSide, 0, pxSide, 0)

            // 2. 统一字体大小
            // 图一看起来文字非常精致，建议维持在 9f - 10f 之间
            val uniformSize = 12f
            tvTime.textSize = uniformSize
            tvBattery.textSize = uniformSize

            // 核心修改 2：彻底移除字体自带的内边距（这是消除留白的最关键一步）
            tvTime.includeFontPadding = false
            tvBattery.includeFontPadding = false

            // 核心修改 3：极限压缩行高
            // 将行间距倍数设为 0.85，让文字进一步向中心靠拢
            tvTime.setLineSpacing(0f, 0.85f)
            tvBattery.setLineSpacing(0f, 0.85f)

            // 3. 配置时间布局 (上方元素)
            val lpTime = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lpTime.gravity = android.view.Gravity.CENTER_HORIZONTAL

            // 核心修改 4：加大负 MarginBottom
            // 设置负边距让下方的电量文字直接“侵入”时间文字的底部空白区
            // 根据图一感官，建议设为 -4dp 到 -5dp
            lpTime.bottomMargin = -(4 * resources.displayMetrics.density).toInt()

            tvTime.layoutParams = lpTime
            tvTime.gravity = android.view.Gravity.CENTER

            // 4. 配置电量布局 (下方元素)
            val lpBattery = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lpBattery.gravity = android.view.Gravity.CENTER_HORIZONTAL

            // 额外修正：确保电量文字顶部没有多余 margin
            lpBattery.topMargin = 0

            tvBattery.layoutParams = lpBattery
            tvBattery.gravity = android.view.Gravity.CENTER
        }
        
        // 更新悬浮窗大小
        updateFloatingWindowSize()
    }
    
    /**
     * 更新悬浮窗大小
     */
    private fun updateFloatingWindowSize() {
        windowParams?.let { params ->
            // 获取屏幕尺寸
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getSize(point)
            val screenWidth = point.x
            val screenHeight = point.y
            
            // 获取屏幕密度，用于计算合适的dp值
            val density = resources.displayMetrics.density
            
            // 先让内容测量自己的大小
            floatingView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val contentWidth = floatingView.measuredWidth
            val contentHeight = floatingView.measuredHeight
            
            // 计算紧凑内边距（8dp转换为像素）
            val compactPadding = (8 * density).toInt()

            if (isHorizontalLayout) {
                // 宽度由原来的 0.13 缩减到 0.11 左右
                // 高度由原来的 0.05 缩减到 0.045
                val targetWidth = (screenWidth * 0.11).toInt()
                val targetHeight = (screenHeight * 0.045).toInt()

                // 这里的 padding 也要同步改小，防止 Math.max 强制撑大
                val tightPadding = (4 * density).toInt()
                params.width = Math.max(contentWidth + tightPadding * 2, targetWidth)
                params.height = Math.max(contentHeight + tightPadding * 2, targetHeight)

            } else {
                // 核心修改 1：调整比例系数
                // 宽度设为屏幕宽度的 10% 左右（稍微宽一点，给图标留空间）
                // 高度设为屏幕高度的 4.5% - 5% 左右（大幅度压扁，消除上下“下巴”）
                val targetWidth = (screenWidth * 0.10).toInt()
                val targetHeight = (screenHeight * 0.05).toInt()

                // 核心修改 2：减少内边距对容器大小的干预
                // 竖版不需要太大的 compactPadding，否则 Math.max 会被撑大
                val tightPadding = (2 * density).toInt()

                params.width = Math.max(contentWidth + tightPadding * 2, targetWidth)
                params.height = Math.max(contentHeight + tightPadding * 2, targetHeight)
            }
            
            // 只有在悬浮窗已经添加到窗口管理器时才更新布局
            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (e: Exception) {
                // 悬浮窗还未添加，忽略异常
            }
        }
    }

    /**
     * 绑定服务时调用
     * @param intent 意图
     * @return IBinder对象（本服务不提供绑定功能，返回null）
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
