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

package me.proton.core.mailsettings.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import me.proton.core.data.db.Database
import me.proton.core.data.db.migration.DatabaseMigration
import me.proton.core.mailsettings.data.db.dao.MailSettingsDao

interface MailSettingsDatabase : Database {
    fun mailSettingsDao(): MailSettingsDao

    companion object {
        /**
         * - Added Table MailSettingsEntity.
         */
        val MIGRATION_0 = object : DatabaseMigration {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Added Table MailSettingsEntity.
                database.execSQL("CREATE TABLE IF NOT EXISTS `MailSettingsEntity` (`userId` TEXT NOT NULL, `displayName` TEXT, `signature` TEXT, `autoSaveContacts` INTEGER, `composerMode` INTEGER, `messageButtons` INTEGER, `showImages` INTEGER, `showMoved` INTEGER, `viewMode` INTEGER, `viewLayout` INTEGER, `swipeLeft` INTEGER, `swipeRight` INTEGER, `shortcuts` INTEGER, `pmSignature` INTEGER, `numMessagePerPage` INTEGER, `draftMimeType` TEXT, `receiveMimeType` TEXT, `showMimeType` TEXT, `enableFolderColor` INTEGER, `inheritParentFolderColor` INTEGER, `rightToLeft` INTEGER, `attachPublicKey` INTEGER, `sign` INTEGER, `pgpScheme` INTEGER, `promptPin` INTEGER, `stickyLabels` INTEGER, `confirmLink` INTEGER, PRIMARY KEY(`userId`), FOREIGN KEY(`userId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            }
        }
    }
}
