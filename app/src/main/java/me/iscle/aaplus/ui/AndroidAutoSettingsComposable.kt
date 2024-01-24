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
fun AndroidAutoSettingsComposable(
    modifier: Modifier = Modifier,
    onSettingChanged: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(Constants.ANDROID_AUTO_SETTINGS, Context.MODE_WORLD_READABLE)
    }

    var hookMusicStreamVolume by remember(preferences) { mutableStateOf(
        preferences.getBoolean(
            Constants.SETTING_ANDROID_AUTO_HOOK_MUSIC_STREAM_VOLUME,
            Constants.SETTING_ANDROID_AUTO_HOOK_MUSIC_STREAM_VOLUME_DEFAULT
        )
    ) }

    Column(
        modifier = modifier,
    ) {
        SettingCategory(title = "Android Auto") {
            SwitchSetting(
                title = "Hook music stream volume",
                checked = hookMusicStreamVolume,
                onCheckedChange = {
                    hookMusicStreamVolume = it
                    preferences.edit().putBoolean(
                        Constants.SETTING_ANDROID_AUTO_HOOK_MUSIC_STREAM_VOLUME,
                        it
                    ).apply()
                    onSettingChanged()
                },
            )
        }
    }
}