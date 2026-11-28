package com.fatih.stopmotion

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fatih.stopmotion.ui.navigation.AppNavigation
import com.fatih.stopmotion.ui.theme.FatihTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val container = (application as FatihApp).container
        setContent {
            FatihTheme {
                AppNavigation(container)
            }
        }
    }
}
