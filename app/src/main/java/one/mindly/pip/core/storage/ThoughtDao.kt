package one.mindly.pip.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ThoughtDao {
    /** Newest first. The Flow re-emits whenever the table changes, so the UI updates by itself. */
    @Query("SELECT * FROM thoughts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Thought>>

    @Query("SELECT * FROM thoughts WHERE id = :id")
    suspend fun get(id: UUID): Thought?

    @Query("SELECT * FROM thoughts WHERE id = :id")
    fun observe(id: UUID): Flow<Thought?>

    @Insert
    suspend fun insert(thought: Thought)

    /** The oldest thought still waiting for its transcript. */
    @Query("SELECT * FROM thoughts WHERE status = 'CAUGHT' ORDER BY createdAt LIMIT 1")
    suspend fun nextToTranscribe(): Thought?

    @Query("UPDATE thoughts SET status = :status WHERE id = :id")
    suspend fun setStatus(id: UUID, status: ThoughtStatus)

    @Query("UPDATE thoughts SET transcript = :transcript, status = 'READY' WHERE id = :id")
    suspend fun setTranscript(id: UUID, transcript: String)

    @Query("DELETE FROM thoughts WHERE id = :id")
    suspend fun delete(id: UUID)

    /** A transcription that was running when the app died never finished: queue it again. */
    @Query("UPDATE thoughts SET status = 'CAUGHT' WHERE status = 'TRANSCRIBING'")
    suspend fun requeueInterrupted()
}
