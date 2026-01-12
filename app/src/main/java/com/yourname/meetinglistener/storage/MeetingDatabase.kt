package com.yourname.meetinglistener.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yourname.meetinglistener.storage.entities.MeetingSummaryEntity
import com.yourname.meetinglistener.storage.entities.TranscriptChunkEntity

/**
 * MeetingDatabase.kt
 *
 * PURPOSE:
 * Room database for persistent storage
 * Stores meeting history, transcripts, and summaries
 *
 * FEATURES:
 * - Meeting summaries table
 * - Transcript chunks table
 * - DAO interfaces for database operations
 */
@Database(
    entities = [
        MeetingSummaryEntity::class,
        TranscriptChunkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MeetingDatabase : RoomDatabase() {

    abstract fun meetingSummaryDao(): MeetingSummaryDao
    abstract fun transcriptChunkDao(): TranscriptChunkDao

    companion object {
        @Volatile
        private var INSTANCE: MeetingDatabase? = null

        /**
         * Get singleton database instance
         */
        fun getDatabase(context: Context): MeetingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeetingDatabase::class.java,
                    "meeting_listener_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * DAO for MeetingSummary operations
 */
@androidx.room.Dao
interface MeetingSummaryDao {

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(summary: MeetingSummaryEntity)

    @androidx.room.Query("SELECT * FROM meeting_summaries ORDER BY createdAt DESC")
    suspend fun getAllSummaries(): List<MeetingSummaryEntity>

    @androidx.room.Query("SELECT * FROM meeting_summaries WHERE meetingId = :meetingId")
    suspend fun getSummaryById(meetingId: String): MeetingSummaryEntity?

    @androidx.room.Query("DELETE FROM meeting_summaries WHERE meetingId = :meetingId")
    suspend fun deleteSummary(meetingId: String)

    @androidx.room.Query("DELETE FROM meeting_summaries")
    suspend fun deleteAll()
}

/**
 * DAO for TranscriptChunk operations
 */
@androidx.room.Dao
interface TranscriptChunkDao {

    @androidx.room.Insert
    suspend fun insert(chunk: TranscriptChunkEntity)

    @androidx.room.Insert
    suspend fun insertAll(chunks: List<TranscriptChunkEntity>)

    @androidx.room.Query("SELECT * FROM transcript_chunks WHERE meetingId = :meetingId ORDER BY timestampMillis ASC")
    suspend fun getChunksForMeeting(meetingId: String): List<TranscriptChunkEntity>

    @androidx.room.Query("DELETE FROM transcript_chunks WHERE meetingId = :meetingId")
    suspend fun deleteChunksForMeeting(meetingId: String)

    @androidx.room.Query("DELETE FROM transcript_chunks")
    suspend fun deleteAll()
}