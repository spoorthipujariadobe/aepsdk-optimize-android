/*
 Copyright 2021 Adobe. All rights reserved.
 This file is licensed to you under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under
 the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 OF ANY KIND, either express or implied. See the License for the specific language
 governing permissions and limitations under the License.
 */
package com.adobe.marketing.optimizetutorial

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.adobe.marketing.mobile.edge.identity.AuthenticatedState
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.edge.identity.IdentityItem
import com.adobe.marketing.mobile.edge.identity.IdentityMap
import com.adobe.marketing.mobile.optimize.DecisionScope
import com.adobe.marketing.mobile.optimize.Offer
import com.adobe.marketing.mobile.optimize.OfferType
import com.adobe.marketing.optimizetutorial.viewmodels.MainViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun OffersView(viewModel: MainViewModel) {
    var listState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        if (viewModel.propositionStateMap.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = 0.85f)
                    .verticalScroll(state = rememberScrollState())
            ) {
                TargetOffersView()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = 0.85f),
                state = listState
            ) {
                items(items = viewModel.propositionStateMap.keys.toList().sorted(), key = { item -> item }, itemContent = { item ->
                    when(item) {
                        viewModel.targetMboxDecisionScope?.name -> {
                            OffersSectionText(sectionName = "Target Offers")
                            TargetOffersView(offers = viewModel.propositionStateMap[viewModel.targetMboxDecisionScope?.name]?.offers, listState = listState)
                        }
                    }
                })
            }
        }


        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.005f)
                .background(color = Color.Gray)
        )

        Surface(
            elevation = 1.5.dp
        ) {
            Box(modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                ) {
                Button(modifier = Modifier.align(Alignment.CenterStart), onClick = {
                    /* Optimize Tutorial: CODE SECTION 6/10 BEGINS
                    viewModel.updateDecisionScopes()
                    //* Optimize Tutorial: CODE SECTION (optional) 6/10 BEGINS
                    // Send a custom Identity in IdentityMap as primary identifier to Edge network in personalization query request.
                    val identityMap = IdentityMap()
                    identityMap.addItem(IdentityItem("1111", AuthenticatedState.AUTHENTICATED, true), "userCRMID")
                    Identity.updateIdentities(identityMap)
                    // Optimize Tutorial: CODE SECTION (optional) 6 ENDS */

                    val decisionScopeList = arrayListOf<DecisionScope>()
                    viewModel.targetMboxDecisionScope?.also { decisionScopeList.add(it) }

                    val data = mutableMapOf<String, Any>()
                    val targetParams = mutableMapOf<String, String>()

                    if(viewModel.targetMboxDecisionScope?.name?.isNotEmpty() == true) {
                        viewModel.targetParamsMbox.forEach {
                            if (it.key.isNotEmpty() && it.value.isNotEmpty()) {
                                targetParams[it.key] = it.value
                            }
                        }

                        viewModel.targetParamsProfile.forEach {
                            if(!it.key.isNullOrEmpty() && !it.value.isNullOrEmpty()){
                                targetParams[it.key] = it.value
                            }
                        }

                        if(viewModel.isValidOrder){
                            targetParams["orderId"] = viewModel.textTargetOrderId
                            targetParams["orderTotal"] = viewModel.textTargetOrderTotal
                            targetParams["purchasedProductIds"] = viewModel.textTargetPurchaseId
                        }

                        if(viewModel.isValidProduct){
                            targetParams["productId"] = viewModel.textTargetProductId
                        targetParams["categoryId"] = viewModel.textTargetProductCategoryId
                        }

                        if (targetParams.isNotEmpty()) {
                            data["__adobe"] = mapOf<String, Any>(Pair("target", targetParams))
                        }
                    }
                    data["dataKey"] = "5678"
                    viewModel.updatePropositions(
                        decisionScopes = decisionScopeList,
                        xdm = mapOf(Pair("xdmKey", "1234")),
                        data = data
                    )
                    // Optimize Tutorial: CODE SECTION 6 ENDS */
                }) {
                    Text(
                        text = "Update \n Propositions",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.button
                    )
                }

                Button(modifier = Modifier.align(Alignment.Center), onClick = {
                    /* Optimize Tutorial: CODE SECTION 7/10 BEGINS
                    viewModel.updateDecisionScopes()
                    val decisionScopeList = arrayListOf<DecisionScope>()
                    viewModel.targetMboxDecisionScope?.also { decisionScopeList.add(it) }

                    viewModel.getPropositions(decisionScopes = decisionScopeList)
                    // Optimize Tutorial: CODE SECTION 7 ENDS */
                }) {
                    Text(
                        text = "Get \n Propositions",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.button
                    )
                }

                Button(modifier = Modifier.align(Alignment.CenterEnd), onClick = {
                    /* Optimize Tutorial: CODE SECTION 8/10 BEGINS
                    viewModel.clearCachedPropositions()
                    // Optimize Tutorial: CODE SECTION 8 ENDS */
                }) {
                    Text(
                        text = "Clear \n Propositions",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.button
                    )
                }
            }
        }
    }
}



@Composable
fun OffersSectionText(sectionName: String) {
    Text(
        text = sectionName,
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.LightGray)
            .padding(10.dp),
        textAlign = TextAlign.Left,
        style = MaterialTheme.typography.subtitle1
    )
}

@Composable
fun HtmlOfferWebView(html: String, onclick: (() -> Unit)? = null) {
    AndroidView(modifier = Modifier
        .padding(vertical = 20.dp)
        .fillMaxWidth()
        .wrapContentHeight(), factory = { context ->
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setOnTouchListener { _, _ ->
                onclick?.invoke()
                true
            }
        }
    }, update = {
            it.loadData(html, "text/html", "UTF-8")
        }
    )
}

@Composable
fun TextOffer(offer: Offer, onclick: (() -> Unit)? = null) {
    Text(
        text = offer.content,
        modifier = Modifier
            .padding(vertical = 20.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable {
                onclick?.invoke()
            },
        textAlign = TextAlign.Center)
}

@Composable
fun TargetOffersView(offers: List<Offer>? = null, listState: LazyListState? = null) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()) {
        /* Optimize Tutorial: CODE SECTION 9/10 BEGINS
        offers?.onEach {
            when (it.type) {
                OfferType.HTML -> HtmlOfferWebView(html = it.content,
                    onclick = { it.tapped() })
                else -> TextOffer(offer = it,
                    onclick = { it.tapped() })
            }
        } ?: Text(
                text = "Placeholder Target Text",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 20.dp)
                    .height(100.dp),
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center
            )
    }

    listState?.also {
        LaunchedEffect(it) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { lazyListItemInfo -> lazyListItemInfo.key } }
                .map { visibleItemKeys -> visibleItemKeys.contains(offers?.get(0)?.proposition?.scope ?: "") }
                .distinctUntilChanged()
                .filter { result -> result }
                .collect {
                    offers?.forEach {offer ->
                        offer.displayed()
                    }
                }
        }
    // Optimize Tutorial: CODE SECTION 9 ENDS */
    }
}
