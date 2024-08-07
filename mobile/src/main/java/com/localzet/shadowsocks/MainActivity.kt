

package com.localzet.shadowsocks

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.RemoteException
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceDataStore
import com.localzet.shadowsocks.acl.CustomRulesFragment
import com.localzet.shadowsocks.aidl.IShadowsocksService
import com.localzet.shadowsocks.aidl.ShadowsocksConnection
import com.localzet.shadowsocks.aidl.TrafficStats
import com.localzet.shadowsocks.bg.BaseService
import com.localzet.shadowsocks.preference.DataStore
import com.localzet.shadowsocks.preference.OnPreferenceDataStoreChangeListener
import com.localzet.shadowsocks.subscription.SubscriptionFragment
import com.localzet.shadowsocks.utils.Key
import com.localzet.shadowsocks.utils.StartService
import com.localzet.shadowsocks.widget.ListHolderListener
import com.localzet.shadowsocks.widget.ServiceButton
import com.localzet.shadowsocks.widget.StatsBar
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase
import timber.log.Timber

class MainActivity : AppCompatActivity(), ShadowsocksConnection.Callback, OnPreferenceDataStoreChangeListener,
        NavigationView.OnNavigationItemSelectedListener {
    companion object {
        var stateListener: ((BaseService.State) -> Unit)? = null
    }

    // UI
    private lateinit var fab: ServiceButton
    private lateinit var stats: StatsBar
    internal lateinit var drawer: DrawerLayout
    private lateinit var navigation: NavigationView

    lateinit var snackbar: CoordinatorLayout private set
    fun snackbar(text: CharSequence = "") = Snackbar.make(snackbar, text, Snackbar.LENGTH_LONG).apply {
        anchorView = fab
    }

    private val customTabsIntent by lazy {
        CustomTabsIntent.Builder().apply {
            setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, CustomTabColorSchemeParams.Builder().apply {
                setToolbarColor(ContextCompat.getColor(this@MainActivity, R.color.light_color_primary))
            }.build())
            setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, CustomTabColorSchemeParams.Builder().apply {
                setToolbarColor(ContextCompat.getColor(this@MainActivity, R.color.dark_color_primary))
            }.build())
        }.build()
    }
    fun launchUrl(uri: String) = try {
        customTabsIntent.launchUrl(this, uri.toUri())
    } catch (_: ActivityNotFoundException) {
        snackbar(uri).show()
    }

    // service
    var state = BaseService.State.Idle
    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) =
            changeState(state, msg)
    override fun trafficUpdated(profileId: Long, stats: TrafficStats) {
        if (profileId == 0L) this@MainActivity.stats.updateTraffic(
                stats.txRate, stats.rxRate, stats.txTotal, stats.rxTotal)
        if (state != BaseService.State.Stopping) {
            (supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ProfilesFragment)
                    ?.onTrafficUpdated(profileId, stats)
        }
    }
    override fun trafficPersisted(profileId: Long) {
        ProfilesFragment.instance?.onTrafficPersisted(profileId)
    }

    private fun changeState(state: BaseService.State, msg: String? = null, animate: Boolean = true) {
        fab.changeState(state, this.state, animate)
        stats.changeState(state, animate)
        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show()
        this.state = state
        ProfilesFragment.instance?.profilesAdapter?.notifyDataSetChanged()  // refresh button enabled state
        stateListener?.invoke(state)
    }

    private fun toggle() = if (state.canStop) Core.stopService() else connect.launch(null)

    private val connection = ShadowsocksConnection(true)
    override fun onServiceConnected(service: com.localzet.shadowsocks.aidl.IShadowsocksService) = changeState(try {
        BaseService.State.values()[service.state]
    } catch (_: RemoteException) {
        BaseService.State.Idle
    })
    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(StartService()) {
        if (it) snackbar().setText(R.string.vpn_permission_denied).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.layout_main)
        snackbar = findViewById(R.id.snackbar)
        ViewCompat.setOnApplyWindowInsetsListener(snackbar, ListHolderListener)
        stats = findViewById(R.id.stats)
        stats.setOnClickListener { if (state == BaseService.State.Connected) stats.testConnection() }
        drawer = findViewById(R.id.drawer)
        val drawerHandler = object : OnBackPressedCallback(drawer.isOpen), DrawerLayout.DrawerListener {
            override fun handleOnBackPressed() = drawer.closeDrawers()
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) { }
            override fun onDrawerOpened(drawerView: View) {
                isEnabled = true
            }
            override fun onDrawerClosed(drawerView: View) {
                isEnabled = false
            }
            override fun onDrawerStateChanged(newState: Int) {
                isEnabled = newState == DrawerLayout.STATE_IDLE == drawer.isOpen
            }
        }
        onBackPressedDispatcher.addCallback(drawerHandler)
        drawer.addDrawerListener(drawerHandler)
        navigation = findViewById(R.id.navigation)
        navigation.setNavigationItemSelectedListener(this)
        if (savedInstanceState == null) {
            navigation.menu.findItem(R.id.profiles).isChecked = true
            displayFragment(ProfilesFragment())
        }

        fab = findViewById(R.id.fab)
        fab.initProgress(findViewById(R.id.fabProgress))
        fab.setOnClickListener { toggle() }
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom +
                        resources.getDimensionPixelOffset(R.dimen.mtrl_bottomappbar_fab_bottom_margin)
            }
            insets
        }

        changeState(BaseService.State.Idle, animate = false)    // reset everything to init state
        connection.connect(this, this)
        DataStore.publicStore.registerChangeListener(this)
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.serviceMode -> {
                connection.disconnect(this)
                connection.connect(this, this)
            }
        }
    }

    private fun displayFragment(fragment: ToolbarFragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_holder, fragment).commitAllowingStateLoss()
        drawer.closeDrawers()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (item.isChecked) drawer.closeDrawers() else {
            when (item.itemId) {
                R.id.profiles -> {
                    displayFragment(ProfilesFragment())
                    connection.bandwidthTimeout = connection.bandwidthTimeout   // request stats update
                }
                R.id.globalSettings -> displayFragment(GlobalSettingsFragment())
                R.id.about -> {
                    Firebase.analytics.logEvent("about") { }
                    displayFragment(com.localzet.shadowsocks.AboutFragment())
                }
//                R.id.customRules -> displayFragment(CustomRulesFragment())
                R.id.subscriptions -> displayFragment(SubscriptionFragment())
                else -> return false
            }
            item.isChecked = true
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        connection.bandwidthTimeout = 500
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent) = when {
        keyCode == KeyEvent.KEYCODE_G && event.hasModifiers(KeyEvent.META_CTRL_ON) -> {
            toggle()
            true
        }
        keyCode == KeyEvent.KEYCODE_T && event.hasModifiers(KeyEvent.META_CTRL_ON) -> {
            stats.testConnection()
            true
        }
        else -> (supportFragmentManager.findFragmentById(R.id.fragment_holder) as ToolbarFragment).toolbar.menu.let {
            it.setQwertyMode(KeyCharacterMap.load(event.deviceId).keyboardType != KeyCharacterMap.NUMERIC)
            it.performShortcut(keyCode, event, 0)
        }
    }

    override fun onStop() {
        connection.bandwidthTimeout = 0
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        DataStore.publicStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }
}
