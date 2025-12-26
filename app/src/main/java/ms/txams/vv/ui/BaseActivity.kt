package ms.txams.vv.ui

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import ms.txams.vv.core.TXAApp

/**
 * Base Activity for TXA Music
 * Handles dynamic font application globally
 */
abstract class BaseActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply factory2 before super.onCreate to catch all views during inflation
        try {
            LayoutInflater.from(this).factory2 = object : LayoutInflater.Factory2 {
                override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
                    val view = delegate.createView(parent, name, context, attrs)
                    if (view is TextView) {
                        applyCustomFont(view)
                    }
                    return view
                }

                override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
                    return onCreateView(null, name, context, attrs)
                }
            }
        } catch (e: Exception) {
            // Factory might already be set
        }
        
        super.onCreate(savedInstanceState)
    }

    override fun onTitleChanged(title: CharSequence?, color: Int) {
        super.onTitleChanged(title, color)
        // Set font to toolbar if exists
        findViewById<Toolbar>(ms.txams.vv.R.id.toolbar)?.let { toolbar ->
            applyFontToToolbar(toolbar)
        }
    }

    private fun applyFontToToolbar(toolbar: Toolbar) {
        val fontResId = TXAApp.getCurrentFontResId(this)
        val typeface = try { ResourcesCompat.getFont(this, fontResId) } catch (e: Exception) { null }
        
        for (i in 0 until toolbar.childCount) {
            val view = toolbar.getChildAt(i)
            if (view is TextView) {
                view.typeface = typeface
            }
        }
    }

    private fun applyCustomFont(textView: TextView) {
        val fontResId = TXAApp.getCurrentFontResId(this)
        try {
            val typeface = ResourcesCompat.getFont(this, fontResId)
            textView.typeface = typeface
        } catch (e: Exception) {}
    }
}
