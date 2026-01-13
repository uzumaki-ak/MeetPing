package com.yourname.meetinglistener.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yourname.meetinglistener.R
import com.yourname.meetinglistener.storage.entities.MeetingSummaryEntity

/**
 * MeetingHistoryAdapter.kt (WITH EXPORT)
 */
class MeetingHistoryAdapter(
    private val onItemClick: (MeetingSummaryEntity) -> Unit,
    private val onQueryClick: (MeetingSummaryEntity) -> Unit,
    private val onExportClick: (MeetingSummaryEntity) -> Unit,
    private val onDeleteClick: (MeetingSummaryEntity) -> Unit
) : ListAdapter<MeetingSummaryEntity, MeetingHistoryAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvMeetingDate)
        val tvDuration: TextView = view.findViewById(R.id.tvMeetingDuration)
        val tvSummary: TextView = view.findViewById(R.id.tvMeetingSummary)
        val btnQuery: Button = view.findViewById(R.id.btnQuery)
        val btnExport: Button = view.findViewById(R.id.btnExport)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meeting_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val meeting = getItem(position)

        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        holder.tvDate.text = "📅 ${sdf.format(java.util.Date(meeting.startTime))}"
        holder.tvDuration.text = "⏱️ ${meeting.durationMinutes} min | 📝 ${meeting.transcriptCount} segments"
        holder.tvSummary.text = meeting.finalSummary.take(100) + if (meeting.finalSummary.length > 100) "..." else ""

        holder.itemView.setOnClickListener { onItemClick(meeting) }
        holder.btnQuery.setOnClickListener { onQueryClick(meeting) }
        holder.btnExport.setOnClickListener { onExportClick(meeting) }
        holder.btnDelete.setOnClickListener { onDeleteClick(meeting) }
    }

    class DiffCallback : DiffUtil.ItemCallback<MeetingSummaryEntity>() {
        override fun areItemsTheSame(oldItem: MeetingSummaryEntity, newItem: MeetingSummaryEntity): Boolean {
            return oldItem.meetingId == newItem.meetingId
        }

        override fun areContentsTheSame(oldItem: MeetingSummaryEntity, newItem: MeetingSummaryEntity): Boolean {
            return oldItem == newItem
        }
    }
}