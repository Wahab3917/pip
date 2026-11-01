package one.mindly.pip.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * One caught thought. The audio file is the source of truth; everything else
 * (transcript, category, reminder) is derived later and can always be redone.
 */
@Entity(tableName = "thoughts")
data class Thought(
    /** Stable forever: future connectors will use it to recognise a thought they already received. */
    @PrimaryKey val id: UUID,
    val createdAt: Instant,
    /** Path RELATIVE to the app's filesDir (e.g. "thoughts/<id>.wav"), so it survives storage moves. */
    val audioPath: String,
    val durationMs: Long,
    val transcript: String? = null,
    val category: Category = Category.THOUGHT,
    val categorySource: CategorySource = CategorySource.RULE,
    val remindAt: Instant? = null,
    val status: ThoughtStatus = ThoughtStatus.CAUGHT,
    val state: ThoughtState = ThoughtState.POCKET,
)

/** What kind of thought it is. THOUGHT is the catch-all until Phase 2 categorises it. */
enum class Category { IDEA, TASK, REMINDER, SHOPPING, THOUGHT }

/** Who chose the category: Pip's keyword rules, or the user (USER always wins). */
enum class CategorySource { RULE, USER }

/** Processing progress of the derived data. */
enum class ThoughtStatus { CAUGHT, TRANSCRIBING, READY, FAILED }

/**
 * Where the thought is in its life. POCKET = kept in Pip. PLANTED = handed to somewhere else
 * (a connector, later). LET_GO = the user is done with it.
 */
enum class ThoughtState { POCKET, PLANTED, LET_GO }
