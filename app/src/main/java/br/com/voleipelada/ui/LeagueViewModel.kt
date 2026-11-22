package br.com.voleipelada.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.voleipelada.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiState(
    val league: League? = null,
    val preferences: Preferences = Preferences(),
    val error: String? = null,
)

@HiltViewModel
class LeagueViewModel @Inject constructor(
    private val repository: LeagueRepository,
    private val settings: PreferencesRepository,
) : ViewModel() {
    private val failure = MutableStateFlow<String?>(null)
    private val retry = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = retry.flatMapLatest {
        combine(repository.league, settings.preferences, failure, ::UiState)
            .catch { emit(UiState(error = "Não foi possível carregar os dados locais.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure.value = "Não foi possível salvar a alteração."
            }
        }
    }

    fun retry() { retry.update { it + 1 } }
    fun clearError() { failure.value = null }
    fun update(onSaved: () -> Unit = {}, transform: (Session) -> Session) = perform {
        repository.update(transform)
        onSaved()
    }
    fun preferences(value: Preferences) = perform { settings.save(value) }
    fun finish(session: Session) = perform { repository.finish(session, System.currentTimeMillis()) }

    fun draw(playerIds: Set<Long>, size: Int, replace: Boolean, minWomen: Int) = perform {
        repository.draw(playerIds, size, replace, minWomen)
    }

    fun saveLeagueName(name: String, onSaved: () -> Unit) = perform {
        settings.saveLeagueName(name)
        onSaved()
    }
    fun clearHistory() = perform { repository.clearHistory() }
    fun resetStandings() = perform { repository.resetStandings() }

    fun point(side: Int, delta: Int) = update { ScorePointUseCase()(it, side, delta) }

    fun undo() = update {
        val match = it.match
        val previous = match.points.lastOrNull() ?: return@update it
        it.copy(match = match.copy(scores = previous.scores, serve = previous.serve, points = match.points.dropLast(1)))
    }

    fun swap() = update {
        val match = it.match
        if (it.court.size < 2) return@update it
        it.copy(court = it.court.reversed(), match = match.copy(scores = match.scores.reversed(),
            serve = 1 - match.serve, swapped = !match.swapped,
            points = match.points.map { point -> PointSnapshot(point.scores.reversed(), 1 - point.serve) }))
    }

    fun timer() = update {
        val now = System.currentTimeMillis()
        val match = it.match
        it.copy(match = if (match.startedAt == null) match.copy(startedAt = now)
            else match.copy(startedAt = null, elapsedMs = match.duration(now)))
    }

    fun move(id: Long, offset: Int) = update {
        val index = it.queue.indexOf(id)
        val destination = index + offset
        if (index < 0 || destination !in it.queue.indices) return@update it
        it.copy(queue = it.queue.toMutableList().apply { add(destination, removeAt(index)) })
    }
}
