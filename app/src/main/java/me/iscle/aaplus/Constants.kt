package me.iscle.aaplus

object Constants {
    const val PACKAGE_NAME = "me.iscle.aaplus"

    const val ANDROID_AUTO_SETTINGS = "android_auto"
    const val SETTING_ANDROID_AUTO_HOOK_MUSIC_STREAM_VOLUME = "hook_music_stream_volume"
    const val SETTING_ANDROID_AUTO_HOOK_MUSIC_STREAM_VOLUME_DEFAULT = false

    // Replaces Android Auto's volume-based ducking (music goes quiet while navigation speaks)
    // with a frequency-selective duck: high frequencies are attenuated while the low end is kept,
    // the way clubs and radio stations lower a track under an announcement.
    const val SETTING_ANDROID_AUTO_FREQUENCY_DUCK = "frequency_selective_duck"
    const val SETTING_ANDROID_AUTO_FREQUENCY_DUCK_DEFAULT = false

    const val WAZE_SETTINGS = "waze"
    const val SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT = "playSpeedCameraSoundBelowSpeedLimit"
    const val SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT_DEFAULT = true
}