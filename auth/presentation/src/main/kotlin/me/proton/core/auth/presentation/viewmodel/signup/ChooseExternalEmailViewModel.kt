/*
 * Copyright (c) 2023 Proton AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.auth.presentation.viewmodel.signup

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import me.proton.core.auth.domain.usecase.AccountAvailability
import me.proton.core.observability.domain.ObservabilityContext
import me.proton.core.observability.domain.ObservabilityManager
import me.proton.core.observability.domain.metrics.SignupEmailAvailabilityTotal
import me.proton.core.observability.domain.metrics.SignupFetchDomainsTotal
import me.proton.core.presentation.viewmodel.ProtonViewModel
import me.proton.core.util.kotlin.coroutine.launchWithResultContext
import javax.inject.Inject

@HiltViewModel
internal class ChooseExternalEmailViewModel @Inject constructor(
    private val accountAvailability: AccountAvailability,
    override val manager: ObservabilityManager
) : ProtonViewModel(), ObservabilityContext {

    private val mainState = MutableStateFlow<State>(State.Idle)
    val state = mainState.asStateFlow()

    sealed class State {
        object Idle : State()
        object Processing : State()
        data class SwitchInternal(val username: String, val domain: String) : State()
        data class Success(val email: String) : State()
        sealed class Error : State() {
            data class Message(val error: Throwable) : Error()
        }
    }

    fun checkExternalEmail(email: String) = viewModelScope.launchWithResultContext {
        onResultEnqueue("checkExternalEmailAvailable") { SignupEmailAvailabilityTotal(this) }
        onResultEnqueue("getAvailableDomains") { SignupFetchDomainsTotal(this) }

        flow {
            emit(State.Processing)

            // See CP-5335.
            val domains = accountAvailability.getDomains(userId = null)
            val emailSplit = email.split("@")
            val username = emailSplit.getOrNull(0)
            val domain = emailSplit.getOrNull(1)

            when {
                username != null && domain != null && domain in domains -> {
                    emit(State.SwitchInternal(username, domain))
                }

                else -> {
                    accountAvailability.checkExternalEmail(email = email)
                    emit(State.Success(email))
                }
            }
        }.catch { error ->
            emit(State.Error.Message(error))
        }.collect {
            mainState.tryEmit(it)
        }
    }
}
