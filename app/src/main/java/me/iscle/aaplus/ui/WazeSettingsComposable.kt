package me.iscle.aaplus.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import me.iscle.aaplus.Constants
import me.iscle.aaplus.ui.component.SettingCategory
import me.iscle.aaplus.ui.component.SwitchSetting

@Composable
fun WazeSettingsComposable(
    modifier: Modifier = Modifier,
    onSettingChanged: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(Constants.WAZE_SETTINGS, Context.MODE_WORLD_READABLE)
    }

    var playSpeedCameraSoundBelowSpeedLimit by remember(preferences) { mutableStateOf(
        preferences.getBoolean(
            Constants.SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT,
            Constants.SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT_DEFAULT
        )
    ) }

    Column(
        modifier = modifier,
    ) {
        SettingCategory(title = "Waze") {
            SwitchSetting(
                title = "Play speed camera sound below speed limit",
                checked = playSpeedCameraSoundBelowSpeedLimit,
                onCheckedChange = {
                    playSpeedCameraSoundBelowSpeedLimit = it
                    preferences.edit().putBoolean(
                        Constants.SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT,
                        it
                    ).apply()
                    onSettingChanged()
                },
            )
        }
    }
}