package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.RoomDatabase

internal const val ACTIVITY_DATABASE_NAME = "alphathinker-activity.db"

/** The activity log database's builder — same seam as [provideDatabaseBuilder], separate file. */
expect fun provideActivityDatabaseBuilder(
  context: PlatformContext,
): RoomDatabase.Builder<ActivityDatabase>