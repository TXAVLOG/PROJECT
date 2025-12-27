package ms.txams.vv.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.core.TXABackgroundLogger
import ms.txams.vv.data.manager.TXANowBarSettingsManager

/**
 * TXA Now Bar Settings Activity
 * Settings screen for customizing notification buttons (Now Bar / Dynamic Island style)
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
class TXANowBarSettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: TXANowBarSettingsManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SettingsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_txa_now_bar_settings)
        
        settingsManager = TXANowBarSettingsManager(this)
        
        setupToolbar()
        setupRecyclerView()
        setupButtons()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = TXATranslation.txa("txamusic_settings_now_bar")
        
        toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.rvSettings)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val settingsItems = listOf(
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_SHOW_PREVIOUS,
                titleKey = "txamusic_nowbar_show_previous",
                icon = R.drawable.ic_previous,
                type = SettingsItemType.SWITCH
            ),
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_SHOW_NEXT,
                titleKey = "txamusic_nowbar_show_next",
                icon = R.drawable.ic_next,
                type = SettingsItemType.SWITCH
            ),
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_SHOW_STOP,
                titleKey = "txamusic_nowbar_show_stop",
                icon = R.drawable.ic_stop,
                type = SettingsItemType.SWITCH
            ),
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_SHOW_SHUFFLE,
                titleKey = "txamusic_nowbar_show_shuffle",
                icon = R.drawable.ic_shuffle,
                type = SettingsItemType.SWITCH
            ),
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_SHOW_REPEAT,
                titleKey = "txamusic_nowbar_show_repeat",
                icon = R.drawable.ic_repeat,
                type = SettingsItemType.SWITCH
            ),
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_SHOW_LIKE,
                titleKey = "txamusic_nowbar_show_like",
                icon = R.drawable.ic_heart,
                type = SettingsItemType.SWITCH
            ),
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_SHOW_SLEEP_TIMER,
                titleKey = "txamusic_nowbar_show_sleep_timer",
                icon = R.drawable.ic_sleep,
                type = SettingsItemType.SWITCH
            ),
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_SHOW_LYRICS,
                titleKey = "txamusic_nowbar_show_lyrics",
                icon = R.drawable.ic_lyrics,
                type = SettingsItemType.SWITCH
            ),
            SettingsItem(
                key = "divider",
                titleKey = "",
                icon = 0,
                type = SettingsItemType.DIVIDER
            ),
            SettingsItem(
                key = TXANowBarSettingsManager.KEY_ALBUM_ART_STYLE,
                titleKey = "txamusic_nowbar_album_art_style",
                icon = R.drawable.ic_music_note,
                type = SettingsItemType.CHOICE
            )
        )
        
        adapter = SettingsAdapter(settingsItems, settingsManager) { item ->
            when (item.type) {
                SettingsItemType.CHOICE -> showAlbumArtStyleDialog()
                else -> {}
            }
        }
        recyclerView.adapter = adapter
    }
    
    private fun setupButtons() {
        val btnReset = findViewById<MaterialButton>(R.id.btnReset)
        btnReset.text = TXATranslation.txa("txamusic_nowbar_reset_defaults")
        btnReset.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(TXATranslation.txa("txamusic_nowbar_reset_defaults"))
                .setMessage(TXATranslation.txa("txamusic_confirm_reset"))
                .setPositiveButton(TXATranslation.txa("txamusic_ok")) { _, _ ->
                    settingsManager.resetToDefaults()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, TXATranslation.txa("txamusic_settings_reset"), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(TXATranslation.txa("txamusic_cancel"), null)
                .show()
        }
    }
    
    private fun showAlbumArtStyleDialog() {
        val styles = arrayOf(
            TXATranslation.txa("txamusic_nowbar_style_square"),
            TXATranslation.txa("txamusic_nowbar_style_rounded"),
            TXATranslation.txa("txamusic_nowbar_style_circle")
        )
        val styleValues = arrayOf(
            TXANowBarSettingsManager.ALBUM_ART_SQUARE,
            TXANowBarSettingsManager.ALBUM_ART_ROUNDED,
            TXANowBarSettingsManager.ALBUM_ART_CIRCLE
        )
        
        val currentStyle = settingsManager.getAlbumArtStyle()
        val currentIndex = styleValues.indexOf(currentStyle).coerceAtLeast(0)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_nowbar_album_art_style"))
            .setSingleChoiceItems(styles, currentIndex) { dialog, which ->
                settingsManager.setAlbumArtStyle(styleValues[which])
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton(TXATranslation.txa("txamusic_cancel"), null)
            .show()
    }
    
    // Data classes
    enum class SettingsItemType {
        SWITCH, CHOICE, DIVIDER
    }
    
    data class SettingsItem(
        val key: String,
        val titleKey: String,
        val icon: Int,
        val type: SettingsItemType
    )
    
    // Adapter
    inner class SettingsAdapter(
        private val items: List<SettingsItem>,
        private val manager: TXANowBarSettingsManager,
        private val onItemClick: (SettingsItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        private val TYPE_SWITCH = 0
        private val TYPE_CHOICE = 1
        private val TYPE_DIVIDER = 2
        
        override fun getItemViewType(position: Int): Int {
            return when (items[position].type) {
                SettingsItemType.SWITCH -> TYPE_SWITCH
                SettingsItemType.CHOICE -> TYPE_CHOICE
                SettingsItemType.DIVIDER -> TYPE_DIVIDER
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_SWITCH -> SwitchViewHolder(inflater.inflate(R.layout.item_setting_switch, parent, false))
                TYPE_CHOICE -> ChoiceViewHolder(inflater.inflate(R.layout.item_setting_choice, parent, false))
                else -> DividerViewHolder(inflater.inflate(R.layout.item_setting_divider, parent, false))
            }
        }
        
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            when (holder) {
                is SwitchViewHolder -> holder.bind(item)
                is ChoiceViewHolder -> holder.bind(item)
            }
        }
        
        override fun getItemCount() = items.size
        
        inner class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.ivIcon)
            private val title: TextView = view.findViewById(R.id.tvTitle)
            private val switch: Switch = view.findViewById(R.id.switchSetting)
            
            fun bind(item: SettingsItem) {
                icon.setImageResource(item.icon)
                title.text = TXATranslation.txa(item.titleKey)
                switch.isChecked = manager.isButtonEnabled(item.key)
                
                switch.setOnCheckedChangeListener { _, isChecked ->
                    manager.setButtonEnabled(item.key, isChecked)
                    TXABackgroundLogger.d("Now Bar setting changed: ${item.key} = $isChecked")
                }
                
                itemView.setOnClickListener {
                    switch.isChecked = !switch.isChecked
                }
            }
        }
        
        inner class ChoiceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.ivIcon)
            private val title: TextView = view.findViewById(R.id.tvTitle)
            private val value: TextView = view.findViewById(R.id.tvValue)
            
            fun bind(item: SettingsItem) {
                icon.setImageResource(item.icon)
                title.text = TXATranslation.txa(item.titleKey)
                
                val styleKey = when (manager.getAlbumArtStyle()) {
                    TXANowBarSettingsManager.ALBUM_ART_SQUARE -> "txamusic_nowbar_style_square"
                    TXANowBarSettingsManager.ALBUM_ART_ROUNDED -> "txamusic_nowbar_style_rounded"
                    TXANowBarSettingsManager.ALBUM_ART_CIRCLE -> "txamusic_nowbar_style_circle"
                    else -> "txamusic_nowbar_style_rounded"
                }
                value.text = TXATranslation.txa(styleKey)
                
                itemView.setOnClickListener { onItemClick(item) }
            }
        }
        
        inner class DividerViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
