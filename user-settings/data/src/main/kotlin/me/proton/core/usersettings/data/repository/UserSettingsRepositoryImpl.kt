/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
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

package me.proton.core.usersettings.data.repository

import com.dropbox.android.external.store4.Fetcher
import com.dropbox.android.external.store4.SourceOfTruth
import com.dropbox.android.external.store4.StoreBuilder
import com.dropbox.android.external.store4.StoreRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.core.auth.domain.usecase.ValidateServerProof
import me.proton.core.crypto.common.srp.Auth
import me.proton.core.crypto.common.srp.SrpProofs
import me.proton.core.data.arch.buildProtonStore
import me.proton.core.data.arch.toDataResult
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.domain.entity.UserId
import me.proton.core.key.data.api.request.AuthRequest
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.isSuccess
import me.proton.core.usersettings.data.api.UserSettingsApi
import me.proton.core.usersettings.data.api.request.SetUsernameRequest
import me.proton.core.usersettings.data.api.request.UpdateLoginPasswordRequest
import me.proton.core.usersettings.data.api.request.UpdateRecoveryEmailRequest
import me.proton.core.usersettings.data.db.UserSettingsDatabase
import me.proton.core.usersettings.data.extension.fromEntity
import me.proton.core.usersettings.data.extension.fromResponse
import me.proton.core.usersettings.data.extension.toEntity
import me.proton.core.usersettings.domain.entity.UserSettings
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import me.proton.core.util.kotlin.CoroutineScopeProvider
import javax.inject.Inject

class UserSettingsRepositoryImpl @Inject constructor(
    db: UserSettingsDatabase,
    private val apiProvider: ApiProvider,
    private val validateServerProof: ValidateServerProof,
    scopeProvider: CoroutineScopeProvider
) : UserSettingsRepository {

    private val userSettingsDao = db.userSettingsDao()

    private val store = StoreBuilder.from(
        fetcher = Fetcher.of { key: UserId ->
            apiProvider.get<UserSettingsApi>(key).invoke {
                getUserSettings().settings.fromResponse(key)
            }.valueOrThrow
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { key -> observeByUserId(key) },
            writer = { _, input -> insertOrUpdate(input) },
            delete = { key -> delete(key) },
            deleteAll = { deleteAll() }
        )
    ).disableCache().buildProtonStore(scopeProvider) // We don't want potential stale data from memory cache

    private fun observeByUserId(userId: UserId): Flow<UserSettings?> =
        userSettingsDao.observeByUserId(userId).map { it?.fromEntity() }

    private suspend fun insertOrUpdate(settings: UserSettings) =
        userSettingsDao.insertOrUpdate(settings.toEntity())

    private suspend fun delete(userId: UserId) =
        userSettingsDao.delete(userId)

    private suspend fun deleteAll() =
        userSettingsDao.deleteAll()

    override suspend fun setUsername(sessionUserId: SessionUserId, username: String): Boolean =
        apiProvider.get<UserSettingsApi>(sessionUserId).invoke {
            setUsername(SetUsernameRequest(username = username)).isSuccess()
        }.valueOrThrow

    override suspend fun updateUserSettings(userSettings: UserSettings) {
        insertOrUpdate(userSettings)
    }

    override fun getUserSettingsFlow(sessionUserId: SessionUserId, refresh: Boolean): Flow<DataResult<UserSettings>> {
        return store.stream(StoreRequest.cached(sessionUserId, refresh = refresh)).map { it.toDataResult() }
    }

    override suspend fun getUserSettings(sessionUserId: SessionUserId, refresh: Boolean) =
        if (refresh) store.fresh(sessionUserId) else store.get(sessionUserId)

    override suspend fun updateRecoveryEmail(
        sessionUserId: SessionUserId,
        email: String,
        srpProofs: SrpProofs,
        srpSession: String,
        secondFactorCode: String
    ): UserSettings {
        return apiProvider.get<UserSettingsApi>(sessionUserId).invoke {
            val response = updateRecoveryEmail(
                UpdateRecoveryEmailRequest(
                    email = email,
                    twoFactorCode = secondFactorCode,
                    clientEphemeral = srpProofs.clientEphemeral,
                    clientProof = srpProofs.clientProof,
                    srpSession = srpSession
                )
            )
            validateServerProof(response.serverProof, srpProofs.expectedServerProof) { "recovery email update failed" }
            insertOrUpdate(response.settings.fromResponse(sessionUserId))
            getUserSettings(sessionUserId)
        }.valueOrThrow
    }

    override suspend fun updateLoginPassword(
        sessionUserId: SessionUserId,
        srpProofs: SrpProofs,
        srpSession: String,
        secondFactorCode: String,
        auth: Auth
    ): UserSettings {
        return apiProvider.get<UserSettingsApi>(sessionUserId).invoke {
            val response = updateLoginPassword(
                UpdateLoginPasswordRequest(
                    twoFactorCode = secondFactorCode,
                    clientEphemeral = srpProofs.clientEphemeral,
                    clientProof = srpProofs.clientProof,
                    srpSession = srpSession,
                    auth = AuthRequest.from(auth)
                )
            )
            validateServerProof(response.serverProof, srpProofs.expectedServerProof) { "password change failed" }
            insertOrUpdate(response.settings.fromResponse(sessionUserId))
            getUserSettings(sessionUserId)
        }.valueOrThrow
    }
}
