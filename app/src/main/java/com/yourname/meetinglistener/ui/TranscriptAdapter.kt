package com.yourname.meetinglistener.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yourname.meetinglistener.R
import com.yourname.meetinglistener.models.TranscriptChunk

/**
 * TranscriptAdapter.kt
 *
 * PURPOSE:
 * Displays live transcripts in RecyclerView
 * Updates in real-time as speech is recognized
 */
class TranscriptAdapter : RecyclerView.Adapter<TranscriptAdapter.ViewHolder>() {

    private val transcripts = mutableListOf<TranscriptChunk>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvText: TextView = view.findViewById(R.id.tvTranscriptText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transcript, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transcript = transcripts[position]
        holder.tvTimestamp.text = transcript.timestamp
        holder.tvText.text = transcript.text
    }

    override fun getItemCount() = transcripts.size

    fun updateTranscripts(newTranscripts: List<TranscriptChunk>) {
        transcripts.clear()
        transcripts.addAll(newTranscripts)
        notifyDataSetChanged()
    }

    fun clear() {
        transcripts.clear()
        notifyDataSetChanged()
    }
}