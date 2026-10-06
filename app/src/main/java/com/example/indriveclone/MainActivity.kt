package com.example.indriveclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.indriveclone.ui.AppRoot
import com.example.indriveclone.ui.theme.InDriveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InDriveTheme {
                AppRoot()
            }
        }
    }
}
