package me.iscle.aaplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.iscle.aaplus.ui.AndroidAutoSettingsComposable
import me.iscle.aaplus.ui.WazeSettingsComposable
import me.iscle.aaplus.ui.component.SettingCategory
import me.iscle.aaplus.ui.component.SwitchSetting
import me.iscle.aaplus.ui.theme.AAPlusTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AAPlusTheme {
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val showSnackbar = {
                    scope.launch {
                        if (snackbarHostState.currentSnackbarData == null) {
                            snackbarHostState.showSnackbar("Kill the target app to apply changes!")
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Android Auto+")
                            }
                        )
                    },
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        item {
                            AndroidAutoSettingsComposable(
                                modifier = Modifier.padding(16.dp),
                                onSettingChanged = { showSnackbar() },
                            )
                        }

                        item {
                            WazeSettingsComposable(
                                modifier = Modifier.padding(16.dp),
                                onSettingChanged = { showSnackbar() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    AAPlusTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            SettingCategory(title = "Waze") {
                SwitchSetting(
                    title = "Play speed camera sound below speed limit",
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }
    }
}