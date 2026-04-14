package com.hstc.quicklogin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hstc.quicklogin.ui.AuthViewModel
import com.hstc.quicklogin.ui.AuthViewModelFactory
import com.hstc.quicklogin.ui.HstcQuickLoginAppScreen
import com.hstc.quicklogin.ui.theme.HstcQuickLoginTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HstcQuickLoginApp
        setContent {
            HstcQuickLoginTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: AuthViewModel = viewModel(
                        factory = AuthViewModelFactory(app.container.authRepository)
                    )
                    HstcQuickLoginAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}
