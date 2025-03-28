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

package me.proton.core.paymentiap.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.domain.entity.AppStore
import me.proton.core.observability.domain.ObservabilityContext
import me.proton.core.observability.domain.ObservabilityManager
import me.proton.core.observability.domain.metrics.CheckoutGiapBillingLaunchBillingTotal
import me.proton.core.observability.domain.metrics.CheckoutGiapBillingProductQueryTotal
import me.proton.core.observability.domain.metrics.CheckoutGiapBillingPurchaseTotal
import me.proton.core.observability.domain.metrics.CheckoutGiapBillingQuerySubscriptionsTotal
import me.proton.core.observability.domain.metrics.CheckoutGiapBillingUnredeemedTotalV1
import me.proton.core.observability.domain.metrics.toPurchaseGiapStatus
import me.proton.core.payment.domain.entity.GooglePurchase
import me.proton.core.payment.domain.entity.GooglePurchaseToken
import me.proton.core.payment.domain.usecase.FindUnacknowledgedGooglePurchase
import me.proton.core.payment.presentation.entity.BillingInput
import me.proton.core.paymentiap.domain.LogTag
import me.proton.core.paymentiap.domain.entity.unwrap
import me.proton.core.paymentiap.domain.repository.BillingClientError
import me.proton.core.paymentiap.domain.repository.GoogleBillingRepository
import me.proton.core.paymentiap.domain.toGiapStatus
import me.proton.core.presentation.savedstate.state
import me.proton.core.presentation.viewmodel.ProtonViewModel
import me.proton.core.util.kotlin.CoreLogger
import me.proton.core.util.kotlin.coroutine.launchWithResultContext
import javax.inject.Inject

