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

package me.proton.core.observability.domain.metrics

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import me.proton.core.observability.domain.entity.SchemaId
import me.proton.core.observability.domain.metrics.common.UnlockUserLabels
import me.proton.core.observability.domain.metrics.common.UnlockUserStatus

@Serializable
@Schema(description = "Unlocking the user just after the signup.")
@SchemaId("https://proton.me/android_core_signup_unlockUser_total_v1.schema.json")
public data class SignupUnlockUserTotalV1(
    override val Labels: UnlockUserLabels,
    @Required override val Value: Long = 1
) : ObservabilityData() {
    public constructor(status: UnlockUserStatus) : this(UnlockUserLabels(status))
}
