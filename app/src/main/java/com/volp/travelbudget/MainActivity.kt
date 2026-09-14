package com.volp.travelbudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.volp.travelbudget.ui.VolpApp
import com.volp.travelbudget.ui.theme.VolpTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolpTheme {
                VolpApp()
            }
        }
    }
}
