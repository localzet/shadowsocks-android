

package com.localzet.shadowsocks.tasker

import android.app.Activity
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.localzet.shadowsocks.R
import com.localzet.shadowsocks.database.Profile
import com.localzet.shadowsocks.database.ProfileManager
import com.localzet.shadowsocks.utils.resolveResourceId
import com.localzet.shadowsocks.widget.ListHolderListener
import com.localzet.shadowsocks.widget.ListListener

class ConfigActivity : AppCompatActivity() {
    inner class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        private var item: Profile? = null
        private val text = itemView.findViewById<CheckedTextView>(android.R.id.text1)

        init {
            view.setBackgroundResource(theme.resolveResourceId(android.R.attr.selectableItemBackground))
            itemView.setOnClickListener(this)
        }

        fun bindDefault() {
            item = null
            text.setText(R.string.profile_default)
            text.isChecked = taskerOption.profileId < 0
        }
        fun bind(item: Profile) {
            this.item = item
            text.text = item.formattedName
            text.isChecked = taskerOption.profileId == item.id
        }

        override fun onClick(v: View?) {
            taskerOption.switchOn = switch.isChecked
            val item = item
            taskerOption.profileId = item?.id ?: -1
            setResult(Activity.RESULT_OK, taskerOption.toIntent(this@ConfigActivity))
            finish()
        }
    }

    inner class ProfilesAdapter : RecyclerView.Adapter<ProfileViewHolder>() {
        internal val profiles = ProfileManager.getActiveProfiles()?.toMutableList() ?: mutableListOf()

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) =
                if (position == 0) holder.bindDefault() else holder.bind(profiles[position - 1])
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder = ProfileViewHolder(
                LayoutInflater.from(parent.context).inflate(Resources.getSystem()
                        .getIdentifier("select_dialog_singlechoice_material", "layout", "android"), parent, false))
        override fun getItemCount(): Int = 1 + profiles.size
    }

    private lateinit var taskerOption: Settings
    private lateinit var switch: Switch
    private val profilesAdapter = ProfilesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        if (intent == null) {
            finish()
            return
        }
        taskerOption = Settings.fromIntent(intent)
        setContentView(R.layout.layout_tasker)
        ListHolderListener.setup(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.app_name)
        toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
        toolbar.setNavigationOnClickListener { finish() }

        switch = findViewById(R.id.serviceSwitch)
        switch.isChecked = taskerOption.switchOn
        findViewById<RecyclerView>(R.id.list).apply {
            ViewCompat.setOnApplyWindowInsetsListener(this, ListListener)
            itemAnimator = DefaultItemAnimator()
            adapter = profilesAdapter
            layoutManager = LinearLayoutManager(this@ConfigActivity, RecyclerView.VERTICAL, false).apply {
                if (taskerOption.profileId >= 0) {
                    scrollToPosition(profilesAdapter.profiles.indexOfFirst { it.id == taskerOption.profileId } + 1)
                }
            }
        }
    }
}
