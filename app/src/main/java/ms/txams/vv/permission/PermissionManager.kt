package ms.txams.vv.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager {

    companion object {
        const val REQUEST_CODE_AUDIO_PERMISSION = 1001
        
        // Android 13+ uses READ_MEDIA_AUDIO instead of READ_EXTERNAL_STORAGE
        fun getAudioPermission(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
        
        fun hasAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                getAudioPermission()
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        fun requestAudioPermission(fragment: Fragment) {
            fragment.requestPermissions(
                arrayOf(getAudioPermission()),
                REQUEST_CODE_AUDIO_PERMISSION
            )
        }
        
        fun shouldShowPermissionRationale(fragment: Fragment): Boolean {
            return fragment.shouldShowRequestPermissionRationale(getAudioPermission())
        }
        
        fun getPermissionMessage(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "This app needs access to your audio files to play music. Please grant permission to access your music library."
            } else {
                "This app needs access to your device storage to find and play music files."
            }
        }
    }
}
