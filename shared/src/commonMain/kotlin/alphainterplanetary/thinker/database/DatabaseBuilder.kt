package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.RoomDatabase

expect fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase>