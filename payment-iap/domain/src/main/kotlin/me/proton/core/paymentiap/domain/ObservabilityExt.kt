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

package me.proton.core.paymentiap.domain

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import me.proton.core.observability.domain.metrics.common.GiapStatus
import me.proton.core.paymentiap.domain.repository.BillingClientError
import me.proton.core.util.kotlin.CoreLogger

public fun Result<*>.toGiapStatus(): GiapStatus {
    if (isSuccess && getOrNull() == null) return GiapStatus.notFound
    return when (val throwable = exceptionOrNull()) {
        null -> GiapStatus.success
        is BillingClientError -> throwable.responseCode.toGiapStatus()
        else -> {
            CoreLogger.e(LogTag.GIAP_ERROR, throwable, "Unknown BillingClientError.")
            GiapStatus.unknown
        }
    }
}

public fun BillingResult.toGiapStatus(): GiapStatus = responseCode.toGiapStatus()

private fun Int?.toGiapStatus(): GiapStatus =
    when (this) {
        BillingClient.BillingResponseCode.OK -> GiapStatus.success
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> GiapStatus.billingUnavailable
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> GiapStatus.serviceDisconnected
        BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> GiapStatus.serviceTimeout
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> GiapStatus.serviceUnavailable
        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> GiapStatus.developerError
        BillingClient.BillingResponseCode.ERROR -> GiapStatus.googlePlayError
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> GiapStatus.featureNotSupported
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> GiapStatus.itemAlreadyOwned
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> GiapStatus.itemNotOwned
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> GiapStatus.itemUnavailable
        BillingClient.BillingResponseCode.USER_CANCELED -> GiapStatus.userCanceled
        null -> GiapStatus.statusNull
        else -> GiapStatus.unknown
    }
