package one.mindly.pip.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The single on-device database. When the schema changes, bump [version] and add a Migration;
 * the exported schemas in app/schemas/ let Room verify migrations. Never use
 * fallbackToDestructiveMigration here: that would delete thoughts.
 */
@Database(entities = [Thought::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class PipDatabase : RoomDatabase() {
    abstract fun thoughtDao(): ThoughtDao

    companion object {
        fun create(context: Context): PipDatabase =
            Room.databaseBuilder(context, PipDatabase::class.java, "pip.db").build()
    }
}
