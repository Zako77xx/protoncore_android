/*
 * Copyright (c) 2020 Proton Technologies AG
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

package me.proton.core.contact.data.repository

import com.dropbox.android.external.store4.Fetcher
import com.dropbox.android.external.store4.SourceOfTruth
import com.dropbox.android.external.store4.Store
import com.dropbox.android.external.store4.StoreBuilder
import com.dropbox.android.external.store4.StoreRequest
import com.dropbox.android.external.store4.fresh
import com.dropbox.android.external.store4.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.core.contact.domain.entity.Contact
import me.proton.core.contact.domain.entity.ContactEmail
import me.proton.core.contact.domain.entity.ContactId
import me.proton.core.contact.domain.entity.ContactWithCards
import me.proton.core.contact.domain.repository.ContactLocalDataSource
import me.proton.core.contact.domain.repository.ContactRemoteDataSource
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.data.arch.toDataResult
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.arch.mapSuccess
import me.proton.core.domain.entity.UserId

class ContactRepositoryImpl(
    private val remoteDataSource: ContactRemoteDataSource,
    private val localDataSource: ContactLocalDataSource
) : ContactRepository {

    private data class ContactStoreKey(val userId: UserId, val contactId: ContactId)

    private val contactWithCardsStore: Store<ContactStoreKey, ContactWithCards> = StoreBuilder.from(
        fetcher = Fetcher.of { key: ContactStoreKey ->
            remoteDataSource.getContactWithCards(key.userId, key.contactId)
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { contactStoreKey -> localDataSource.observeContact(contactStoreKey.contactId) },
            writer = { _, contactWithCards -> localDataSource.mergeContactWithCards(contactWithCards) },
            delete = { key -> localDataSource.deleteContacts(key.contactId) },
            deleteAll = localDataSource::deleteAllContacts
        )
    ).build()

    private val allContactsStore: Store<UserId, List<Contact>> = StoreBuilder.from(
        fetcher = Fetcher.of { userId: UserId ->
            remoteDataSource.getAllContacts(userId)
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { userId ->
                localDataSource.observeAllContacts(userId).map { contacts ->
                    // Force refresh if no contact in db
                    contacts.ifEmpty { null }
                }
            },
            writer = { _, contacts -> localDataSource.mergeContacts(*contacts.toTypedArray()) },
            delete = { userId -> localDataSource.deleteAllContacts(userId) },
            deleteAll = localDataSource::deleteAllContacts
        )
    ).build()

    override fun observeContactWithCards(
        userId: UserId,
        contactId: ContactId,
        refresh: Boolean
    ): Flow<DataResult<ContactWithCards>> {
        val key = ContactStoreKey(userId, contactId)
        return contactWithCardsStore.stream(StoreRequest.cached(key, refresh)).map { it.toDataResult() }
    }

    override suspend fun getContactWithCards(
        userId: UserId,
        contactId: ContactId,
        fresh: Boolean
    ): ContactWithCards {
        val key = ContactStoreKey(userId, contactId)
        return if (fresh) contactWithCardsStore.fresh(key) else contactWithCardsStore.get(key)
    }

    override fun observeAllContacts(userId: UserId, refresh: Boolean): Flow<DataResult<List<Contact>>> {
        return allContactsStore.stream(StoreRequest.cached(userId, refresh)).map { it.toDataResult() }
    }

    override suspend fun getAllContacts(userId: UserId, fresh: Boolean): List<Contact> {
        return if (fresh) allContactsStore.fresh(userId) else allContactsStore.get(userId)
    }

    override fun observeAllContactEmails(
        userId: UserId,
        refresh: Boolean
    ): Flow<DataResult<List<ContactEmail>>> {
        return observeAllContacts(userId, refresh).mapSuccess { contactsResult ->
            DataResult.Success(
                source = contactsResult.source,
                value = contactsResult.value.flatMap { it.contactEmails }
            )
        }
    }

    override suspend fun getAllContactEmails(userId: UserId, fresh: Boolean): List<ContactEmail> {
        return getAllContacts(userId, fresh).flatMap { it.contactEmails }
    }
}
