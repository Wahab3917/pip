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

    @Insert
    suspend fun insert(thought: Thought)
}
