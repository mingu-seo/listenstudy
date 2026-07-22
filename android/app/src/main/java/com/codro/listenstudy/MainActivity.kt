package com.codro.listenstudy

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.codro.listenstudy.ui.PlayerScreen
import com.codro.listenstudy.ui.PlayerViewModel
import com.codro.listenstudy.ui.theme.ListenStudyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            // Supporter-only sepia option; false for everyone else, so the standard themes are
            // untouched and no core feature depends on the supporter state.
            val sepiaTheme by viewModel.sepiaThemeActive.collectAsState()
            ListenStudyTheme(sepiaTheme = sepiaTheme) {
                PlayerScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Foreground restoration: connect (first time) and re-query Play ownership so purchases,
        // pending completions, and refunds made outside the app are reflected.
        viewModel.refreshSupporter()
    }

    override fun onStop() {
        viewModel.persistNow()
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
