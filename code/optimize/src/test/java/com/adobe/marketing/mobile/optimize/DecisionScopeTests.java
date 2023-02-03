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

import android.util.Base64;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.Silent.class)
@SuppressWarnings({"rawtypes"})
public class DecisionScopeTests {

    @Test
    public void testConstructor_validName() {
        // test
        final DecisionScope scope = new DecisionScope("myMbox");
        Assert.assertNotNull(scope);
        Assert.assertEquals("myMbox", scope.getName());
    }

    @Test
    public void testConstructor_emptyName() {
        // test
        final DecisionScope scope = new DecisionScope("");
        Assert.assertNotNull(scope);
        Assert.assertEquals("", scope.getName());
    }

    @Test
    public void testConstructor_nullName() {
        // test
        final DecisionScope scope = new DecisionScope("");
        Assert.assertNotNull(scope);
        Assert.assertEquals("", scope.getName());
    }

    @Test
    public void testConstructor_defaultItemCount() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.encodeToString(ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer) invocation -> java.util.Base64.getEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111");
            Assert.assertNotNull(scope);
            Assert.assertEquals("eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==", scope.getName());
        }
    }

    @Test
    public void testConstructor_itemCount() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.encodeToString(ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer) invocation -> java.util.Base64.getEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111", 10);
            Assert.assertNotNull(scope);
            Assert.assertEquals("eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEiLCJpdGVtQ291bnQiOjEwfQ==", scope.getName());
        }
    }

    @Test
    public void testConstructor_emptyActivityId() {
        // test
        final DecisionScope scope = new DecisionScope("", "xcore:offer-placement:1111111111111111");
        Assert.assertNotNull(scope);
        Assert.assertEquals("", scope.getName());
    }

    @Test
    public void testConstructor_nullActivityId() {
        // test
        final DecisionScope scope = new DecisionScope(null, "xcore:offer-placement:1111111111111111");
        Assert.assertNotNull(scope);
        Assert.assertEquals("", scope.getName());
    }

    @Test
    public void testConstructor_emptyPlacementId() {
        // test
        final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", "");
        Assert.assertNotNull(scope);
        Assert.assertEquals("", scope.getName());
    }

    @Test
    public void testConstructor_nullPlacementId() {
        // test
        final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", null);
        Assert.assertNotNull(scope);
        Assert.assertEquals("", scope.getName());
    }

    @Test
    public void testConstructor_zeroItemCount() {
        // test
        final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111", 0);
        Assert.assertNotNull(scope);
        Assert.assertEquals("", scope.getName());
    }

    @Test
    public void testIsValid_scopeWithValidName() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("myMbox");
            Assert.assertTrue(scope.isValid());
        }
    }

    @Test
    public void testIsValid_scopeWithNullName() {
        // test
        final DecisionScope scope = new DecisionScope(null);
        Assert.assertFalse(scope.isValid());
    }

    @Test
    public void testIsValid_scopeWithEmptyName() {
        // test
        final DecisionScope scope = new DecisionScope("");
        Assert.assertFalse(scope.isValid());
    }

    @Test
    public void testIsValid_scopeWithDefaultItemCount() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.encodeToString(ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer) invocation -> java.util.Base64.getEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111");
            Assert.assertTrue(scope.isValid());
        }
    }

    @Test
    public void testIsValid_scopeWithItemCount() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.encodeToString(ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer) invocation -> java.util.Base64.getEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111", 10);
            Assert.assertTrue(scope.isValid());
        }
    }

    @Test
    public void testIsValid_scopeWithEmptyActivityId() {
        // test
        final DecisionScope scope = new DecisionScope("", "xcore:offer-placement:1111111111111111");
        Assert.assertFalse(scope.isValid());
    }

    @Test
    public void testIsValid_scopeWithNullActivityId() {
        // test
        final DecisionScope scope = new DecisionScope(null, "xcore:offer-placement:1111111111111111");
        Assert.assertFalse(scope.isValid());
    }

    @Test
    public void testIsValid_scopeWithEmptyPlacementId() {
        // test
        final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", "");
        Assert.assertFalse(scope.isValid());
    }

    @Test
    public void testIsValid_scopeWithNullPlacementId() {
        // test
        final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", null);
        Assert.assertFalse(scope.isValid());
    }

    @Test
    public void testIsValid_scopeWithZeroItemCount() {
        // test
        final DecisionScope scope = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111", 0);
        Assert.assertFalse(scope.isValid());
    }

    @Test
    public void testIsValid_encodedScopeWithDefaultItemCount() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("eyJ4ZG06YWN0aXZpdHlJZCI6Inhjb3JlOm9mZmVyLWFjdGl2aXR5OjExMTExMTExMTExMTExMTEiLCJ4ZG06cGxhY2VtZW50SWQiOiJ4Y29yZTpvZmZlci1wbGFjZW1lbnQ6MTExMTExMTExMTExMTExMSJ9");
            Assert.assertTrue(scope.isValid());
        }
    }

    @Test
    public void testIsValid_encodedScopeWithItemCount() {
        // test
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            final DecisionScope scope = new DecisionScope("eyJ4ZG06YWN0aXZpdHlJZCI6Inhjb3JlOm9mZmVyLWFjdGl2aXR5OjExMTExMTExMTExMTExMTEiLCJ4ZG06cGxhY2VtZW50SWQiOiJ4Y29yZTpvZmZlci1wbGFjZW1lbnQ6MTExMTExMTExMTExMTExMSIsInhkbTppdGVtQ291bnQiOjEwMH0=");
            Assert.assertTrue(scope.isValid());
        }
    }

    @Test
    public void testIsValid_encodedScopeWithValidName() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("eyJ4ZG06bmFtZSI6ImNvbS5hZG9iZS5TYW1wbGVBcHAifQ==");
            Assert.assertTrue(scope.isValid());
        }
    }

    @Test
    public void testIsValid_encodedScopeWithEmptyName() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("eyJ4ZG06bmFtZSI6IiJ9");
            Assert.assertFalse(scope.isValid());
        }

    }

    @Test
    public void testIsValid_encodedScopeWithEmptyActivityId() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("eyJ4ZG06YWN0aXZpdHlJZCI6IiIsInhkbTpwbGFjZW1lbnRJZCI6Inhjb3JlOm9mZmVyLXBsYWNlbWVudDoxMTExMTExMTExMTExMTExIn0=");
            Assert.assertFalse(scope.isValid());
        }
    }

    @Test
    public void testIsValid_encodedScopeWithEmptyPlacementId() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("eyJ4ZG06YWN0aXZpdHlJZCI6Inhjb3JlOm9mZmVyLWFjdGl2aXR5OjExMTExMTExMTExMTExMTEiLCJ4ZG06cGxhY2VtZW50SWQiOiIifQ==");
            Assert.assertFalse(scope.isValid());
        }
    }

    @Test
    public void testIsValid_encodedScopeWithInvalidItemCount() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope( "eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEiLCJpdGVtQ291bnQiOi0xfQ==");
            Assert.assertFalse(scope.isValid());
        }
    }

    @Test
    public void testIsValid_encodedScopeWithZeroItemCount() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope = new DecisionScope("eyJ4ZG06YWN0aXZpdHlJZCI6Inhjb3JlOm9mZmVyLWFjdGl2aXR5OjExMTExMTExMTExMTExMTEiLCJ4ZG06cGxhY2VtZW50SWQiOiJ4Y29yZTpvZmZlci1wbGFjZW1lbnQ6MTExMTExMTExMTExMTExMSIsInhkbTppdGVtQ291bnQiOjB9");
            Assert.assertFalse(scope.isValid());
        }
    }

    @Ignore
    public void testIsValid_invalidEncodedScope() {
        // test
        final DecisionScope scope = new DecisionScope( "eyJ4ZG06YWN0aXZpdHlJZCI6Inhjb3JlOm9mZmVyLWFjdGl2aXR5OjExMTExMTExMTExMTExMTEiLCJ4ZG06cGxhY2VtZW50SWQiOiJ4Y29yZTpvZmZlci1wbGFjZW1lbnQ6MTExMTExMTExMTExMTExMSwieGRtOml0ZW1Db3VudCI6MzB9");
        Assert.assertFalse(scope.isValid());
    }

    @Test
    public void testEquals() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.encodeToString(ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer) invocation -> java.util.Base64.getEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope1 = new DecisionScope("eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEifQ==");
            final DecisionScope scope2 = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111");
            Assert.assertEquals(scope1, scope2);
        }
    }

    @Test
    public void testEquals_decisionScopeObjectContainsItemCount() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.encodeToString(ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer) invocation -> java.util.Base64.getEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
            base64MockedStatic.when(() -> Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer<byte[]>) invocation -> java.util.Base64.getDecoder().decode((String) invocation.getArguments()[0]));
            // test
            final DecisionScope scope1 = new DecisionScope("eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTExMTExMTExMTExMTExMSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjExMTExMTExMTExMTExMTEiLCJpdGVtQ291bnQiOjEwMH0=");
            final DecisionScope scope2 = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111", 100);
            Assert.assertEquals(scope1, scope2);
        }
    }

    @Test
    public void testEquals_decisionScopeObjectsNotEqual() {
        try (MockedStatic<Base64> base64MockedStatic = Mockito.mockStatic(Base64.class)) {
            base64MockedStatic.when(() -> Base64.encodeToString(ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenAnswer((Answer) invocation -> java.util.Base64.getEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
            // test
            final DecisionScope scope1 = new DecisionScope("mymbox");
            final DecisionScope scope2 = new DecisionScope("xcore:offer-activity:1111111111111111", "xcore:offer-placement:1111111111111111");
            Assert.assertNotEquals(scope1, scope2);
        }
    }

    @Test
    public void testEquals_otherDecisionScopeObjectIsNull() {
        // test
        final DecisionScope scope1 = new DecisionScope("mymbox");
        final DecisionScope scope2 = null;
        Assert.assertNotEquals(scope1, scope2);
    }

    @Test
    public void testFromEventData_validScopeName() {
        // setup
        final Map<String, Object> testEventData = new HashMap<>();
        testEventData.put("name", "myMbox");

        // test
        final DecisionScope scope = DecisionScope.fromEventData(testEventData);

        // verify
        Assert.assertNotNull(scope);
        Assert.assertEquals("myMbox", scope.getName());
    }

    @Test
    public void testFromEventData_nullEventData() {
        // test
        final DecisionScope scope = DecisionScope.fromEventData(null);

        // verify
        Assert.assertNull(scope);
    }

    @Test
    public void testFromEventData_emptyEventData() {
        // test
        final DecisionScope scope = DecisionScope.fromEventData(new HashMap<>());

        // verify
        Assert.assertNull(scope);
    }

    @Test
    public void testFromEventData_emptyScopeName() {
        // setup
        final Map<String, Object> testEventData = new HashMap<>();
        testEventData.put("name", "");

        // test
        final DecisionScope scope = DecisionScope.fromEventData(testEventData);

        // verify
        Assert.assertNull(scope);
    }

    @Test
    public void testFromEventData_nullScopeName() {
        // setup
        final Map<String, Object> testEventData = new HashMap<>();
        testEventData.put("name", null);

        // test
        final DecisionScope scope = DecisionScope.fromEventData(testEventData);

        // verify
        Assert.assertNull(scope);
    }

    @Test
    public void testFromEventData_missingScopeName() {
        // setup
        final Map<String, Object> testEventData = new HashMap<>();
        testEventData.put("somekey", "someValue");

        // test
        final DecisionScope scope = DecisionScope.fromEventData(testEventData);

        // verify
        Assert.assertNull(scope);
    }

    @Test
    public void testToEventData_validScope() {
        // setup
        final DecisionScope scope = new DecisionScope("myMbox");

        // test
        final Map<String, Object> eventData = scope.toEventData();

        // verify
        Assert.assertNotNull(eventData);
        Assert.assertEquals(1, eventData.size());
        Assert.assertEquals("myMbox", eventData.get("name"));
    }
}
