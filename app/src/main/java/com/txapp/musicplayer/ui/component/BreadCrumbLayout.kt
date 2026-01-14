package com.txapp.musicplayer.ui.component

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXAPreferences
import java.io.File
import java.util.*

/**
 * Breadcrumb navigation for folder browsing.
 * Modified from Retro Music Player.
 */
class BreadCrumbLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr), View.OnClickListener {

    @ColorInt
    private var contentColorActivated: Int = Color.WHITE
    @ColorInt
    private var contentColorDeactivated: Int = Color.GRAY
    
    private var mActive: Int = -1
    private var mCallback: SelectionCallback? = null
    private lateinit var mChildFrame: LinearLayout
    private val mCrumbs = ArrayList<Crumb>()
    private val mHistory = ArrayList<Crumb>()
    private var mOldCrumbs: MutableList<Crumb>? = null

    init {
        init()
    }

    private fun init() {
        try {
            contentColorActivated = Color.parseColor(TXAPreferences.currentAccent)
        } catch (e: Exception) {
            contentColorActivated = Color.WHITE
        }
        
        contentColorDeactivated = Color.GRAY
        
        setMinimumHeight(resources.getDimensionPixelSize(R.dimen.tab_height))
        clipToPadding = false
        isHorizontalScrollBarEnabled = false
        
        mChildFrame = LinearLayout(context)
        addView(
            mChildFrame,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun addCrumb(crumb: Crumb, refreshLayout: Boolean) {
        val view = LayoutInflater.from(context).inflate(R.layout.bread_crumb, this, false) as LinearLayout
        view.tag = mCrumbs.size
        view.setOnClickListener(this)

        val iv = view.getChildAt(1) as ImageView
        iv.drawable?.isAutoMirrored = true
        iv.visibility = View.GONE

        mChildFrame.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        mCrumbs.add(crumb)
        if (refreshLayout) {
            mActive = mCrumbs.size - 1
            requestLayout()
        }
        invalidateActivatedAll()
    }

    fun clearCrumbs() {
        try {
            mOldCrumbs = ArrayList(mCrumbs)
            mCrumbs.clear()
            mChildFrame.removeAllViews()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    fun findCrumb(forDir: File): Crumb? {
        for (crumb in mCrumbs) {
            if (crumb.file == forDir) {
                return crumb
            }
        }
        return null
    }

    override fun onClick(v: View) {
        val index = v.tag as Int
        mCallback?.onCrumbSelection(mCrumbs[index], index)
    }

    fun setActiveOrAdd(crumb: Crumb, forceRecreate: Boolean) {
        if (forceRecreate || !setActive(crumb)) {
            clearCrumbs()
            val newPathSet = ArrayList<File>()
            var p: File? = crumb.file
            while (p != null) {
                newPathSet.add(0, p)
                p = p.parentFile
            }

            for (fi in newPathSet) {
                var c = Crumb(fi)
                // Restore scroll positions if possible
                mOldCrumbs?.let { oldList ->
                    val it = oldList.iterator()
                    while (it.hasNext()) {
                        val old = it.next()
                        if (old == c) {
                            c.scrollPos = old.scrollPos
                            it.remove()
                            break
                        }
                    }
                }
                addCrumb(c, true)
            }
            mOldCrumbs = null
        }
    }

    private fun setActive(newActive: Crumb): Boolean {
        mActive = mCrumbs.indexOf(newActive)
        invalidateActivatedAll()
        val success = mActive > -1
        if (success) {
            requestLayout()
        }
        return success
    }

    private fun invalidateActivatedAll() {
        for (i in 0 until mCrumbs.size) {
            val crumb = mCrumbs[i]
            val textView = invalidateActivated(
                mChildFrame.getChildAt(i),
                mActive == i,
                i < mCrumbs.size - 1
            )
            textView.text = crumb.title
        }
    }

    private fun invalidateActivated(
        view: View,
        isActive: Boolean,
        allowArrowVisible: Boolean
    ): TextView {
        val contentColor = if (isActive) contentColorActivated else contentColorDeactivated
        val child = view as LinearLayout
        val tv = child.getChildAt(0) as TextView
        tv.setTextColor(contentColor)
        val iv = child.getChildAt(1) as ImageView
        iv.setColorFilter(contentColor, PorterDuff.Mode.SRC_IN)
        
        iv.visibility = if (allowArrowVisible) View.VISIBLE else View.GONE
        
        return tv
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val child = mChildFrame.getChildAt(mActive)
        child?.let {
            smoothScrollTo(it.left, 0)
        }
    }

    fun setCallback(callback: SelectionCallback?) {
        mCallback = callback
    }

    interface SelectionCallback {
        fun onCrumbSelection(crumb: Crumb, index: Int)
    }

    class Crumb : Parcelable {
        val file: File
        var scrollPos: Int = 0

        constructor(file: File) {
            this.file = file
        }

        constructor(parcel: Parcel) {
            file = parcel.readSerializable() as File
            scrollPos = parcel.readInt()
        }

        val title: String
            get() = if (file.path == "/") "root" else file.name

        override fun equals(other: Any?): Boolean {
            return (other is Crumb) && other.file == file
        }

        override fun hashCode(): Int {
            return file.hashCode()
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeSerializable(file)
            parcel.writeInt(scrollPos)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Crumb> {
            override fun createFromParcel(parcel: Parcel): Crumb {
                return Crumb(parcel)
            }

            override fun newArray(size: Int): Array<Crumb?> {
                return arrayOfNulls(size)
            }
        }
    }
}
