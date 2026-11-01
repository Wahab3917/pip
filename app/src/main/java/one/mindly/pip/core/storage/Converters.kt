package one.mindly.pip.core.storage

import androidx.room.TypeConverter
import java.time.Instant
import java.util.UUID

/**
 * Teaches Room how to store types SQLite doesn't know. UUIDs become readable TEXT
 * (handy when inspecting the DB), Instants become epoch milliseconds.
 * Enums need no converter: Room stores them by name.
 */
class Converters {
    @TypeConverter fun uuidToString(id: UUID): String = id.toString()
    @TypeConverter fun stringToUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()
    @TypeConverter fun millisToInstant(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)
}
