package com.example.handar

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.example.handar.effect.HandLandmarkerProvider
import com.example.handar.utils.applySystemBarsInsetsMargin
import com.example.handar.utils.matchSystemBarsBottomInsetHeight

class MainActivity : AppCompatActivity() {

    private val destinationsWithMainChrome = setOf(
        R.id.effectListFragment,
        R.id.videoListFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // app luôn dark theme -> ép luôn icon status bar/nav bar màu sáng
        // thay vì để enableEdgeToEdge() tự điều chỉnh.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        val navHost = findViewById<FragmentContainerView>(R.id.nav_host)
        val navController = (supportFragmentManager.findFragmentById(navHost.id) as NavHostFragment)
            .navController
        val bottomNav = findViewById<View>(R.id.bottom_nav)
        bottomNav.applySystemBarsInsetsMargin(bottom = true)

        findViewById<View>(R.id.bottom_system_bar_scrim).matchSystemBarsBottomInsetHeight()

        val topBar = findViewById<View>(R.id.top_bar)
        topBar.applySystemBarsInsetsMargin(top = true)
        val topBarTitle = topBar.findViewById<TextView>(R.id.text_top_bar_title)
        topBar.findViewById<View>(R.id.btn_open_settings).setOnClickListener {
            navController.navigate(R.id.settingsFragment)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val visibility =
                if (destination.id in destinationsWithMainChrome) View.VISIBLE else View.GONE
            bottomNav.visibility = visibility
            topBar.visibility = visibility
            // top bar dùng chung cho cả 2 tab: màn home hiện tên app, collection đổi sang "Collection".
            topBarTitle.setText(
                if (destination.id == R.id.videoListFragment) R.string.nav_collection else R.string.app_name
            )
        }

        setupBottomNavTabs(navController, bottomNav)
    }

    private fun setupBottomNavTabs(navController: NavController, bottomNav: View) {
        val itemHome = bottomNav.findViewById<View>(R.id.item_home)
        val itemCollection = bottomNav.findViewById<View>(R.id.item_collection)
        val iconHome = bottomNav.findViewById<ImageView>(R.id.icon_home)
        val iconCollection = bottomNav.findViewById<ImageView>(R.id.icon_collection)

        fun navigateToTab(destinationId: Int) {
            if (navController.currentDestination?.id == destinationId) return
            navController.navigate(
                destinationId,
                null,
                navOptions {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(R.id.effectListFragment) {
                        saveState = true
                    }
                }
            )
        }

        itemHome.setOnClickListener { navigateToTab(R.id.effectListFragment) }
        itemCollection.setOnClickListener { navigateToTab(R.id.videoListFragment) }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val onEffectList = destination.id == R.id.effectListFragment
            iconHome.setImageResource(if (onEffectList) R.drawable.home_selected else R.drawable.home)
            iconCollection.setImageResource(if (onEffectList) R.drawable.collection else R.drawable.collection_selected)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        HandLandmarkerProvider.release()
    }
}
