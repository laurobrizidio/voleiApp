package br.com.voleipelada.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.voleipelada.domain.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class RoomLeagueRepository @Inject constructor(db: LeagueDatabase) : LeagueRepository {
    private val dao = db.dao()
    override val league = db.invalidationTracker.createFlow(
        "teams", "players", "rotation_rules", "active_match_state", "match_history"
    ).map { dao.readLeague() }

    override suspend fun update(transform: (Session) -> Session) = dao.update(transform)
    override suspend fun finish(expected: Session, now: Long) = dao.finishMatchAndRotateTeams(expected, now)
    override suspend fun draw(playerIds: Set<Long>, size: Int, replace: Boolean, minWomen: Int) =
        dao.drawTeams(playerIds, size, replace, minWomen)
    override suspend fun clearHistory() = dao.clearHistory()
    override suspend fun resetStandings() = dao.resetStandings()
}

private val Context.settings by preferencesDataStore("settings")

@Singleton
class DataStorePreferences @Inject constructor(@ApplicationContext context: Context) : PreferencesRepository {
    private val store = context.settings
    private val appearance = stringPreferencesKey("appearance")
    private val vibration = booleanPreferencesKey("vibration")
    private val sound = booleanPreferencesKey("sound")
    private val leagueName = stringPreferencesKey("league_name")

    override val preferences = store.data.map {
        Preferences(Appearance.entries.find { option -> option.name == it[appearance] } ?: Appearance.SYSTEM,
            it[vibration] ?: true, it[sound] ?: false, it[leagueName].orEmpty())
    }

    override suspend fun saveLeagueName(name: String) {
        store.edit { it[leagueName] = name.trim() }
    }

    override suspend fun save(preferences: Preferences) {
        store.edit {
            it[appearance] = preferences.appearance.name
            it[vibration] = preferences.vibration
            it[sound] = preferences.sound
        }
    }
}
