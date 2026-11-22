package br.com.voleipelada.di

import android.content.Context
import androidx.room.Room
import br.com.voleipelada.data.DataStorePreferences
import br.com.voleipelada.data.LeagueDatabase
import br.com.voleipelada.data.RoomLeagueRepository
import br.com.voleipelada.domain.LeagueRepository
import br.com.voleipelada.domain.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun league(implementation: RoomLeagueRepository): LeagueRepository
    @Binds abstract fun preferences(implementation: DataStorePreferences): PreferencesRepository

    companion object {
        @Provides @Singleton
        fun database(@ApplicationContext context: Context): LeagueDatabase =
            Room.databaseBuilder(context, LeagueDatabase::class.java, "volei_pelada.db")
                .addMigrations(LeagueDatabase.MIGRATION_1_2, LeagueDatabase.MIGRATION_2_3, LeagueDatabase.MIGRATION_3_4)
                .addCallback(LeagueDatabase.seed)
                .build()
    }
}
