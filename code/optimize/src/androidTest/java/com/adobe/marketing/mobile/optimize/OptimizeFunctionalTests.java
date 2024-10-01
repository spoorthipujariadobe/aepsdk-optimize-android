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

package com.adobe.marketing.mobile.optimize;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.AdobeCallbackWithError;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Edge;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.MonitorExtension;
import com.adobe.marketing.mobile.TestHelper;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import kotlin.text.Charsets;

@RunWith(AndroidJUnit4.class)
public class OptimizeFunctionalTests {

    private interface NetworkMonitor {
        void call(NetworkRequest request);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(new TestHelper.SetupCoreRule())
                    .around(new TestHelper.RegisterMonitorExtensionRule());

    private NetworkMonitor networkMonitor;
    private int responseCode = 0;
    private String responseBody = null;
    private String errorBody = null;
    private String networkRequestBody = null;
    private Map<String, String> networkRequestHeaders = null;
    // reusable latches
    private CountDownLatch waitForCallback = null;
    private CountDownLatch waitForNetworkCall = null;

    @Before
    public void setup() throws Exception {
        setupNetwork();
        MobileCore.setApplication(ApplicationProvider.getApplicationContext());
        MobileCore.setLogLevel(LoggingMode.VERBOSE);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        MobileCore.registerExtensions(
                Arrays.asList(Optimize.EXTENSION, Edge.EXTENSION, Identity.EXTENSION), o -> countDownLatch.countDown());
        Assert.assertTrue(countDownLatch.await(1000, TimeUnit.MILLISECONDS));
        TestHelper.resetTestExpectations();

        // set latches
        resetLatches();
    }

    @After
    public void tearDown() {
        NamedCollection configDataStore =
                ServiceProvider.getInstance()
                        .getDataStoreService()
                        .getNamedCollection(OptimizeTestConstants.CONFIG_DATA_STORE);
        if (configDataStore != null) {
            configDataStore.removeAll();
        }
        resetNetworkVariables();
    }

    private void resetNetworkVariables() {
        ServiceProvider.getInstance().setNetworkService(null);
        responseCode = 0;
        responseBody = null;
        errorBody = null;
    }

    private void resetLatches() {
        waitForCallback = new CountDownLatch(1);
        waitForNetworkCall = new CountDownLatch(1);
    }

    private void setupNetwork() {
        ServiceProvider.getInstance().setNetworkService((request, callback) -> {
            HttpConnecting connection = null;
            String url = request.getUrl();

            if (url.contains("edge.adobedc.net/ee/v1/interact")) {
                connection = new MockedHttpConnecting(responseCode, responseBody, errorBody);
                if (networkMonitor != null) {
                    networkMonitor.call(request);
                }
            } else {
                connection = new MockedHttpConnecting(responseCode, responseBody, errorBody);
            }

            if (callback != null && connection != null) {
                callback.call(connection);
            } else {
                // If no callback is passed by the client, close the connection.
                if (connection != null) {
                    connection.close();
                }
            }
        });
    }

    private class MockedHttpConnecting implements HttpConnecting {

        public MockedHttpConnecting(final int responseCode, final String responseBody, final String errorBody) {
            this(responseCode, responseBody, errorBody, null);
        }
        public MockedHttpConnecting(
                final int responseCode,
                final String responseBody,
                final String errorBody,
                final Map<String, String> headers
        ) {
            mockGetResponseCode = responseCode;
            mockResponseBody = responseBody;
            mockErrorBody = errorBody;
            mockGetResponsePropertyValues = headers;
        }

        public int getInputStreamCalledTimes = 0;
        private final String mockResponseBody;

        @Override
        public InputStream getInputStream() {
            getInputStreamCalledTimes += 1;

            if (mockResponseBody == null) {
                return null;
            }

            return new ByteArrayInputStream(mockResponseBody.getBytes());
        }

        public int getErrorStreamCalledTimes = 0;
        private final String mockErrorBody;

        @Override
        public InputStream getErrorStream() {
            getErrorStreamCalledTimes += 1;

            if (mockErrorBody == null) {
                return null;
            }

            return new ByteArrayInputStream(mockErrorBody.getBytes());
        }

        public int getResponseCodeCalledTimes = 0;
        private int mockGetResponseCode = 0;

        @Override
        public int getResponseCode() {
            getResponseCodeCalledTimes += 1;
            return mockGetResponseCode;
        }

        @Override
        public String getResponseMessage() {
            return null;
        }

        private final Map<String, String> mockGetResponsePropertyValues;

        @Override
        public String getResponsePropertyValue(final String value) {
            if (mockGetResponsePropertyValues == null) {
                return null;
            }

            return mockGetResponsePropertyValues.get(value);
        }

        public int closeCalledTimes = 0;

        @Override
        public void close() {
            closeCalledTimes += 1;
        }
    }

    // 1
    @Test
    public void testExtensionVersion() {
        assertEquals(OptimizeTestConstants.EXTENSION_VERSION, Optimize.extensionVersion());
    }

