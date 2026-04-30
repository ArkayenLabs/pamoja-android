package com.pamoja.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.ui.PamojaNavGraph
import com.pamoja.app.ui.Screen
import com.pamoja.app.ui.theme.PamojaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PamojaTheme {
                val navController = rememberNavController()
                val isOnboarded by userPreferences.isOnboarded.collectAsState(initial = false)

                val startDestination = if (isOnboarded) {
                    Screen.Home.route
                } else {
                    Screen.Welcome.route
                }

                PamojaNavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}