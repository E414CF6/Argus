package argus

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import argus.data.preferences.AppPreferences
import argus.ui.ArgusMainScreen
import argus.ui.ArgusViewModel
import argus.ui.PermissionGateScreen
import argus.ui.hasAllScanPermissions
import argus.ui.theme.ArgusTheme

class Argus : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppPreferences.wrapContextWithLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: ArgusViewModel = viewModel()
            val extra by vm.extraState.collectAsState()
            var granted by remember { mutableStateOf(hasAllScanPermissions(this)) }

            LaunchedEffect(extra.keepScreenOn) {
                if (extra.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            ArgusTheme(themeMode = extra.themeMode) {
                Surface {
                    if (!granted) {
                        PermissionGateScreen(currentLanguage = extra.language, onSelectLanguage = { lang ->
                            vm.setLanguage(lang)
                            recreate()
                        }, onPermissionsGranted = { granted = true })
                    } else {
                        ArgusMainScreen(viewModel = vm)
                    }
                }
            }
        }
    }
}
