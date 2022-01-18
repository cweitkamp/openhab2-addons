/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.pushover.internal.connection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.pushover.internal.config.PushoverAccountConfiguration;
import org.openhab.binding.pushover.internal.dto.Sound;
import org.openhab.core.i18n.CommunicationException;
import org.openhab.core.i18n.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test cases for {@link PushoverAPIConnection}.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
@NonNullByDefault
public class PuschoverAPIConnectionTest {

    private static final String TEST_MESSAGE = "Test message!";
    private static final String VALID_API_KEY = "validAPIKey";
    private static final String INVALID_API_KEY = "invalidAPIKey";
    private static final String VALID_USER = "validUser";
    private static final String INVALID_USER = "invalidUser";
    private static final String VALID_RECEIPT = "ri5fyf2y5z6ivgav55phcbmdogcpic";
    private static final String INVALID_RECEIPT = "invalid";

    private final Logger logger = LoggerFactory.getLogger(PuschoverAPIConnectionTest.class);

    private @NonNullByDefault({}) @Mock HttpClient mockedHttpClient;
    private @NonNullByDefault({}) @Mock Request mockedRequest;
    private @NonNullByDefault({}) @Mock ContentResponse mockedContentResponse;

    @BeforeEach
    public void setup() throws InterruptedException, TimeoutException, ExecutionException {
        when(mockedHttpClient.newRequest(any(String.class))).thenReturn(mockedRequest);

        when(mockedRequest.method(any(HttpMethod.class))).thenReturn(mockedRequest);
        when(mockedRequest.timeout(any(Long.class), any(TimeUnit.class))).thenReturn(mockedRequest);
        when(mockedRequest.content(any(ContentProvider.class))).thenReturn(mockedRequest);

        when(mockedRequest.send()).thenReturn(mockedContentResponse);
    }

    @Test
    void testValidAPIKey() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.OK_200);
        when(mockedContentResponse.getContentAsString())
                .thenReturn(PushoverTestUtils.getJsonFromFile("valid-apikey-and-user.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(VALID_API_KEY, VALID_USER);

        assertThat(true, is(mockedConnection.validateUser()));
    }

    @Test
    void testInalidAPIKey() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.BAD_REQUEST_400);
        when(mockedContentResponse.getContentAsString())
                .thenReturn(PushoverTestUtils.getJsonFromFile("invalid-apikey.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(INVALID_API_KEY, VALID_USER);

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> mockedConnection.validateUser());
        assertThat(e.getMessage(), is("[\"application token is invalid\"]"));
    }

    @Test
    public void testSoundsData() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.OK_200);
        when(mockedContentResponse.getContentAsString()).thenReturn(PushoverTestUtils.getJsonFromFile("sounds.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(VALID_API_KEY, VALID_USER);

        List<Sound> sounds = mockedConnection.getSounds();
        assertNotNull(sounds);
        assertThat(sounds, hasSize(PushoverAccountConfiguration.DEFAULT_SOUNDS.size()));

        Sound defaultSound = sounds.stream().filter(s -> "pushover".equals(s.sound)).findFirst().orElse(null);
        assertNotNull(defaultSound);
        assertThat(defaultSound.sound, is("pushover"));
        assertThat(defaultSound.label, is("Pushover (default)"));
    }

    @Test
    public void testInvalidUser() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.BAD_REQUEST_400);
        when(mockedContentResponse.getContentAsString())
                .thenReturn(PushoverTestUtils.getJsonFromFile("invalid-user.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(VALID_API_KEY, INVALID_USER);

        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> mockedConnection.sendMessage(PushoverMessageBuilder.getInstance(VALID_API_KEY, INVALID_USER)));
        assertThat(e.getMessage(), is("[\"user key is invalid\"]"));
    }

    @Test
    public void testSendMessageButMessageMissing() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.BAD_REQUEST_400);
        when(mockedContentResponse.getContentAsString())
                .thenReturn(PushoverTestUtils.getJsonFromFile("message-missing.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(VALID_API_KEY, VALID_USER);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mockedConnection.sendMessage(PushoverMessageBuilder.getInstance(VALID_API_KEY, VALID_USER)));
        assertThat(e.getMessage(), is("[\"message cannot be blank\"]"));
    }

    @Test
    public void testSendMessage() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.OK_200);
        when(mockedContentResponse.getContentAsString())
                .thenReturn(PushoverTestUtils.getJsonFromFile("message-success.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(VALID_API_KEY, VALID_USER);

        assertThat(true, is(mockedConnection
                .sendMessage(PushoverMessageBuilder.getInstance(VALID_API_KEY, VALID_USER).withMessage(TEST_MESSAGE))));
    }

    @Test
    public void testSendEmergencyMessageReturnsReceipt() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.OK_200);
        when(mockedContentResponse.getContentAsString())
                .thenReturn(PushoverTestUtils.getJsonFromFile("message-success-with-receipt.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(VALID_API_KEY, VALID_USER);

        assertThat(VALID_RECEIPT,
                is(mockedConnection.sendPriorityMessage(PushoverMessageBuilder.getInstance(VALID_API_KEY, VALID_USER)
                        .withMessage(TEST_MESSAGE).withPriority(PushoverMessageBuilder.EMERGENCY_PRIORITY))));
    }

    @Test
    public void testCancelInvalidReceipt() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.BAD_REQUEST_400);
        when(mockedContentResponse.getContentAsString())
                .thenReturn(PushoverTestUtils.getJsonFromFile("cancel-receipt-invalid.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(VALID_API_KEY, VALID_USER);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mockedConnection.cancelPriorityMessage(INVALID_RECEIPT));
        assertThat(e.getMessage(), is("[\"receipt not found; may be invalid or expired\"]"));
    }

    @Test
    public void testCancelReceipt() throws IOException {
        when(mockedContentResponse.getStatus()).thenReturn(HttpStatus.OK_200);
        when(mockedContentResponse.getContentAsString())
                .thenReturn(PushoverTestUtils.getJsonFromFile("cancel-receipt-success.json"));

        PushoverAPIConnection mockedConnection = createMockedConnection(VALID_API_KEY, VALID_USER);

        assertThat(true, is(mockedConnection.cancelPriorityMessage(VALID_RECEIPT)));
    }

    private PushoverAPIConnection createMockedConnection(String apikey, String user) {
        PushoverAccountConfiguration config = new PushoverAccountConfiguration();
        config.apikey = apikey;
        config.user = user;

        return new PushoverAPIConnection(mockedHttpClient, config) {
            @Override
            synchronized String executeRequest(HttpMethod httpMethod, String url, @Nullable ContentProvider body)
                    throws CommunicationException, ConfigurationException {
                logger.debug("Processing URL: {}", url);
                return super.executeRequest(httpMethod, url, body);
            }
        };
    }
}
