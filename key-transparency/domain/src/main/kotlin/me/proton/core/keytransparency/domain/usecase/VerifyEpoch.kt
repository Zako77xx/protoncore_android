/*
 * Copyright (c) 2022 Proton Technologies AG
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

package me.proton.core.keytransparency.domain.usecase

import me.proton.core.keytransparency.domain.entity.Epoch

/**
 * Verify that the epoch has a legitimate
 * certificate for the information claimed in the epoch.
 */
public interface VerifyEpoch {

    /**
     * Verify the [epoch] certificate, and
     * verify that the epoch information are
     * matching the certificate.
     *
     * @param epoch: The epoch to verify.
     *
     * @throws KeyTransparencyException if the epoch is invalid.
     *
     * @return the NotBefore value for the certificate.
     */
    public suspend operator fun invoke(epoch: Epoch): Long
}
