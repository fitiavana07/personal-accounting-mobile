package dev.fitiavana.accounting.ui.sync

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.sync.PeerState
import dev.fitiavana.accounting.features.sync.PeerSync

/** One row per device found on the Wi-Fi, with where its last sync stands; tapping a row syncs with it. */
class SyncPeersAdapter(private val onSync: (deviceId: String) -> Unit) :
    RecyclerView.Adapter<SyncPeersAdapter.ViewHolder>() {

    private var peers: List<PeerState> = emptyList()

    fun submit(peers: List<PeerState>) {
        this.peers = peers
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = peers.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sync_peer, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(peers[position], onSync)

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.text_peer_name)
        private val status = view.findViewById<TextView>(R.id.text_peer_status)
        private val normalStatusColor: ColorStateList = status.textColors

        fun bind(item: PeerState, onSync: (String) -> Unit) {
            name.text = item.peer.label
            status.text = SyncStatusFormatter.peer(itemView.context, item.sync)
            if (item.sync is PeerSync.Failed) {
                status.setTextColor(ContextCompat.getColor(itemView.context, R.color.loss))
            } else {
                status.setTextColor(normalStatusColor)
            }
            itemView.setOnClickListener { onSync(item.peer.info.deviceId) }
        }
    }
}