@HiltViewModel
internal class BillingIAPViewModel @Inject constructor(
    private val billingRepository: GoogleBillingRepository,
    private val findUnacknowledgedGooglePurchase: FindUnacknowledgedGooglePurchase,
    override val manager: ObservabilityManager,
    savedStateHandle: SavedStateHandle
) : ProtonViewModel(), ObservabilityContext {

    private var billingInput: BillingInput? by savedStateHandle.state(null)

    private val _billingIAPState = MutableStateFlow<State>(State.Initializing)
    val billingIAPState: StateFlow<State> = _billingIAPState.asStateFlow()

    sealed class State {
        object Initializing : State()
        object QueryingProductDetails : State()
        object PurchaseStarted : State()
        data class UnredeemedPurchase(val purchase: GooglePurchase) : State()

        sealed class Success : State() {
            data class GoogleProductDetails(
                val amount: Long,
                val currency: String,
                val formattedPriceAndCurrency: String,
            ) : Success()

            object PurchaseFlowLaunched : State()

            data class PurchaseSuccess(
                val productId: String,
                val purchaseToken: GooglePurchaseToken,
                val orderID: String,
                val customerId: String,
                val billingInput: BillingInput
            ) : Success()
        }

        sealed class Error : State() {
            object BillingClientUnavailable : State()
            object BillingClientDisconnected : State()
            sealed class ProductDetailsError : State() {
                object ResponseCode : ProductDetailsError()
                object Price : ProductDetailsError()
                object ProductMismatch : ProductDetailsError()
                data class Message(val error: String? = null) : ProductDetailsError()
            }

            sealed class ProductPurchaseError : State() {
                object UserCancelled : ProductPurchaseError()
                object ItemAlreadyOwned : ProductDetailsError()
                data class Message(
                    val error: Int? = null,
                    @BillingClient.BillingResponseCode val code: Int? = null
                ) : ProductPurchaseError()

                object IncorrectCustomerId : ProductPurchaseError()
            }
        }
    }

    private lateinit var selectedProduct: ProductDetails

    init {
        listenForPurchases()
    }

    override fun onCleared() {
        super.onCleared()
        billingRepository.destroy()
    }

    fun queryProductDetails(googlePlanName: String) = viewModelScope.launchWithResultContext {
        onResultEnqueue("getProductDetails") { CheckoutGiapBillingProductQueryTotal(toGiapStatus()) }

        flow {
            emit(State.QueryingProductDetails)

            val productDetails = billingRepository.getProductDetails(googlePlanName)
            if (productDetails == null) {
                emit(State.Error.ProductDetailsError.ProductMismatch)
                return@flow
            }

            selectedProduct = productDetails

            val price = productDetails.getProductPrice()
            if (price == null) {
                emit(State.Error.ProductDetailsError.Price)
            } else {
                emit(
                    State.Success.GoogleProductDetails(
                        amount = price.priceAmountMicros,
                        currency = price.priceCurrencyCode,
                        formattedPriceAndCurrency = price.formattedPrice,
                    )
                )
            }
        }.catch { throwable ->
            val error = when (throwable) {
                is BillingClientError -> {
                    when (throwable.responseCode) {
                        null,
                        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> State.Error.BillingClientUnavailable
                        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> State.Error.BillingClientDisconnected
                        else -> State.Error.ProductDetailsError.ResponseCode
                    }
                }

                else -> State.Error.ProductDetailsError.Message(throwable.message)
            }
            _billingIAPState.tryEmit(error)
        }.onEach { subscriptionState ->
            _billingIAPState.tryEmit(subscriptionState)
        }.collect()
    }

    fun makePurchase(activity: FragmentActivity, input: BillingInput): Job =
        viewModelScope.launchWithResultContext {
            billingInput = input

            onResultEnqueue("querySubscriptionPurchases") {
                CheckoutGiapBillingQuerySubscriptionsTotal(
                    toGiapStatus()
                )
            }
            onResultEnqueue("launchBillingFlow") {
                CheckoutGiapBillingLaunchBillingTotal(
                    toGiapStatus()
                )
            }

            flow {
                require(this@BillingIAPViewModel::selectedProduct.isInitialized) {
                    "Product must be set before making the purchase."
                }

                val unredeemedPurchase =
                    findUnacknowledgedGooglePurchase.byProduct(selectedProduct.productId)
                if (unredeemedPurchase != null) {
                    manager.enqueue(CheckoutGiapBillingUnredeemedTotalV1())
                    emit(State.UnredeemedPurchase(unredeemedPurchase))
                } else {
                    launchBillingFlow(activity, input.googleCustomerId)
                }
            }.catch { error ->
                _billingIAPState.tryEmit(State.Error.ProductDetailsError.Message(error.message))
            }.onEach { subscriptionState ->
                _billingIAPState.tryEmit(subscriptionState)
            }.collect()
        }

    fun redeemExistingPurchase(purchase: GooglePurchase) = viewModelScope.launch {
        val state = onSubscriptionPurchased(purchase.unwrap())
        _billingIAPState.value = state
    }

    /** Launches Google billing flow. */
    private suspend fun FlowCollector<State>.launchBillingFlow(
        activity: FragmentActivity,
        customerId: String
    ) {
        val offer = requireNotNull(selectedProduct.subscriptionOfferDetails?.firstOrNull())
        emit(State.PurchaseStarted)
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(selectedProduct)
                .setOfferToken(offer.offerToken)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .setObfuscatedAccountId(customerId)
            .build()

        billingRepository.launchBillingFlow(activity, billingFlowParams)
        emit(State.Success.PurchaseFlowLaunched)
    }

    private fun listenForPurchases() = billingRepository.purchaseUpdated.onEach {
        onPurchasesUpdated(it.first, it.second)
    }.launchIn(viewModelScope)

    private fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        val state = when {
            billingResult.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty() -> {
                val purchase = purchases.firstOrNull { purchase ->
                    purchase.products.contains(selectedProduct.productId)
                }
                if (purchase?.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    onSubscriptionPurchased(purchase)
                } else {
                    State.Error.ProductPurchaseError.Message(code = billingResult.responseCode)
                }
            }

            billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                State.Error.ProductPurchaseError.UserCancelled

            billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                State.Error.ProductPurchaseError.ItemAlreadyOwned

            else ->
                State.Error.ProductPurchaseError.Message(code = billingResult.responseCode)
        }
        _billingIAPState.value = state
        enqueuePurchaseResult(billingResult, state)
    }

    private fun onSubscriptionPurchased(purchase: Purchase): State {
        val billingInput = requireNotNull(billingInput) {
            "BillingInput should not be null, after a subscription has been purchased."
        }
        val purchaseCustomerId = requireNotNull(purchase.accountIdentifiers?.obfuscatedAccountId) {
            "The purchase should contain a customer ID."
        }

        return if (purchaseCustomerId.isEmpty()) {
            State.Error.ProductPurchaseError.IncorrectCustomerId
        } else {
            State.Success.PurchaseSuccess(
                productId = selectedProduct.productId,
                purchaseToken = GooglePurchaseToken(purchase.purchaseToken),
                orderID = purchase.orderId,
                customerId = purchaseCustomerId,
                billingInput = billingInput
            )
        }
    }

    private fun enqueuePurchaseResult(billingResult: BillingResult, state: State) {
        val event = if (state == State.Error.ProductPurchaseError.IncorrectCustomerId) {
            CheckoutGiapBillingPurchaseTotal(CheckoutGiapBillingPurchaseTotal.PurchaseStatus.incorrectCustomerId)
        } else {
            CheckoutGiapBillingPurchaseTotal(billingResult.toGiapStatus().toPurchaseGiapStatus())
        }
        manager.enqueue(event)
    }
}

private val BillingInput.googleCustomerId: String
    get() = requireNotNull(plan.vendors[AppStore.GooglePlay]?.customerId) {
        "Missing Vendor data for Google Play."
    }

private fun ProductDetails.getProductPrice(): ProductDetails.PricingPhase? =
    subscriptionOfferDetails?.getOrNull(0)?.pricingPhases?.pricingPhaseList?.getOrNull(0)
