package com.pantrypulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pantrypulse.app.ui.AppRoot
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.appViewModel
import com.pantrypulse.app.ui.theme.PantryPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PantryPulseTheme {
                val vm = appViewModel { MainViewModel(it) }
                AppRoot(vm)
            }
        }
    }
}
