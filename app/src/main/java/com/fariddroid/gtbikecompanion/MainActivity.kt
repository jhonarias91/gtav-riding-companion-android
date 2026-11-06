package com.fariddroid.gtbikecompanion

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.fariddroid.gtbikecompanion.ui.navigation.CompanionRoot
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so per-app language works below API 33. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CompanionRoot() }
    }
}
