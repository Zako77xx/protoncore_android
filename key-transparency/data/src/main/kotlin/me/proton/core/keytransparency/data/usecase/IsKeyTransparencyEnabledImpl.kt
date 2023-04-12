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

package me.proton.core.keytransparency.data.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.core.keytransparency.data.R
import me.proton.core.keytransparency.domain.usecase.GetHostType
import me.proton.core.keytransparency.domain.usecase.HostType
import me.proton.core.keytransparency.domain.usecase.IsKeyTransparencyEnabled
import javax.inject.Inject

public class IsKeyTransparencyEnabledImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getHostType: GetHostType
) : IsKeyTransparencyEnabled {

    private val localFeatureFlag = context.resources.getBoolean(
        R.bool.core_feature_key_transparency_enabled
    )

    override suspend fun invoke(): Boolean = localFeatureFlag && getHostType() != HostType.Other
}
