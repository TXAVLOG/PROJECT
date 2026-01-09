package com.txapp.musicplayer.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.txapp.musicplayer.ui.theme.TXAMusicTheme

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                TXAMusicTheme {
                    // Instead of hosting the complex Settings navigation here, 
                    // we launch the Activity to preserve the existing successful logic.
                    // This fragment acts as a bridge.
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val intent = Intent(requireContext(), SettingsActivity::class.java)
        startActivity(intent)
    }
}
