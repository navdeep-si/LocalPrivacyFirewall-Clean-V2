package com.localprivacy.firewall

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** A transparent, user-controlled UI. No hidden launcher or deceptive disguise. */
class MainActivity : Activity() {
    private data class AppEntry(
        val packageId: String,
        val label: String,
        val systemApp: Boolean
    )

    private val accent = Color.rgb(24, 73, 111)
    private val muted = Color.rgb(94, 103, 113)
    private lateinit var stateLabel: TextView
    private lateinit var countLabel: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var listContainer: LinearLayout
    private lateinit var searchField: EditText
    private var allApps: List<AppEntry> = emptyList()
    private var showSystem = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadInstalledApps()
        refreshState()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            stateReceiver,
            IntentFilter(FirewallVpnService.ACTION_STATE),
            Context.RECEIVER_NOT_EXPORTED
        )
        refreshState()
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        setContentView(root)
        // Android 15–17 enforce edge-to-edge layouts for modern targets.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(
                dp(18) + bars.left, dp(16) + bars.top,
                dp(18) + bars.right, dp(12) + bars.bottom
            )
            insets
        }

        root.addView(text("Local Privacy Firewall", 25f, accent, true))
        root.addView(text(
            "Block selected applications from using Wi-Fi and mobile data. " +
                "No traffic is sent to any VPN server.", 14f, muted, false
        ).apply { setPadding(0, dp(9), 0, dp(8)) })

        stateLabel = text("Firewall off", 17f, accent, true)
        root.addView(stateLabel)
        countLabel = text("0 selected", 13f, muted, false)
        root.addView(countLabel)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(actions)
        startButton = Button(this).apply {
            text = "START BLOCKING"
            setOnClickListener { requestVpnPermission() }
        }
        actions.addView(startButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        stopButton = Button(this).apply {
            text = "STOP"
            setOnClickListener {
                startService(Intent(this@MainActivity, FirewallVpnService::class.java)
                    .setAction(FirewallVpnService.ACTION_STOP))
                Rules.setDesiredRunning(this@MainActivity, false)
                refreshState()
            }
        }
        actions.addView(stopButton, LinearLayout.LayoutParams(0, dp(52), 0.58f))

        val settings = Button(this).apply {
            text = "ANDROID VPN SETTINGS / ALWAYS-ON"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }
        }
        root.addView(settings, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ))

        root.addView(text(
            "Network blocking does not stop an app's process or erase reports it stores. " +
                "Other system apps could still send their own data. " +
                "Only one Android VPN can be active at a time.",
            12f, muted, false
        ).apply { setPadding(0, dp(8), 0, dp(14)) })

        searchField = EditText(this).apply {
            hint = "Search installed apps or package ID"
            setSingleLine(true)
            textSize = 15f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderApps()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(searchField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ))

        val systemToggle = CheckBox(this).apply {
            text = "Show system applications (advanced — use carefully)"
            textSize = 13f
            setOnCheckedChangeListener { _, checked ->
                showSystem = checked
                renderApps()
            }
        }
        root.addView(systemToggle)

        val scroll = ScrollView(this).apply {
            isFillViewport = false
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(listContainer)
        listContainer.addView(text("Loading installed applications…", 14f, muted, false))
    }

    private fun loadInstalledApps() {
        Thread {
            val installed = try {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } catch (_: Exception) {
                emptyList()
            }
            val apps = installed.asSequence()
                .filter { it.packageName != packageName }
                .map { info ->
                    AppEntry(
                        packageId = info.packageName,
                        label = packageManager.getApplicationLabel(info).toString(),
                        systemApp = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()
            runOnUiThread {
                allApps = apps
                renderApps()
            }
        }.start()
    }

    private fun renderApps() {
        if (!::listContainer.isInitialized || !::searchField.isInitialized) return
        listContainer.removeAllViews()
        val filter = searchField.text.toString().trim().lowercase(Locale.ROOT)
        val visible = allApps.filter { app ->
            (showSystem || !app.systemApp) &&
                (filter.isEmpty() ||
                    app.label.lowercase(Locale.ROOT).contains(filter) ||
                    app.packageId.lowercase(Locale.ROOT).contains(filter))
        }
        if (visible.isEmpty()) {
            listContainer.addView(text("No matching applications.", 14f, muted, false))
            return
        }
        val selected = Rules.selected(this)
        visible.forEach { app ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(8), dp(2), dp(8))
            }

            val icon = ImageView(this).apply {
                try {
                    setImageDrawable(packageManager.getApplicationIcon(app.packageId))
                } catch (_: PackageManager.NameNotFoundException) {
                    setImageResource(android.R.drawable.sym_def_app_icon)
                }
            }
            line.addView(icon, LinearLayout.LayoutParams(dp(35), dp(35)).apply {
                marginEnd = dp(12)
            })
            val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            names.addView(text(app.label, 15f, Color.rgb(35, 45, 55), true))
            names.addView(text(app.packageId, 11f, muted, false))
            line.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val toggle = Switch(this).apply {
                isChecked = selected.contains(app.packageId)
                contentDescription = "Isolate ${app.label}"
                setOnCheckedChangeListener { _, checked ->
                    val updated = Rules.selected(this@MainActivity).toMutableSet()
                    if (checked) updated.add(app.packageId) else updated.remove(app.packageId)
                    Rules.setSelected(this@MainActivity, updated)
                    if (FirewallVpnService.isActive) {
                        startService(Intent(this@MainActivity, FirewallVpnService::class.java)
                            .setAction(FirewallVpnService.ACTION_REFRESH))
                    }
                    refreshState()
                }
            }
            line.addView(toggle)
            listContainer.addView(line)
            val separator = View(this).apply { setBackgroundColor(Color.rgb(235, 239, 242)) }
            listContainer.addView(separator, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ))
        }
    }

    private fun refreshState() {
        if (!::stateLabel.isInitialized) return
        val active = FirewallVpnService.isActive
        val selected = Rules.selected(this).size
        stateLabel.text = if (active) "● ACTIVE — Selected apps offline" else "○ OFF — No apps blocked"
        stateLabel.setTextColor(if (active) Color.rgb(18, 124, 80) else accent)
        val problem = Rules.lastError(this)
        countLabel.text = "$selected app(s) selected" +
            (if (!active && problem.isNotBlank()) "\n$problem" else "")
        startButton.isEnabled = !active && selected > 0
        stopButton.isEnabled = active
    }

    private fun requestVpnPermission() {
        if (Rules.selected(this).isEmpty()) {
            Toast.makeText(this, "Select an app first", Toast.LENGTH_SHORT).show()
            return
        }
        val consent = VpnService.prepare(this)
        if (consent == null) {
            maybeRequestNotificationPermission()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(consent, VPN_REQUEST)
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_REQUEST
            )
        } else {
            startFirewall()
        }
    }

    private fun startFirewall() {
        Rules.setLastError(this, "")
        Rules.setDesiredRunning(this, true)
        startForegroundService(
            Intent(this, FirewallVpnService::class.java)
                .setAction(FirewallVpnService.ACTION_START)
        )
    }

    @Deprecated("Android's onActivityResult contract is sufficient without extra libraries")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) maybeRequestNotificationPermission()
            else Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_REQUEST) {
            // Notification permission is optional; Android still allows a VPN FGS.
            startFirewall()
        }
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val VPN_REQUEST = 1501
        private const val NOTIFICATION_REQUEST = 1502
    }
}