    @Test
    public void testUpdatePropositions_timeoutError() throws Exception {
        // Setup
        final String decisionScopeName = "decisionScope";
        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        // Action
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeName)),
                null,
                null,
                new AdobeCallbackWithOptimizeError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AEPOptimizeError error) {
                        Assert.fail(OptimizeConstants.ErrorData.Timeout.DETAIL);
                        assertEquals(
                                OptimizeConstants.ErrorData.Timeout.STATUS, error.getStatus());
                        assertEquals(
                                OptimizeConstants.ErrorData.Timeout.TITLE, error.getTitle());
                        assertEquals(
                                OptimizeConstants.ErrorData.Timeout.DETAIL, error.getDetail());
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        Assert.assertNull(decisionScopePropositionMap);
                    }
                });
    }

    @Test
    public void testUpdatePropositions_validDecisionScope_MockedSuccessNetworkResponse() throws InterruptedException, IOException {
        // Setup

        // setup network capture-er
        networkMonitor = request -> {
            networkRequestBody = new String(request.getBody(), Charsets.UTF_8);
            networkRequestHeaders = request.getHeaders();
            waitForNetworkCall.countDown();
        };

        responseCode = 200;
        responseBody = "\u0000{\"requestId\": \"FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF\",\"handle\": [{\"payload\": [{\"id\": \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"scope\": \"eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==\",\"activity\": {\"id\": \"xcore:offer-activity:1111111111111111\",\"etag\": \"8\"},\"placement\": {\"id\": \"xcore:offer-placement:1111111111111111\",\"etag\": \"1\"},\"items\": [{\"id\": \"xcore:personalized-offer:2222222222222222\",\"etag\": \"39\",\"score\": 1,\"schema\": \"https:\\/\\/ns.adobe.com\\/experience\\/offer-management\\/content-component-text\",\"data\": {\"id\": \"xcore:personalized-offer:2222222222222222\",\"format\": \"text\\/plain\",\"language\": [\"en-us\"],\"content\": \"This is a plain text content!\",\"characteristics\": {\"mobile\": \"true\"}}}]}],\"type\":\"personalization:decisions\",\"eventIndex\":0}]}";


        final String decisionScopeName =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        // Action
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeName)),
                null,
                null,
                new AdobeCallbackWithOptimizeError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AEPOptimizeError error) {
                        waitForCallback.countDown();
                    }

                    @Override
                    public void call(Map<DecisionScope, OptimizeProposition> decisionScopeOptimizePropositionMap) {
                        waitForCallback.countDown();
                    }
                });
        waitForCallback.await(15, TimeUnit.SECONDS);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        Assert.assertNotNull(eventsListEdge);
        assertEquals(1, eventsListOptimize.size());
        assertEquals(1, eventsListEdge.size());
        Event event = eventsListOptimize.get(0);
        Map<String, Object> eventData = event.getEventData();
        assertEquals(OptimizeTestConstants.EventType.OPTIMIZE, event.getType());
        assertEquals(OptimizeTestConstants.EventSource.REQUEST_CONTENT, event.getSource());
        Assert.assertTrue(eventData.size() > 0);
        assertEquals("updatepropositions", eventData.get("requesttype"));
        List<Map<String, String>> decisionScopes =
                (List<Map<String, String>>) eventData.get("decisionscopes");
        assertEquals(1, decisionScopes.size());
        assertEquals(decisionScopeName, decisionScopes.get(0).get("name"));

        // Validating Event data of Edge Request event
        Event edgeEvent = eventsListEdge.get(0);
        Assert.assertNotNull(edgeEvent);
        Map<String, Object> edgeEventData = edgeEvent.getEventData();
        Assert.assertNotNull(edgeEventData);
        Assert.assertTrue(edgeEventData.size() > 0);
        assertEquals(
                "personalization.request",
                ((Map<String, Object>) edgeEventData.get("xdm")).get("eventType"));
        Map<String, Object> personalizationMap =
                (Map<String, Object>)
                        ((Map<String, Object>) edgeEventData.get("query")).get("personalization");
        List<String> decisionScopeList = (List<String>) personalizationMap.get("decisionScopes");
        Assert.assertNotNull(decisionScopeList);
        assertEquals(1, decisionScopeList.size());
        assertEquals(decisionScopeName, decisionScopeList.get(0));

        // todo: add assertions for optimize response event and propositions returned in the callbac

    }

    @Test
    public void testUpdatePropositions_validDecisionScope_MockedErrorNetworkResponse() throws InterruptedException, IOException {
        // Setup

        // setup network capture-er
        networkMonitor = request -> {
            networkRequestBody = new String(request.getBody(), Charsets.UTF_8);
            networkRequestHeaders = request.getHeaders();
            waitForNetworkCall.countDown();
        };

        responseCode = 408;
        errorBody = "\u0000{ \"requestId\":\"FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF\", \"handle\":[], \"errors\":[{ \"type\":\"EXEG-0201-408\", \"status\":408, \"title\":\"Request timed out. Please try again.\"} ]}";

        final String decisionScopeName =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        // Action
        final AdobeError[] receivedError = new AdobeError[1];
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeName)),
                null,
                null,
                new AdobeCallbackWithOptimizeError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AEPOptimizeError error) {
                        receivedError[0] = error.getAdobeError();
                        waitForCallback.countDown();
                    }

                    @Override
                    public void call(Map<DecisionScope, OptimizeProposition> decisionScopeOptimizePropositionMap) {
                        waitForCallback.countDown();
                    }
                });
        waitForCallback.await(15, TimeUnit.SECONDS);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        Assert.assertNotNull(eventsListEdge);
        assertEquals(1, eventsListOptimize.size());
        assertEquals(1, eventsListEdge.size());
        Event event = eventsListOptimize.get(0);
        Map<String, Object> eventData = event.getEventData();
        assertEquals(OptimizeTestConstants.EventType.OPTIMIZE, event.getType());
        assertEquals(OptimizeTestConstants.EventSource.REQUEST_CONTENT, event.getSource());
        Assert.assertTrue(eventData.size() > 0);
        assertEquals("updatepropositions", eventData.get("requesttype"));
        List<Map<String, String>> decisionScopes =
                (List<Map<String, String>>) eventData.get("decisionscopes");
        assertEquals(1, decisionScopes.size());
        assertEquals(decisionScopeName, decisionScopes.get(0).get("name"));

        // Validating Event data of Edge Request event
        Event edgeEvent = eventsListEdge.get(0);
        Assert.assertNotNull(edgeEvent);
        Map<String, Object> edgeEventData = edgeEvent.getEventData();
        Assert.assertNotNull(edgeEventData);
        Assert.assertTrue(edgeEventData.size() > 0);
        assertEquals(
                "personalization.request",
                ((Map<String, Object>) edgeEventData.get("xdm")).get("eventType"));
        Map<String, Object> personalizationMap =
                (Map<String, Object>)
                        ((Map<String, Object>) edgeEventData.get("query")).get("personalization");
        List<String> decisionScopeList = (List<String>) personalizationMap.get("decisionScopes");
        Assert.assertNotNull(decisionScopeList);
        assertEquals(1, decisionScopeList.size());
        assertEquals(decisionScopeName, decisionScopeList.get(0));

        // todo: add assertions for errors received
        // this currently fails due to error in adding AEPOptimizeError to event data map
        assertEquals(
                AdobeError.CALLBACK_TIMEOUT.getErrorCode(), receivedError[0].getErrorCode());
    }

    // 2a
    @Test
    public void testUpdatePropositions_validDecisionScope() throws InterruptedException {
        // Setup
        final String decisionScopeName =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        // Action
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeName)), null, null);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        Assert.assertNotNull(eventsListEdge);
        assertEquals(1, eventsListOptimize.size());
        assertEquals(1, eventsListEdge.size());
        Event event = eventsListOptimize.get(0);
        Map<String, Object> eventData = event.getEventData();
        assertEquals(OptimizeTestConstants.EventType.OPTIMIZE, event.getType());
        assertEquals(OptimizeTestConstants.EventSource.REQUEST_CONTENT, event.getSource());
        Assert.assertTrue(eventData.size() > 0);
        assertEquals("updatepropositions", eventData.get("requesttype"));
        List<Map<String, String>> decisionScopes =
                (List<Map<String, String>>) eventData.get("decisionscopes");
        assertEquals(1, decisionScopes.size());
        assertEquals(decisionScopeName, decisionScopes.get(0).get("name"));

        // Validating Event data of Edge Request event
        Event edgeEvent = eventsListEdge.get(0);
        Assert.assertNotNull(edgeEvent);
        Map<String, Object> edgeEventData = edgeEvent.getEventData();
        Assert.assertNotNull(edgeEventData);
        Assert.assertTrue(edgeEventData.size() > 0);
        assertEquals(
                "personalization.request",
                ((Map<String, Object>) edgeEventData.get("xdm")).get("eventType"));
        Map<String, Object> personalizationMap =
                (Map<String, Object>)
                        ((Map<String, Object>) edgeEventData.get("query")).get("personalization");
        List<String> decisionScopeList = (List<String>) personalizationMap.get("decisionScopes");
        Assert.assertNotNull(decisionScopeList);
        assertEquals(1, decisionScopeList.size());
        assertEquals(decisionScopeName, decisionScopeList.get(0));
    }

    // 2b
    @Test
    public void testUpdatePropositions_validNonEncodedDecisionScope() throws InterruptedException {
        // Setup
        final String activityId = "xcore:offer-activity:1111111111111111";
        final String placementId = "xcore:offer-placement:1111111111111111";

        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        // Action
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(activityId, placementId)), null, null);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        Assert.assertNotNull(eventsListEdge);
        assertEquals(1, eventsListOptimize.size());
        assertEquals(1, eventsListEdge.size());
        Event event = eventsListOptimize.get(0);
        Map<String, Object> eventData = event.getEventData();
        assertEquals(OptimizeTestConstants.EventType.OPTIMIZE, event.getType());
        assertEquals(OptimizeTestConstants.EventSource.REQUEST_CONTENT, event.getSource());
        Assert.assertTrue(eventData.size() > 0);
        assertEquals("updatepropositions", eventData.get("requesttype"));
        List<Map<String, String>> decisionScopes =
                (List<Map<String, String>>) eventData.get("decisionscopes");
        assertEquals(1, decisionScopes.size());
        assertEquals(
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==",
                decisionScopes.get(0).get("name"));

        // Validating Event data of Edge Request event
        Event edgeEvent = eventsListEdge.get(0);
        Assert.assertNotNull(edgeEvent);
        Map<String, Object> edgeEventData = edgeEvent.getEventData();
        Assert.assertNotNull(edgeEventData);
        Assert.assertTrue(edgeEventData.size() > 0);
        assertEquals(
                "personalization.request",
                ((Map<String, Object>) edgeEventData.get("xdm")).get("eventType"));
        Map<String, Object> personalizationMap =
                (Map<String, Object>)
                        ((Map<String, Object>) edgeEventData.get("query")).get("personalization");
        List<String> decisionScopeList = (List<String>) personalizationMap.get("decisionScopes");
        Assert.assertNotNull(decisionScopeList);
        assertEquals(1, decisionScopeList.size());
        assertEquals(
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==",
                decisionScopeList.get(0));
    }

    // 2c
    @Test
    public void testUpdatePropositions_validNonEncodedDecisionScopeWithItemCount()
            throws InterruptedException {
        // Setup
        final String activityId = "xcore:offer-activity:1111111111111111";
        final String placementId = "xcore:offer-placement:1111111111111111";
        final int itemCount = 30;

        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        // Action
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(activityId, placementId, itemCount)),
                null,
                null);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        Assert.assertNotNull(eventsListEdge);
        assertEquals(1, eventsListOptimize.size());
        assertEquals(1, eventsListEdge.size());
        Event event = eventsListOptimize.get(0);
        Map<String, Object> eventData = event.getEventData();
        assertEquals(OptimizeTestConstants.EventType.OPTIMIZE, event.getType());
        assertEquals(OptimizeTestConstants.EventSource.REQUEST_CONTENT, event.getSource());
        Assert.assertTrue(eventData.size() > 0);
        assertEquals("updatepropositions", eventData.get("requesttype"));
        List<Map<String, String>> decisionScopes =
                (List<Map<String, String>>) eventData.get("decisionscopes");
        assertEquals(1, decisionScopes.size());
        assertEquals(
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEiLCJpdGVtQ291bnQiOjMwfQ==",
                decisionScopes.get(0).get("name"));

        // Validating Event data of Edge Request event
        Event edgeEvent = eventsListEdge.get(0);
        Assert.assertNotNull(edgeEvent);
        Map<String, Object> edgeEventData = edgeEvent.getEventData();
        Assert.assertNotNull(edgeEventData);
        Assert.assertTrue(edgeEventData.size() > 0);
        assertEquals(
                "personalization.request",
                ((Map<String, Object>) edgeEventData.get("xdm")).get("eventType"));
        Map<String, Object> personalizationMap =
                (Map<String, Object>)
                        ((Map<String, Object>) edgeEventData.get("query")).get("personalization");
        List<String> decisionScopeList = (List<String>) personalizationMap.get("decisionScopes");
        Assert.assertNotNull(decisionScopeList);
        assertEquals(1, decisionScopeList.size());
        assertEquals(
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEiLCJpdGVtQ291bnQiOjMwfQ==",
                decisionScopeList.get(0));
    }

    // 3
    @Test
    public void testUpdatePropositions_validDecisionScopeWithXdmAndDataAndDatasetId()
            throws InterruptedException {
        // Setup
        final String decisionScopeName =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        final String optimizeDatasetId = "111111111111111111111111";
        Map<String, Object> xdmMap =
                new HashMap<String, Object>() {
                    {
                        put("MyXDMKey", "MyXDMValue");
                    }
                };

        Map<String, Object> dataMap =
                new HashMap<String, Object>() {
                    {
                        put("MyDataKey", "MyDataValue");
                    }
                };

        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        configData.put("optimize.datasetId", optimizeDatasetId);
        updateConfiguration(configData);

        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeName)), xdmMap, dataMap);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        Assert.assertNotNull(eventsListEdge);
        assertEquals(1, eventsListOptimize.size());
        assertEquals(1, eventsListEdge.size());
        Event event = eventsListOptimize.get(0);
        Map<String, Object> eventData = event.getEventData();
        assertEquals(OptimizeTestConstants.EventType.OPTIMIZE, event.getType());
        assertEquals(OptimizeTestConstants.EventSource.REQUEST_CONTENT, event.getSource());
        Assert.assertTrue(eventData.size() > 0);
        assertEquals(
                "MyXDMValue", ((Map<String, String>) eventData.get("xdm")).get("MyXDMKey"));
        assertEquals(
                "MyDataValue", ((Map<String, String>) eventData.get("data")).get("MyDataKey"));
        assertEquals("updatepropositions", eventData.get("requesttype"));
        List<Map<String, String>> decisionScopes =
                (List<Map<String, String>>) eventData.get("decisionscopes");
        assertEquals(1, decisionScopes.size());
        assertEquals(decisionScopeName, decisionScopes.get(0).get("name"));

        // Validating Event data of Edge Request event
        Event edgeEvent = eventsListEdge.get(0);
        Assert.assertNotNull(edgeEvent);
        Map<String, Object> edgeEventData = edgeEvent.getEventData();
        Assert.assertNotNull(edgeEventData);
        Assert.assertTrue(edgeEventData.size() > 0);
        assertEquals(optimizeDatasetId, edgeEventData.get("datasetId"));
        assertEquals(
                "personalization.request",
                ((Map<String, Object>) edgeEventData.get("xdm")).get("eventType"));
        assertEquals(
                "MyXDMValue", ((Map<String, Object>) edgeEventData.get("xdm")).get("MyXDMKey"));
        Map<String, Object> personalizationMap =
                (Map<String, Object>)
                        ((Map<String, Object>) edgeEventData.get("query")).get("personalization");
        List<String> decisionScopeList = (List<String>) personalizationMap.get("decisionScopes");
        Assert.assertNotNull(decisionScopeList);
        assertEquals(1, decisionScopeList.size());
        assertEquals(decisionScopeName, decisionScopeList.get(0));
        assertEquals(
                "MyDataValue", ((Map<String, Object>) edgeEventData.get("data")).get("MyDataKey"));
    }

    // 4
    @Test
    public void testUpdatePropositions_multipleValidDecisionScope() throws InterruptedException {
        // Setup
        final String decisionScopeName1 =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        final String decisionScopeName2 = "MyMbox";
        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        // Action
        Optimize.updatePropositions(
                Arrays.asList(
                        new DecisionScope(decisionScopeName1),
                        new DecisionScope(decisionScopeName2)),
                null,
                null);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        assertEquals(1, eventsListOptimize.size());
        Assert.assertNotNull(eventsListEdge);
        assertEquals(1, eventsListEdge.size());
        Event event = eventsListOptimize.get(0);
        Map<String, Object> eventData = event.getEventData();
        assertEquals(OptimizeTestConstants.EventType.OPTIMIZE, event.getType());
        assertEquals(OptimizeTestConstants.EventSource.REQUEST_CONTENT, event.getSource());
        Assert.assertTrue(eventData.size() > 0);
        assertEquals("updatepropositions", eventData.get("requesttype"));
        List<Map<String, String>> decisionScopes =
                (List<Map<String, String>>) eventData.get("decisionscopes");
        assertEquals(2, decisionScopes.size());
        assertEquals(decisionScopeName1, decisionScopes.get(0).get("name"));
        assertEquals(decisionScopeName2, decisionScopes.get(1).get("name"));

        // Validating Event data of Edge Request event
        Event edgeEvent = eventsListEdge.get(0);
        Assert.assertNotNull(edgeEvent);
        Map<String, Object> edgeEventData = edgeEvent.getEventData();
        Assert.assertNotNull(edgeEventData);
        Assert.assertTrue(edgeEventData.size() > 0);
        assertEquals(
                "personalization.request",
                ((Map<String, Object>) edgeEventData.get("xdm")).get("eventType"));
        Map<String, Object> personalizationMap =
                (Map<String, Object>)
                        ((Map<String, Object>) edgeEventData.get("query")).get("personalization");
        List<String> decisionScopeList = (List<String>) personalizationMap.get("decisionScopes");
        Assert.assertNotNull(decisionScopeList);
        assertEquals(2, decisionScopeList.size());
        assertEquals(decisionScopeName1, decisionScopeList.get(0));
        assertEquals(decisionScopeName2, decisionScopeList.get(1));
    }

    // 5
    @Test
    public void testUpdatePropositions_ConfigNotAvailable() throws InterruptedException {
        // Setup
        final String decisionScopeName =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        clearUpdatedConfiguration();

        // Action
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeName)), null, null);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        assertEquals(1, eventsListOptimize.size());
        Assert.assertTrue(eventsListEdge.isEmpty());
    }

    // 6
    @Test
    public void testUpdatePropositions_validAndInvalidDecisionScopes() throws InterruptedException {
        // Setup
        final String decisionScopeName1 =
                "eyJhY3Rpdml0eUlkIjoiIiwicGxhY2VtZW50SWQiOiJ4Y29yZTpvZmZlci1wbGFjZW1lbnQ6MTExMTExMTExMTExMTExMSJ9";
        final String decisionScopeName2 = "MyMbox";
        Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        // Action
        Optimize.updatePropositions(
                Arrays.asList(
                        new DecisionScope(decisionScopeName1),
                        new DecisionScope(decisionScopeName2)),
                null,
                null);

        // Assert
        List<Event> eventsListOptimize =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(eventsListOptimize);
        assertEquals(1, eventsListOptimize.size());
        Assert.assertNotNull(eventsListEdge);
        assertEquals(1, eventsListEdge.size());
        Event event = eventsListOptimize.get(0);
        Map<String, Object> eventData = event.getEventData();
        assertEquals(OptimizeTestConstants.EventType.OPTIMIZE, event.getType());
        assertEquals(OptimizeTestConstants.EventSource.REQUEST_CONTENT, event.getSource());
        Assert.assertTrue(eventData.size() > 0);
        assertEquals("updatepropositions", eventData.get("requesttype"));
        List<Map<String, String>> decisionScopes =
                (List<Map<String, String>>) eventData.get("decisionscopes");
        assertEquals(1, decisionScopes.size());
        assertEquals(decisionScopeName2, decisionScopes.get(0).get("name"));

        // Validating Event data of Edge Request event
        Event edgeEvent = eventsListEdge.get(0);
        Assert.assertNotNull(edgeEvent);
        Map<String, Object> edgeEventData = edgeEvent.getEventData();
        Assert.assertNotNull(edgeEventData);
        Assert.assertTrue(edgeEventData.size() > 0);
        assertEquals(
                "personalization.request",
                ((Map<String, Object>) edgeEventData.get("xdm")).get("eventType"));
        Map<String, Object> personalizationMap =
                (Map<String, Object>)
                        ((Map<String, Object>) edgeEventData.get("query")).get("personalization");
        List<String> decisionScopeList = (List<String>) personalizationMap.get("decisionScopes");
        Assert.assertNotNull(decisionScopeList);
        assertEquals(1, decisionScopeList.size());
        assertEquals(decisionScopeName2, decisionScopeList.get(0));
    }

    // 7a
    @Test
    public void testGetPropositions_decisionScopeInCache()
            throws InterruptedException, IOException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        final String decisionScopeString =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeString)), null, null);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        assertEquals(1, eventsListEdge.size());
        Event edgeEvent = eventsListEdge.get(0);
        final String requestEventId = edgeEvent.getUniqueIdentifier();
        Assert.assertFalse(requestEventId.isEmpty());

        // Send Edge Response event
        final String edgeResponseData =
                "{\n"
                        + "                                  \"payload\": [\n"
                        + "                                    {\n"
                        + "                                        \"id\":"
                        + " \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\n"
                        + "                                        \"scope\": \""
                        + decisionScopeString
                        + "\",\n"
                        + "                                        \"activity\": {\n"
                        + "                                            \"etag\": \"8\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-activity:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"placement\": {\n"
                        + "                                            \"etag\": \"1\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-placement:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"items\": [\n"
                        + "                                            {\n"
                        + "                                                \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                \"etag\": \"10\",\n"
                        + "                                                \"score\": 1,\n"
                        + "                                                \"schema\":"
                        + " \"https://ns.adobe.com/experience/offer-management/content-component-html\",\n"
                        + "                                                \"data\": {\n"
                        + "                                                    \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                    \"format\":"
                        + " \"text/html\",\n"
                        + "                                                    \"content\":"
                        + " \"<h1>This is HTML content</h1>\",\n"
                        + "                                                    \"characteristics\":"
                        + " {\n"
                        + "                                                        \"testing\":"
                        + " \"true\"\n"
                        + "                                                    }\n"
                        + "                                                }\n"
                        + "                                            }\n"
                        + "                                        ]\n"
                        + "                                    }\n"
                        + "                                  ],\n"
                        + "                                \"requestEventId\":\""
                        + requestEventId
                        + "\",\n"
                        + "                                \"requestId\":"
                        + " \"BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB\",\n"
                        + "                                \"type\":"
                        + " \"personalization:decisions\"\n"
                        + "                              }";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> eventData =
                objectMapper.readValue(
                        edgeResponseData, new TypeReference<Map<String, Object>>() {});

        Event event =
                new Event.Builder(
                                "AEP Response Event Handle",
                                OptimizeTestConstants.EventType.EDGE,
                                OptimizeTestConstants.EventSource.PERSONALIZATION)
                        .setEventData(eventData)
                        .build();

        // Action
        MobileCore.dispatchEvent(event);

        Thread.sleep(1000);

        // Send completion event
        Map<String, Object> completionEventData =
                new HashMap<String, Object>() {
                    {
                        put("completedUpdateRequestForEventId", requestEventId);
                    }
                };
        Event completionEvent =
                new Event.Builder(
                                "Optimize Update Propositions Complete",
                                OptimizeTestConstants.EventType.OPTIMIZE,
                                OptimizeTestConstants.EventSource.CONTENT_COMPLETE)
                        .setEventData(completionEventData)
                        .build();
        MobileCore.dispatchEvent(completionEvent);

        Thread.sleep(1000);
        TestHelper.resetTestExpectations();
        DecisionScope decisionScope = new DecisionScope(decisionScopeString);
        final Map<DecisionScope, OptimizeProposition> propositionMap = new HashMap<>();
        final ADBCountDownLatch countDownLatch = new ADBCountDownLatch(1);
        Optimize.getPropositions(
                Collections.singletonList(decisionScope),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch.countDown();
                    }
                });

        countDownLatch.await(1, TimeUnit.SECONDS);
        // Assertions
        List<Event> optimizeResponseEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.RESPONSE_CONTENT);

        Assert.assertNotNull(optimizeResponseEventsList);
        assertEquals(1, optimizeResponseEventsList.size());
        Assert.assertNull(optimizeResponseEventsList.get(0).getEventData().get("responseerror"));
        assertEquals(1, propositionMap.size());
        OptimizeProposition optimizeProposition = propositionMap.get(decisionScope);
        Assert.assertNotNull(optimizeProposition);
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", optimizeProposition.getId());
        assertEquals(
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==",
                optimizeProposition.getScope());
        assertEquals(1, optimizeProposition.getOffers().size());

        Offer offer = optimizeProposition.getOffers().get(0);
        assertEquals("xcore:personalized-offer:1111111111111111", offer.getId());
        assertEquals("10", offer.getEtag());
        assertEquals(1, offer.getScore());
        assertEquals(
                "https://ns.adobe.com/experience/offer-management/content-component-html",
                offer.getSchema());
        assertEquals(OfferType.HTML, offer.getType());
        assertEquals("<h1>This is HTML content</h1>", offer.getContent());
        assertEquals(1, offer.getCharacteristics().size());
        assertEquals("true", offer.getCharacteristics().get("testing"));
    }
    // 7a
    @Test
    public void testGetPropositions_defaultContentItem() throws InterruptedException, IOException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        final String decisionScopeString = "someDecisionScope";
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeString)), null, null);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        assertEquals(1, eventsListEdge.size());
        Event edgeEvent = eventsListEdge.get(0);
        final String requestEventId = edgeEvent.getUniqueIdentifier();
        Assert.assertFalse(requestEventId.isEmpty());

        // Send Edge Response event
        final String edgeResponseData =
                "{\r\n"
                    + "      \"payload\": [\r\n"
                    + "        {\r\n"
                    + "          \"scopeDetails\": {\r\n"
                    + "            \"characteristics\": {\r\n"
                    + "              \"eventToken\": \"someEventToken\"\r\n"
                    + "            },\r\n"
                    + "            \"activity\": {\r\n"
                    + "              \"id\": \"716226\"\r\n"
                    + "            },\r\n"
                    + "            \"strategies\": [\r\n"
                    + "              {\r\n"
                    + "                \"trafficType\": \"0\",\r\n"
                    + "                \"step\": \"entry\"\r\n"
                    + "              },\r\n"
                    + "              {\r\n"
                    + "                \"trafficType\": \"0\",\r\n"
                    + "                \"step\": \"display\"\r\n"
                    + "              }\r\n"
                    + "            ],\r\n"
                    + "            \"correlationID\": \"716226:0:0\",\r\n"
                    + "            \"decisionProvider\": \"TGT\",\r\n"
                    + "            \"experience\": {\r\n"
                    + "              \"id\": \"0\"\r\n"
                    + "            }\r\n"
                    + "          },\r\n"
                    + "          \"scope\": \"someDecisionScope\",\r\n"
                    + "          \"id\": \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\r\n"
                    + "          \"items\": [\r\n"
                    + "            {\r\n"
                    + "              \"schema\":"
                    + " \"https://ns.adobe.com/personalization/default-content-item\",\r\n"
                    + "              \"meta\": {\r\n"
                    + "                \"activity.name\": \"Some Test Activity\",\r\n"
                    + "                \"profile.timeNow\": \"1722212083855\",\r\n"
                    + "                \"profile.audienceUserNeed\": \"\",\r\n"
                    + "                \"profile.language\": \"\",\r\n"
                    + "                \"experience.name\": \"Default Content\",\r\n"
                    + "                \"profile.site\": \"\",\r\n"
                    + "                \"profile.url\": \"\",\r\n"
                    + "                \"profile.subjects\": \"\",\r\n"
                    + "                \"profile.path\": \"\",\r\n"
                    + "                \"profile.subjectPrimary\": \"\",\r\n"
                    + "                \"profile.translatedTabbedShelfTitle\": \"Discover SBS in 63"
                    + " Languages\",\r\n"
                    + "                \"profile.environment\": \"production\",\r\n"
                    + "                \"profile.brandName\": \"\",\r\n"
                    + "                \"profile.audioChannelLastPlayed\": \"\",\r\n"
                    + "                \"profile.type\": \"\"\r\n"
                    + "              },\r\n"
                    + "              \"id\": \"0\"\r\n"
                    + "            }\r\n"
                    + "          ]\r\n"
                    + "        }\r\n"
                    + "      ],\r\n"
                    + "      \"requestId\": \"someRequestId\",\r\n"
                    + "      \"requestEventId\": \""
                        + requestEventId
                        + "\",\r\n      \"type\": \"personalization:decisions\"\r\n    }";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> eventData =
                objectMapper.readValue(
                        edgeResponseData, new TypeReference<Map<String, Object>>() {});

        Event event =
                new Event.Builder(
                                "AEP Response Event Handle",
                                OptimizeTestConstants.EventType.EDGE,
                                OptimizeTestConstants.EventSource.PERSONALIZATION)
                        .setEventData(eventData)
                        .build();

        // Action
        MobileCore.dispatchEvent(event);

        Thread.sleep(1000);

        // Send completion event
        Map<String, Object> completionEventData =
                new HashMap<String, Object>() {
                    {
                        put("completedUpdateRequestForEventId", requestEventId);
                    }
                };
        Event completionEvent =
                new Event.Builder(
                                "Optimize Update Propositions Complete",
                                OptimizeTestConstants.EventType.OPTIMIZE,
                                OptimizeTestConstants.EventSource.CONTENT_COMPLETE)
                        .setEventData(completionEventData)
                        .build();
        MobileCore.dispatchEvent(completionEvent);

        Thread.sleep(1000);
        TestHelper.resetTestExpectations();
        DecisionScope decisionScope = new DecisionScope(decisionScopeString);
        final Map<DecisionScope, OptimizeProposition> propositionMap = new HashMap<>();
        final ADBCountDownLatch countDownLatch = new ADBCountDownLatch(1);
        Optimize.getPropositions(
                Collections.singletonList(decisionScope),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch.countDown();
                    }
                });

        countDownLatch.await(1, TimeUnit.SECONDS);
        // Assertions
        List<Event> optimizeResponseEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.RESPONSE_CONTENT);

        Assert.assertNotNull(optimizeResponseEventsList);

        // 1 additional event is being sent from handleUpdatePropositions() to provide callback for
        // updatePropositons()
        assertEquals(2, optimizeResponseEventsList.size());

        assertEquals(1, propositionMap.size());
        OptimizeProposition optimizeProposition = propositionMap.get(decisionScope);
        Assert.assertNotNull(optimizeProposition);
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", optimizeProposition.getId());
        assertEquals("someDecisionScope", optimizeProposition.getScope());
        assertEquals(1, optimizeProposition.getOffers().size());

        Offer offer = optimizeProposition.getOffers().get(0);
        assertEquals("0", offer.getId());
        assertEquals(null, offer.getEtag());
        assertEquals(0, offer.getScore());
        assertEquals(
                "https://ns.adobe.com/personalization/default-content-item", offer.getSchema());
        assertEquals(OfferType.UNKNOWN, offer.getType());
        assertEquals("", offer.getContent());
    }

    // 7b
    @Test
    public void testGetPropositions_decisionScopeInCacheFromTargetResponseWithClickTracking()
            throws ClassCastException, InterruptedException, IOException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        final String decisionScopeString = "myMbox1";
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeString)), null, null);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        assertEquals(1, eventsListEdge.size());
        Event edgeEvent = eventsListEdge.get(0);
        final String requestEventId = edgeEvent.getUniqueIdentifier();
        Assert.assertFalse(requestEventId.isEmpty());

        // Send Edge response event
        final String edgeResponseData =
                "{\n"
                        + "                                  \"payload\": [\n"
                        + "                                    {\n"
                        + "                                        \"id\":"
                        + " \"AT:eyJhY3Rpdml0eUlkIjoiMTExMTExIiwiZXhwZXJpZW5jZUlkIjoiMCJ9\",\n"
                        + "                                        \"scope\": \""
                        + decisionScopeString
                        + "\",\n"
                        + "                                        \"scopeDetails\": {\n"
                        + "                                            \"decisionProvider\":"
                        + " \"TGT\",\n"
                        + "                                            \"activity\": {\n"
                        + "                                               \"id\": \"111111\"\n"
                        + "                                             },\n"
                        + "                                            \"experience\": {\n"
                        + "                                               \"id\": \"0\"\n"
                        + "                                             },\n"
                        + "                                            \"strategies\": [\n"
                        + "                                               {\n"
                        + "                                                  \"step\": \"entry\",\n"
                        + "                                                  \"algorithmID\":"
                        + " \"0\",\n"
                        + "                                                  \"trafficType\":"
                        + " \"0\"\n"
                        + "                                               },\n"
                        + "                                               {\n"
                        + "                                                  \"step\":"
                        + " \"display\",\n"
                        + "                                                  \"algorithmID\":"
                        + " \"0\",\n"
                        + "                                                  \"trafficType\":"
                        + " \"0\"\n"
                        + "                                               }\n"
                        + "                                             ],\n"
                        + "                                            \"characteristics\": {\n"
                        + "                                               \"stateToken\":"
                        + " \"SGFZpwAqaqFTayhAT2xsgzG3+2fw4m+O9FK8c0QoOHfxVkH1ttT1PGBX3/jV8a5uFF0fAox6CXpjJ1PGRVQBjHl9Zc6mRxY9NQeM7rs/3Es1RHPkzBzyhpVS6eg9q+kw\",\n"
                        + "                                               \"eventTokens\": {\n"
                        + "                                                   \"display\":"
                        + " \"MmvRrL5aB4Jz36JappRYg2qipfsIHvVzTQxHolz2IpSCnQ9Y9OaLL2gsdrWQTvE54PwSz67rmXWmSnkXpSSS2Q==\",\n"
                        + "                                                   \"click\":"
                        + " \"EZDMbI2wmAyGcUYLr3VpmA==\"\n"
                        + "                                               }\n"
                        + "                                             }\n"
                        + "                                        },\n"
                        + "                                        \"items\": [\n"
                        + "                                            {\n"
                        + "                                                \"id\": \"0\",\n"
                        + "                                                \"schema\":"
                        + " \"https://ns.adobe.com/personalization/json-content-item\",\n"
                        + "                                                \"data\": {\n"
                        + "                                                    \"id\": \"0\",\n"
                        + "                                                    \"format\":"
                        + " \"application/json\",\n"
                        + "                                                    \"content\": {\n"
                        + "                                                       \"device\":"
                        + " \"mobile\"\n"
                        + "                                                     }\n"
                        + "                                                }\n"
                        + "                                            },\n"
                        + "                                            {\n"
                        + "                                                \"id\": \"111111\",\n"
                        + "                                                \"data\": {\n"
                        + "                                                    \"type\":"
                        + " \"click\",\n"
                        + "                                                    \"format\":"
                        + " \"application/vnd.adobe.target.metric\"\n"
                        + "                                                }\n"
                        + "                                            }\n"
                        + "                                        ]\n"
                        + "                                    }\n"
                        + "                                  ],\n"
                        + "                                \"requestEventId\":\""
                        + requestEventId
                        + "\",\n"
                        + "                                \"requestId\":"
                        + " \"BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB\",\n"
                        + "                                \"type\":"
                        + " \"personalization:decisions\"\n"
                        + "                              }";

        final ObjectMapper objectMapper = new ObjectMapper();
        final Map<String, Object> eventData =
                objectMapper.readValue(
                        edgeResponseData, new TypeReference<Map<String, Object>>() {});

        final Event event =
                new Event.Builder(
                                "AEP Response Event Handle",
                                OptimizeTestConstants.EventType.EDGE,
                                OptimizeTestConstants.EventSource.PERSONALIZATION)
                        .setEventData(eventData)
                        .build();

        // Action
        MobileCore.dispatchEvent(event);

        Thread.sleep(1000);

        // Send completion event
        Map<String, Object> completionEventData =
                new HashMap<String, Object>() {
                    {
                        put("completedUpdateRequestForEventId", requestEventId);
                    }
                };
        Event completionEvent =
                new Event.Builder(
                                "Optimize Update Propositions Complete",
                                OptimizeTestConstants.EventType.OPTIMIZE,
                                OptimizeTestConstants.EventSource.CONTENT_COMPLETE)
                        .setEventData(completionEventData)
                        .build();
        MobileCore.dispatchEvent(completionEvent);

        Thread.sleep(1000);
        TestHelper.resetTestExpectations();
        final DecisionScope decisionScope = new DecisionScope(decisionScopeString);
        final Map<DecisionScope, OptimizeProposition> propositionMap = new HashMap<>();
        final ADBCountDownLatch countDownLatch = new ADBCountDownLatch(1);
        Optimize.getPropositions(
                Collections.singletonList(decisionScope),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch.countDown();
                    }
                });

        countDownLatch.await(1, TimeUnit.SECONDS);
        // Assertions
        final List<Event> optimizeResponseEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.RESPONSE_CONTENT);
        Assert.assertNotNull(optimizeResponseEventsList);
        assertEquals(1, optimizeResponseEventsList.size());
        Assert.assertNull(optimizeResponseEventsList.get(0).getEventData().get("responseerror"));
        assertEquals(1, propositionMap.size());

        final OptimizeProposition optimizeProposition = propositionMap.get(decisionScope);
        Assert.assertNotNull(optimizeProposition);
        assertEquals(
                "AT:eyJhY3Rpdml0eUlkIjoiMTExMTExIiwiZXhwZXJpZW5jZUlkIjoiMCJ9",
                optimizeProposition.getId());
        assertEquals("myMbox1", optimizeProposition.getScope());

        final Map<String, Object> scopeDetails = optimizeProposition.getScopeDetails();
        assertEquals(5, scopeDetails.size());
        assertEquals("TGT", scopeDetails.get("decisionProvider"));
        final Map<String, Object> activity = (Map<String, Object>) scopeDetails.get("activity");
        Assert.assertNotNull(activity);
        assertEquals(1, activity.size());
        assertEquals("111111", activity.get("id"));
        final Map<String, Object> experience = (Map<String, Object>) scopeDetails.get("experience");
        Assert.assertNotNull(experience);
        assertEquals(1, experience.size());
        assertEquals("0", experience.get("id"));
        final List<Map<String, Object>> strategies =
                (List<Map<String, Object>>) scopeDetails.get("strategies");
        Assert.assertNotNull(strategies);
        assertEquals(2, strategies.size());
        final Map<String, Object> strategy0 = strategies.get(0);
        Assert.assertNotNull(strategy0);
        assertEquals(3, strategy0.size());
        assertEquals("entry", strategy0.get("step"));
        assertEquals("0", strategy0.get("algorithmID"));
        assertEquals("0", strategy0.get("trafficType"));
        final Map<String, Object> strategy1 = strategies.get(1);
        Assert.assertNotNull(strategy1);
        assertEquals(3, strategy1.size());
        assertEquals("display", strategy1.get("step"));
        assertEquals("0", strategy1.get("algorithmID"));
        assertEquals("0", strategy1.get("trafficType"));
        final Map<String, Object> characteristics =
                (Map<String, Object>) scopeDetails.get("characteristics");
        Assert.assertNotNull(characteristics);
        assertEquals(2, characteristics.size());
        assertEquals(
                "SGFZpwAqaqFTayhAT2xsgzG3+2fw4m+O9FK8c0QoOHfxVkH1ttT1PGBX3/jV8a5uFF0fAox6CXpjJ1PGRVQBjHl9Zc6mRxY9NQeM7rs/3Es1RHPkzBzyhpVS6eg9q+kw",
                characteristics.get("stateToken"));
        final Map<String, Object> eventTokens =
                (Map<String, Object>) characteristics.get("eventTokens");
        Assert.assertNotNull(eventTokens);
        assertEquals(2, eventTokens.size());
        assertEquals(
                "MmvRrL5aB4Jz36JappRYg2qipfsIHvVzTQxHolz2IpSCnQ9Y9OaLL2gsdrWQTvE54PwSz67rmXWmSnkXpSSS2Q==",
                eventTokens.get("display"));
        assertEquals("EZDMbI2wmAyGcUYLr3VpmA==", eventTokens.get("click"));

        assertEquals(1, optimizeProposition.getOffers().size());
        final Offer offer = optimizeProposition.getOffers().get(0);
        assertEquals("0", offer.getId());
        assertEquals(
                "https://ns.adobe.com/personalization/json-content-item", offer.getSchema());
        assertEquals(OfferType.JSON, offer.getType());
        assertEquals("{\"device\":\"mobile\"}", offer.getContent());
        Assert.assertNull(offer.getCharacteristics());
        Assert.assertNull(offer.getLanguage());
    }

    // 8
    @Test
    public void testGetPropositions_notAllDecisionScopesInCache()
            throws IOException, InterruptedException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        final String decisionScopeString =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeString)), null, null);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        assertEquals(1, eventsListEdge.size());
        Event edgeEvent = eventsListEdge.get(0);
        final String requestEventId = edgeEvent.getUniqueIdentifier();
        Assert.assertFalse(requestEventId.isEmpty());

        // Send Edge response event
        final String edgeResponseData =
                "{\n"
                        + "                                  \"payload\": [\n"
                        + "                                    {\n"
                        + "                                        \"id\":"
                        + " \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\n"
                        + "                                        \"scope\": \""
                        + decisionScopeString
                        + "\",\n"
                        + "                                        \"activity\": {\n"
                        + "                                            \"etag\": \"8\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-activity:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"placement\": {\n"
                        + "                                            \"etag\": \"1\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-placement:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"items\": [\n"
                        + "                                            {\n"
                        + "                                                \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                \"etag\": \"10\",\n"
                        + "                                                \"schema\":"
                        + " \"https://ns.adobe.com/experience/offer-management/content-component-html\",\n"
                        + "                                                \"data\": {\n"
                        + "                                                    \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                    \"format\":"
                        + " \"text/html\",\n"
                        + "                                                    \"content\":"
                        + " \"<h1>This is HTML content</h1>\",\n"
                        + "                                                    \"characteristics\":"
                        + " {\n"
                        + "                                                        \"testing\":"
                        + " \"true\"\n"
                        + "                                                    }\n"
                        + "                                                }\n"
                        + "                                            }\n"
                        + "                                        ]\n"
                        + "                                    }\n"
                        + "                                  ],\n"
                        + "                                \"requestEventId\": \""
                        + requestEventId
                        + "\",\n"
                        + "                                \"requestId\":"
                        + " \"BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB\",\n"
                        + "                                \"type\":"
                        + " \"personalization:decisions\"\n"
                        + "                              }";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> eventData = objectMapper.readValue(edgeResponseData, Map.class);

        Event event =
                new Event.Builder(
                                "AEP Response Event Handle",
                                OptimizeTestConstants.EventType.EDGE,
                                OptimizeTestConstants.EventSource.PERSONALIZATION)
                        .setEventData(eventData)
                        .build();

        // Action
        MobileCore.dispatchEvent(event);

        Thread.sleep(1000);

        // Send completion event
        Map<String, Object> completionEventData =
                new HashMap<String, Object>() {
                    {
                        put("completedUpdateRequestForEventId", requestEventId);
                    }
                };
        Event completionEvent =
                new Event.Builder(
                                "Optimize Update Propositions Complete",
                                OptimizeTestConstants.EventType.OPTIMIZE,
                                OptimizeTestConstants.EventSource.CONTENT_COMPLETE)
                        .setEventData(completionEventData)
                        .build();
        MobileCore.dispatchEvent(completionEvent);

        Thread.sleep(1000);
        TestHelper.resetTestExpectations();
        DecisionScope decisionScope1 = new DecisionScope(decisionScopeString);
        DecisionScope decisionScope2 = new DecisionScope("myMbox");
        final Map<DecisionScope, OptimizeProposition> propositionMap = new HashMap<>();
        final ADBCountDownLatch countDownLatch = new ADBCountDownLatch(1);
        Optimize.getPropositions(
                Arrays.asList(decisionScope1, decisionScope2),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch.countDown();
                    }
                });

        countDownLatch.await(1, TimeUnit.SECONDS);
        // Assertions
        List<Event> optimizeResponseEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.RESPONSE_CONTENT);

        Assert.assertNotNull(optimizeResponseEventsList);

        assertEquals(1, optimizeResponseEventsList.size());
        Assert.assertNull(optimizeResponseEventsList.get(0).getEventData().get("responseerror"));
        assertEquals(1, propositionMap.size());

        Assert.assertTrue(propositionMap.containsKey(decisionScope1));
        Assert.assertFalse(
                propositionMap.containsKey(
                        decisionScope2)); // Decision scope myMbox is not present in cache

        OptimizeProposition optimizeProposition = propositionMap.get(decisionScope1);
        Assert.assertNotNull(optimizeProposition);
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", optimizeProposition.getId());
        assertEquals(
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==",
                optimizeProposition.getScope());
        assertEquals(1, optimizeProposition.getOffers().size());

        Offer offer = optimizeProposition.getOffers().get(0);
        assertEquals("xcore:personalized-offer:1111111111111111", offer.getId());
        assertEquals(
                "https://ns.adobe.com/experience/offer-management/content-component-html",
                offer.getSchema());
        assertEquals(OfferType.HTML, offer.getType());
        assertEquals("<h1>This is HTML content</h1>", offer.getContent());
        assertEquals(1, offer.getCharacteristics().size());
        assertEquals("true", offer.getCharacteristics().get("testing"));
    }

    // 9
    @Test
    public void testGetPropositions_noDecisionScopeInCache()
            throws IOException, InterruptedException {
        // setup
        // Send Edge Response event so that propositions will get cached by the Optimize SDK
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);
        final String decisionScopeString =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";

        final String edgeResponseData =
                "{\n"
                        + "                                  \"payload\": [\n"
                        + "                                    {\n"
                        + "                                        \"id\":"
                        + " \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\n"
                        + "                                        \"scope\": \""
                        + decisionScopeString
                        + "\",\n"
                        + "                                        \"activity\": {\n"
                        + "                                            \"etag\": \"8\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-activity:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"placement\": {\n"
                        + "                                            \"etag\": \"1\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-placement:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"items\": [\n"
                        + "                                            {\n"
                        + "                                                \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                \"etag\": \"10\",\n"
                        + "                                                \"schema\":"
                        + " \"https://ns.adobe.com/experience/offer-management/content-component-html\",\n"
                        + "                                                \"data\": {\n"
                        + "                                                    \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                    \"format\":"
                        + " \"text/html\",\n"
                        + "                                                    \"content\":"
                        + " \"<h1>This is HTML content</h1>\",\n"
                        + "                                                    \"characteristics\":"
                        + " {\n"
                        + "                                                        \"testing\":"
                        + " \"true\"\n"
                        + "                                                    }\n"
                        + "                                                }\n"
                        + "                                            }\n"
                        + "                                        ]\n"
                        + "                                    }\n"
                        + "                                  ],\n"
                        + "                                \"requestEventId\":"
                        + " \"AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA\",\n"
                        + "                                \"requestId\":"
                        + " \"BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB\",\n"
                        + "                                \"type\":"
                        + " \"personalization:decisions\"\n"
                        + "                              }";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> eventData =
                objectMapper.readValue(
                        edgeResponseData, new TypeReference<Map<String, Object>>() {});

        Event event =
                new Event.Builder(
                                "AEP Response Event Handle",
                                OptimizeTestConstants.EventType.EDGE,
                                OptimizeTestConstants.EventSource.PERSONALIZATION)
                        .setEventData(eventData)
                        .build();

        // Action
        MobileCore.dispatchEvent(event);

        Thread.sleep(1000);
        TestHelper.resetTestExpectations();
        DecisionScope decisionScope1 = new DecisionScope("myMbox1");
        DecisionScope decisionScope2 = new DecisionScope("myMbox2");
        final Map<DecisionScope, OptimizeProposition> propositionMap = new HashMap<>();
        final ADBCountDownLatch countDownLatch = new ADBCountDownLatch(1);
        Optimize.getPropositions(
                Arrays.asList(decisionScope1, decisionScope2),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch.countDown();
                    }
                });

        countDownLatch.await(1, TimeUnit.SECONDS);
        // Assertions
        List<Event> optimizeResponseEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.RESPONSE_CONTENT);

        Assert.assertNotNull(optimizeResponseEventsList);
        assertEquals(1, optimizeResponseEventsList.size());
        Assert.assertNull(optimizeResponseEventsList.get(0).getEventData().get("responseerror"));
        assertEquals(0, propositionMap.size());

        Assert.assertFalse(
                propositionMap.containsKey(
                        decisionScope1)); // Decision scope myMbox1 is not present in cache
        Assert.assertFalse(
                propositionMap.containsKey(
                        decisionScope2)); // Decision scope myMbox2 is not present in cache
    }

    // 10
    @Test
    public void testGetPropositions_emptyCache() throws InterruptedException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);
        final DecisionScope decisionScope1 =
                new DecisionScope(
                        "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==");
        final DecisionScope decisionScope2 = new DecisionScope("myMbox");

        // Action
        final ADBCountDownLatch countDownLatch = new ADBCountDownLatch(1);
        final Map<DecisionScope, OptimizeProposition> propositionMap = new HashMap<>();
        TestHelper.resetTestExpectations();
        Optimize.getPropositions(
                Arrays.asList(decisionScope1, decisionScope2),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting Propositions.");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch.countDown();
                    }
                });

        countDownLatch.await(1, TimeUnit.SECONDS);
        // Assertions
        List<Event> optimizeResponseEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.RESPONSE_CONTENT);

        Assert.assertNotNull(optimizeResponseEventsList);
        assertEquals(1, optimizeResponseEventsList.size());
        Assert.assertNull(optimizeResponseEventsList.get(0).getEventData().get("responseerror"));
        assertEquals(0, propositionMap.size());

        Assert.assertFalse(propositionMap.containsKey(decisionScope1));
        Assert.assertFalse(propositionMap.containsKey(decisionScope2));
    }

    // 11
    @Test
    public void testTrackPropositions_validPropositionInteractionsForDisplay()
            throws InterruptedException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        Offer offer =
                new Offer.Builder(
                                "xcore:personalized-offer:1111111111111111",
                                OfferType.TEXT,
                                "Text Offer!!")
                        .build();
        OptimizeProposition optimizeProposition =
                new OptimizeProposition(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        Collections.singletonList(offer),
                        "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==",
                        Collections.emptyMap());

        // Action
        TestHelper.resetTestExpectations();
        offer.displayed();

        // Assert
        List<Event> optimizeRequestEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> edgeRequestEventList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(optimizeRequestEventsList);
        assertEquals(1, optimizeRequestEventsList.size());
        Assert.assertNotNull(edgeRequestEventList);
        assertEquals(1, edgeRequestEventList.size());

        Map<String, Object> xdm =
                (Map<String, Object>) edgeRequestEventList.get(0).getEventData().get("xdm");
        assertEquals("decisioning.propositionDisplay", xdm.get("eventType"));

        List<Map<String, Object>> propositionList =
                (List<Map<String, Object>>)
                        ((Map<String, Object>)
                                        ((Map<String, Object>) xdm.get("_experience"))
                                                .get("decisioning"))
                                .get("propositions");
        Assert.assertNotNull(propositionList);
        assertEquals(1, propositionList.size());
        Map<String, Object> propositionData = propositionList.get(0);
        List<Map<String, Object>> propositionsList =
                (List<Map<String, Object>>) propositionData.get("propositions");
        Assert.assertNotNull(propositionList);
        assertEquals(1, propositionList.size());
        Map<String, Object> propositionMap = propositionList.get(0);
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", propositionMap.get("id"));
        assertEquals(
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==",
                propositionMap.get("scope"));
        Assert.assertTrue(((Map<String, Object>) propositionMap.get("scopeDetails")).isEmpty());
        List<Map<String, Object>> itemsList =
                (List<Map<String, Object>>) propositionMap.get("items");
        Assert.assertNotNull(itemsList);
        assertEquals(1, itemsList.size());
        assertEquals(
                "xcore:personalized-offer:1111111111111111", itemsList.get(0).get("id"));
    }

    // 12
    @Test
    public void testTrackPropositions_validPropositionInteractionsForTap()
            throws IOException, InterruptedException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);
        final String testScopeDetails =
                "        {\n"
                        + "        \"decisionProvider\": \"TGT\",\n"
                        + "                \"activity\": {\n"
                        + "        \"id\": \"125589\"\n"
                        + "            },\n"
                        + "        \"experience\": {\n"
                        + "        \"id\": \"0\"\n"
                        + "            },\n"
                        + "        \"strategies\": [\n"
                        + "                {\n"
                        + "        \"algorithmID\": \"0\",\n"
                        + "                \"trafficType\": \"0\"\n"
                        + "                }\n"
                        + "            ]\n"
                        + "        }\n";
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> testDecisionScopesMap =
                objectMapper.readValue(
                        testScopeDetails, new TypeReference<Map<String, Object>>() {});

        Offer offer = new Offer.Builder("246315", OfferType.TEXT, "Text Offer!!").build();
        // Set the proposition soft reference to Offer
        OptimizeProposition optimizeProposition =
                new OptimizeProposition(
                        "AT:eyJhY3Rpdml0eUlkIjoiMTI1NTg5IiwiZXhwZXJpZW5jZUlkIjoiMCJ9",
                        Collections.singletonList(offer),
                        "myMbox",
                        testDecisionScopesMap);

        // Action
        TestHelper.resetTestExpectations();
        offer.tapped();

        // Assert
        List<Event> optimizeRequestEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> edgeRequestEventList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(optimizeRequestEventsList);
        assertEquals(1, optimizeRequestEventsList.size());
        Assert.assertNotNull(edgeRequestEventList);
        assertEquals(1, edgeRequestEventList.size());

        Map<String, Object> xdm =
                (Map<String, Object>) edgeRequestEventList.get(0).getEventData().get("xdm");
        assertEquals("decisioning.propositionInteract", xdm.get("eventType"));

        List<Map<String, Object>> propositionList =
                (List<Map<String, Object>>)
                        ((Map<String, Object>)
                                        ((Map<String, Object>) xdm.get("_experience"))
                                                .get("decisioning"))
                                .get("propositions");
        Assert.assertNotNull(propositionList);
        assertEquals(1, propositionList.size());
        Map<String, Object> propositionData = propositionList.get(0);
        List<Map<String, Object>> propositionsList =
                (List<Map<String, Object>>) propositionData.get("propositions");
        Assert.assertNotNull(propositionList);
        assertEquals(1, propositionList.size());
        Map<String, Object> propositionMap = propositionList.get(0);
        assertEquals(
                "AT:eyJhY3Rpdml0eUlkIjoiMTI1NTg5IiwiZXhwZXJpZW5jZUlkIjoiMCJ9",
                propositionMap.get("id"));
        assertEquals("myMbox", propositionMap.get("scope"));
        assertEquals(testDecisionScopesMap, propositionMap.get("scopeDetails"));
        List<Map<String, Object>> itemsList =
                (List<Map<String, Object>>) propositionMap.get("items");
        Assert.assertNotNull(itemsList);
        assertEquals(1, itemsList.size());
        assertEquals("246315", itemsList.get(0).get("id"));
    }

    // 13
    @Test
    public void testTrackPropositions_validPropositionInteractionsWithDatasetConfig()
            throws InterruptedException, IOException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        configData.put("optimize.datasetId", "111111111111111111111111");
        updateConfiguration(configData);
        final String testDecisionScopes =
                "        {\n"
                        + "        \"decisionProvider\": \"TGT\",\n"
                        + "                \"activity\": {\n"
                        + "        \"id\": \"125589\"\n"
                        + "            },\n"
                        + "        \"experience\": {\n"
                        + "        \"id\": \"0\"\n"
                        + "            },\n"
                        + "        \"strategies\": [\n"
                        + "                {\n"
                        + "        \"algorithmID\": \"0\",\n"
                        + "                \"trafficType\": \"0\"\n"
                        + "                }\n"
                        + "            ]\n"
                        + "        }\n";
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> testDecisionScopesMap =
                objectMapper.readValue(
                        testDecisionScopes, new TypeReference<Map<String, Object>>() {});

        Offer offer = new Offer.Builder("246315", OfferType.TEXT, "Text Offer!!").build();
        OptimizeProposition optimizeProposition =
                new OptimizeProposition(
                        "AT:eyJhY3Rpdml0eUlkIjoiMTI1NTg5IiwiZXhwZXJpZW5jZUlkIjoiMCJ9",
                        Collections.singletonList(offer),
                        "myMbox",
                        testDecisionScopesMap);

        // Action
        TestHelper.resetTestExpectations();
        offer.tapped();

        // Assert
        List<Event> optimizeRequestEventsList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.OPTIMIZE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        List<Event> edgeRequestEventList =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);

        Assert.assertNotNull(optimizeRequestEventsList);
        assertEquals(1, optimizeRequestEventsList.size());
        Assert.assertNotNull(edgeRequestEventList);
        assertEquals(1, edgeRequestEventList.size());

        Map<String, Object> xdm =
                (Map<String, Object>) edgeRequestEventList.get(0).getEventData().get("xdm");
        assertEquals("decisioning.propositionInteract", xdm.get("eventType"));

        List<Map<String, Object>> propositionList =
                (List<Map<String, Object>>)
                        ((Map<String, Object>)
                                        ((Map<String, Object>) xdm.get("_experience"))
                                                .get("decisioning"))
                                .get("propositions");
        Assert.assertNotNull(propositionList);
        assertEquals(1, propositionList.size());
        Assert.assertNotNull(propositionList);
        assertEquals(1, propositionList.size());
        Map<String, Object> propositionMap = propositionList.get(0);
        assertEquals(
                "AT:eyJhY3Rpdml0eUlkIjoiMTI1NTg5IiwiZXhwZXJpZW5jZUlkIjoiMCJ9",
                propositionMap.get("id"));
        assertEquals("myMbox", propositionMap.get("scope"));
        assertEquals(testDecisionScopesMap, propositionMap.get("scopeDetails"));
        List<Map<String, Object>> itemsList =
                (List<Map<String, Object>>) propositionMap.get("items");
        Assert.assertNotNull(itemsList);
        assertEquals(1, itemsList.size());
        assertEquals("246315", itemsList.get(0).get("id"));

        assertEquals(
                "111111111111111111111111",
                edgeRequestEventList.get(0).getEventData().get("datasetId"));
    }

    // 14
    @Test
    public void testClearCachedPropositions() throws InterruptedException, IOException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        final String decisionScopeString =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeString)), null, null);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        assertEquals(1, eventsListEdge.size());
        Event edgeEvent = eventsListEdge.get(0);
        final String requestEventId = edgeEvent.getUniqueIdentifier();
        Assert.assertFalse(requestEventId.isEmpty());

        // Send Edge response event
        final String edgeResponseData =
                "{\n"
                        + "                                  \"payload\": [\n"
                        + "                                    {\n"
                        + "                                        \"id\":"
                        + " \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\n"
                        + "                                        \"scope\": \""
                        + decisionScopeString
                        + "\",\n"
                        + "                                        \"activity\": {\n"
                        + "                                            \"etag\": \"8\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-activity:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"placement\": {\n"
                        + "                                            \"etag\": \"1\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-placement:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"items\": [\n"
                        + "                                            {\n"
                        + "                                                \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                \"etag\": \"10\",\n"
                        + "                                                \"schema\":"
                        + " \"https://ns.adobe.com/experience/offer-management/content-component-html\",\n"
                        + "                                                \"data\": {\n"
                        + "                                                    \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                    \"format\":"
                        + " \"text/html\",\n"
                        + "                                                    \"content\":"
                        + " \"<h1>This is HTML content</h1>\",\n"
                        + "                                                    \"characteristics\":"
                        + " {\n"
                        + "                                                        \"testing\":"
                        + " \"true\"\n"
                        + "                                                    }\n"
                        + "                                                }\n"
                        + "                                            }\n"
                        + "                                        ]\n"
                        + "                                    }\n"
                        + "                                  ],\n"
                        + "                                \"requestEventId\":\""
                        + requestEventId
                        + "\",\n"
                        + "                                \"requestId\":"
                        + " \"BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB\",\n"
                        + "                                \"type\":"
                        + " \"personalization:decisions\"\n"
                        + "                              }";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> eventData =
                objectMapper.readValue(
                        edgeResponseData, new TypeReference<Map<String, Object>>() {});

        Event event =
                new Event.Builder(
                                "AEP Response Event Handle",
                                OptimizeTestConstants.EventType.EDGE,
                                OptimizeTestConstants.EventSource.PERSONALIZATION)
                        .setEventData(eventData)
                        .build();

        // Action
        MobileCore.dispatchEvent(event);

        Thread.sleep(1000);

        // Send completion event
        Map<String, Object> completionEventData =
                new HashMap<String, Object>() {
                    {
                        put("completedUpdateRequestForEventId", requestEventId);
                    }
                };
        Event completionEvent =
                new Event.Builder(
                                "Optimize Update Propositions Complete",
                                OptimizeTestConstants.EventType.OPTIMIZE,
                                OptimizeTestConstants.EventSource.CONTENT_COMPLETE)
                        .setEventData(completionEventData)
                        .build();
        MobileCore.dispatchEvent(completionEvent);

        Thread.sleep(1000);
        TestHelper.resetTestExpectations();
        DecisionScope decisionScope = new DecisionScope(decisionScopeString);
        final Map<DecisionScope, OptimizeProposition> propositionMap = new HashMap<>();
        final ADBCountDownLatch countDownLatch = new ADBCountDownLatch(1);
        Optimize.getPropositions(
                Collections.singletonList(decisionScope),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch.countDown();
                    }
                });

        countDownLatch.await(1, TimeUnit.SECONDS);
        // Assertions
        assertEquals(1, propositionMap.size());

        // Action clear the cache
        Optimize.clearCachedPropositions();

        Thread.sleep(1000);

        final ADBCountDownLatch countDownLatch1 = new ADBCountDownLatch(1);
        propositionMap.clear();
        Optimize.getPropositions(
                Collections.singletonList(decisionScope),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch1.countDown();
                    }
                });
        countDownLatch.await(1, TimeUnit.SECONDS);

        Assert.assertTrue(propositionMap.isEmpty());
    }

    // 15
    @Test
    public void testCoreResetIdentities() throws InterruptedException, IOException {
        // setup
        final Map<String, Object> configData = new HashMap<>();
        configData.put("edge.configId", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        updateConfiguration(configData);

        final String decisionScopeString =
                "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==";
        Optimize.updatePropositions(
                Collections.singletonList(new DecisionScope(decisionScopeString)), null, null);
        List<Event> eventsListEdge =
                TestHelper.getDispatchedEventsWith(
                        OptimizeTestConstants.EventType.EDGE,
                        OptimizeTestConstants.EventSource.REQUEST_CONTENT,
                        1000);
        assertEquals(1, eventsListEdge.size());
        Event edgeEvent = eventsListEdge.get(0);
        final String requestEventId = edgeEvent.getUniqueIdentifier();
        Assert.assertFalse(requestEventId.isEmpty());

        // Send Edge response event
        final String edgeResponseData =
                "{\n"
                        + "                                  \"payload\": [\n"
                        + "                                    {\n"
                        + "                                        \"id\":"
                        + " \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\n"
                        + "                                        \"scope\": \""
                        + decisionScopeString
                        + "\",\n"
                        + "                                        \"activity\": {\n"
                        + "                                            \"etag\": \"8\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-activity:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"placement\": {\n"
                        + "                                            \"etag\": \"1\",\n"
                        + "                                            \"id\":"
                        + " \"xcore:offer-placement:1111111111111111\"\n"
                        + "                                        },\n"
                        + "                                        \"items\": [\n"
                        + "                                            {\n"
                        + "                                                \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                \"etag\": \"10\",\n"
                        + "                                                \"schema\":"
                        + " \"https://ns.adobe.com/experience/offer-management/content-component-html\",\n"
                        + "                                                \"data\": {\n"
                        + "                                                    \"id\":"
                        + " \"xcore:personalized-offer:1111111111111111\",\n"
                        + "                                                    \"format\":"
                        + " \"text/html\",\n"
                        + "                                                    \"content\":"
                        + " \"<h1>This is HTML content</h1>\",\n"
                        + "                                                    \"characteristics\":"
                        + " {\n"
                        + "                                                        \"testing\":"
                        + " \"true\"\n"
                        + "                                                    }\n"
                        + "                                                }\n"
                        + "                                            }\n"
                        + "                                        ]\n"
                        + "                                    }\n"
                        + "                                  ],\n"
                        + "                                \"requestEventId\":\""
                        + requestEventId
                        + "\",\n"
                        + "                                \"requestId\":"
                        + " \"BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB\",\n"
                        + "                                \"type\":"
                        + " \"personalization:decisions\"\n"
                        + "                              }";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> eventData =
                objectMapper.readValue(
                        edgeResponseData, new TypeReference<Map<String, Object>>() {});

        Event event =
                new Event.Builder(
                                "AEP Response Event Handle",
                                OptimizeTestConstants.EventType.EDGE,
                                OptimizeTestConstants.EventSource.PERSONALIZATION)
                        .setEventData(eventData)
                        .build();

        // Action
        MobileCore.dispatchEvent(event);

        Thread.sleep(1000);

        // Send completion event
        Map<String, Object> completionEventData =
                new HashMap<String, Object>() {
                    {
                        put("completedUpdateRequestForEventId", requestEventId);
                    }
                };
        Event completionEvent =
                new Event.Builder(
                                "Optimize Update Propositions Complete",
                                OptimizeTestConstants.EventType.OPTIMIZE,
                                OptimizeTestConstants.EventSource.CONTENT_COMPLETE)
                        .setEventData(completionEventData)
                        .build();
        MobileCore.dispatchEvent(completionEvent);

        Thread.sleep(1000);
        TestHelper.resetTestExpectations();
        DecisionScope decisionScope = new DecisionScope(decisionScopeString);
        final Map<DecisionScope, OptimizeProposition> propositionMap = new HashMap<>();
        final ADBCountDownLatch countDownLatch = new ADBCountDownLatch(1);
        Optimize.getPropositions(
                Collections.singletonList(decisionScope),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch.countDown();
                    }
                });

        countDownLatch.await(1, TimeUnit.SECONDS);
        // Assertions
        assertEquals(1, propositionMap.size());

        // Action: Trigger Identity Request reset event.
        MobileCore.resetIdentities();

        Thread.sleep(1000);

        // Assert
        TestHelper.resetTestExpectations();
        propositionMap.clear();
        final ADBCountDownLatch countDownLatch1 = new ADBCountDownLatch(1);
        Optimize.getPropositions(
                Collections.singletonList(decisionScope),
                new AdobeCallbackWithError<Map<DecisionScope, OptimizeProposition>>() {
                    @Override
                    public void fail(AdobeError adobeError) {
                        Assert.fail("Error in getting cached propositions");
                    }

                    @Override
                    public void call(
                            Map<DecisionScope, OptimizeProposition> decisionScopePropositionMap) {
                        propositionMap.putAll(decisionScopePropositionMap);
                        countDownLatch1.countDown();
                    }
                });

        countDownLatch1.await(1, TimeUnit.SECONDS);
        // Assertions
        Assert.assertTrue(propositionMap.isEmpty());
    }

    // 16
    @Test
    public void testOfferGenerateDisplayInteractionXdm() throws IOException {
        // Setup
        final String validPropositionText =
                "{\n"
                    + "  \"id\":\"de03ac85-802a-4331-a905-a57053164d35\",\n"
                    + "  \"items\":[\n"
                    + "    {\n"
                    + "      \"id\":\"xcore:personalized-offer:1111111111111111\",\n"
                    + "      \"etag\":\"10\",\n"
                    + "     "
                    + " \"schema\":\"https://ns.adobe.com/experience/offer-management/content-component-html\",\n"
                    + "      \"data\":{\n"
                    + "        \"id\":\"xcore:personalized-offer:1111111111111111\",\n"
                    + "        \"format\":\"text/html\",\n"
                    + "        \"content\":\"<h1>This is a HTML content</h1>\"\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"placement\":{\n"
                    + "    \"etag\":\"1\",\n"
                    + "    \"id\":\"xcore:offer-placement:1111111111111111\"\n"
                    + "  },\n"
                    + "  \"activity\":{\n"
                    + "    \"etag\":\"8\",\n"
                    + "    \"id\":\"xcore:offer-activity:1111111111111111\"\n"
                    + "  },\n"
                    + "  \"scope\":\"eydhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==\"\n"
                    + "}";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> propositionData =
                objectMapper.readValue(
                        validPropositionText, new TypeReference<Map<String, Object>>() {});
        OptimizeProposition optimizeProposition =
                OptimizeProposition.fromEventData(propositionData);
        assert optimizeProposition != null;
        Offer offer = optimizeProposition.getOffers().get(0);

        // Action
        final Map<String, Object> propositionInteractionXdm = offer.generateDisplayInteractionXdm();

        // Assert
        Assert.assertNotNull(propositionInteractionXdm);
        assertEquals(
                "decisioning.propositionDisplay", propositionInteractionXdm.get("eventType"));
        final Map<String, Object> experience =
                (Map<String, Object>) propositionInteractionXdm.get("_experience");
        Assert.assertNotNull(experience);
        final Map<String, Object> decisioning = (Map<String, Object>) experience.get("decisioning");
        Assert.assertNotNull(decisioning);
        final List<Map<String, Object>> propositionInteractionDetailsList =
                (List<Map<String, Object>>) decisioning.get("propositions");
        Assert.assertNotNull(propositionInteractionDetailsList);
        assertEquals(1, propositionInteractionDetailsList.size());
        final Map<String, Object> propositionInteractionDetailsMap =
                propositionInteractionDetailsList.get(0);
        assertEquals(
                "de03ac85-802a-4331-a905-a57053164d35", propositionInteractionDetailsMap.get("id"));
        assertEquals(
                "eydhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==",
                propositionInteractionDetailsMap.get("scope"));
        final Map<String, Object> scopeDetails =
                (Map<String, Object>) propositionInteractionDetailsMap.get("scopeDetails");
        Assert.assertNotNull(scopeDetails);
        Assert.assertTrue(scopeDetails.isEmpty());
        final List<Map<String, Object>> items =
                (List<Map<String, Object>>) propositionInteractionDetailsMap.get("items");
        Assert.assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("xcore:personalized-offer:1111111111111111", items.get(0).get("id"));
    }

    // 17
    @Test
    public void testOfferGenerateTapInteractionXdm() throws IOException {
        // Setup
        final String validProposition =
                "{\n"
                    + "  \"id\": \"AT:eyJhY3Rpdml0eUlkIjoiMTI1NTg5IiwiZXhwZXJpZW5jZUlkIjoiMCJ9\",\n"
                    + "  \"items\": [\n"
                    + "    {\n"
                    + "      \"id\": \"246315\",\n"
                    + "      \"schema\":"
                    + " \"https://ns.adobe.com/personalization/json-content-item\",\n"
                    + "      \"data\": {\n"
                    + "        \"id\": \"246315\",\n"
                    + "        \"format\": \"application/json\",\n"
                    + "        \"content\": {\n"
                    + "          \"testing\": \"ho-ho\"\n"
                    + "        }\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"scope\": \"myMbox\",\n"
                    + "  \"scopeDetails\": {\n"
                    + "    \"decisionProvider\": \"TGT\",\n"
                    + "    \"activity\": {\n"
                    + "      \"id\": \"125589\"\n"
                    + "    },\n"
                    + "    \"experience\": {\n"
                    + "      \"id\": \"0\"\n"
                    + "    },\n"
                    + "    \"strategies\": [\n"
                    + "      {\n"
                    + "        \"algorithmID\": \"0\",\n"
                    + "        \"trafficType\": \"0\"\n"
                    + "      }\n"
                    + "    ]\n"
                    + "  }\n"
                    + "}";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> propositionData =
                objectMapper.readValue(
                        validProposition, new TypeReference<Map<String, Object>>() {});
        OptimizeProposition optimizeProposition =
                OptimizeProposition.fromEventData(propositionData);
        assert optimizeProposition != null;
        Offer offer = optimizeProposition.getOffers().get(0);

        // Action
        Map<String, Object> propositionTapInteractionXdm = offer.generateTapInteractionXdm();

        // Assert
        // verify
        Assert.assertNotNull(propositionTapInteractionXdm);
        assertEquals(
                "decisioning.propositionInteract", propositionTapInteractionXdm.get("eventType"));
        final Map<String, Object> experience =
                (Map<String, Object>) propositionTapInteractionXdm.get("_experience");
        Assert.assertNotNull(experience);
        final Map<String, Object> decisioning = (Map<String, Object>) experience.get("decisioning");
        Assert.assertNotNull(decisioning);
        final List<Map<String, Object>> propositionInteractionDetailsList =
                (List<Map<String, Object>>) decisioning.get("propositions");
        Assert.assertNotNull(propositionInteractionDetailsList);
        assertEquals(1, propositionInteractionDetailsList.size());
        final Map<String, Object> propositionInteractionDetailsMap =
                propositionInteractionDetailsList.get(0);
        assertEquals(
                "AT:eyJhY3Rpdml0eUlkIjoiMTI1NTg5IiwiZXhwZXJpZW5jZUlkIjoiMCJ9",
                propositionInteractionDetailsMap.get("id"));
        assertEquals("myMbox", propositionInteractionDetailsMap.get("scope"));
        final Map<String, Object> scopeDetails =
                (Map<String, Object>) propositionInteractionDetailsMap.get("scopeDetails");
        Assert.assertNotNull(scopeDetails);
        Assert.assertTrue(scopeDetails.size() > 0);
        final List<Map<String, Object>> items =
                (List<Map<String, Object>>) propositionInteractionDetailsMap.get("items");
        Assert.assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("246315", items.get(0).get("id"));
    }

    // 18
    @Test
    public void testPropositionGenerateReferenceXdm() throws IOException {
        // Setup
        final String validProposition =
                "{\n"
                    + "  \"id\":\"de03ac85-802a-4331-a905-a57053164d35\",\n"
                    + "  \"items\":[\n"
                    + "    {\n"
                    + "      \"id\":\"xcore:personalized-offer:1111111111111111\",\n"
                    + "      \"etag\":\"10\",\n"
                    + "     "
                    + " \"schema\":\"https://ns.adobe.com/experience/offer-management/content-component-html\",\n"
                    + "      \"data\":{\n"
                    + "        \"id\":\"xcore:personalized-offer:1111111111111111\",\n"
                    + "        \"format\":\"text/html\",\n"
                    + "        \"content\":\"<h1>This is a HTML content</h1>\"\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"placement\":{\n"
                    + "    \"etag\":\"1\",\n"
                    + "    \"id\":\"xcore:offer-placement:1111111111111111\"\n"
                    + "  },\n"
                    + "  \"activity\":{\n"
                    + "    \"etag\":\"8\",\n"
                    + "    \"id\":\"xcore:offer-activity:1111111111111111\"\n"
                    + "  },\n"
                    + "  \"scope\":\"eydhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==\"\n"
                    + "}";

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> propositionData =
                objectMapper.readValue(
                        validProposition, new TypeReference<Map<String, Object>>() {});

        final OptimizeProposition optimizeProposition =
                OptimizeProposition.fromEventData(propositionData);

        // Action
        assert optimizeProposition != null;
        final Map<String, Object> propositionReferenceXdm =
                optimizeProposition.generateReferenceXdm();

        // verify
        Assert.assertNotNull(propositionReferenceXdm);
        final Map<String, Object> experience =
                (Map<String, Object>) propositionReferenceXdm.get("_experience");
        Assert.assertNotNull(experience);
        final Map<String, Object> decisioning = (Map<String, Object>) experience.get("decisioning");
        Assert.assertNotNull(decisioning);
        assertEquals(
                "de03ac85-802a-4331-a905-a57053164d35", decisioning.get("propositionID"));
    }

    private void updateConfiguration(final Map<String, Object> config) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        MonitorExtension.configurationAwareness(configurationState -> latch.countDown());
        MobileCore.updateConfiguration(config);
        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    private void clearUpdatedConfiguration() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        MonitorExtension.configurationAwareness(configurationState -> latch.countDown());
        MobileCore.clearUpdatedConfiguration();
        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
}
