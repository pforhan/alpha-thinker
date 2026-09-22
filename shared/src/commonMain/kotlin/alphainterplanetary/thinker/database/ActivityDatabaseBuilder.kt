package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.RoomDatabase

expect fun provideActivityDatabaseBuilder(
  context: PlatformContext,
): RoomDatabase.Builder<ActivityDatabase>