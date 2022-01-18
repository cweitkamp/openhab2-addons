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
import static org.junit.jupiter.api.Assertions.*;
import static org.openhab.binding.pushover.internal.PushoverBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.util.MultiPartContentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.core.i18n.ConfigurationException;

/**
 * Test cases for {@link PushoverMessageBuilder}.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
@NonNullByDefault
class PushoverMessageBuilderTest {

    private static final String VALID_API_KEY = "validAPIKey";
    private static final String VALID_USER = "validUser";

    @Test
    void testGetInstanceThrowsExceptionIfApiKeyIsNull() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> PushoverMessageBuilder.getInstance(null, null));
        assertThat(e.getRawMessage(), is(TEXT_OFFLINE_CONF_ERROR_MISSING_APIKEY));
    }

    @Test
    void testGetInstanceThrowsExceptionIfApiKeyIsBlank() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> PushoverMessageBuilder.getInstance(" ", null));
        assertThat(e.getRawMessage(), is(TEXT_OFFLINE_CONF_ERROR_MISSING_APIKEY));
    }

    @Test
    void testGetInstanceThrowsExceptionIfUserIsNull() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> PushoverMessageBuilder.getInstance(VALID_API_KEY, null));
        assertThat(e.getRawMessage(), is(TEXT_OFFLINE_CONF_ERROR_MISSING_USER));
    }

    @Test
    void testGetInstanceThrowsExceptionIfUserIsBlank() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> PushoverMessageBuilder.getInstance(VALID_API_KEY, " "));
        assertThat(e.getRawMessage(), is(TEXT_OFFLINE_CONF_ERROR_MISSING_USER));
    }

    @Test
    void testGetInstanceAReturnsPushoverMessageBuilderInstance() {
        PushoverMessageBuilder builder = getBuilder();
        assertNotNull(builder);
    }

    @Test
    void testBuildReturnsAContentProviderBuilderInstance() {
        PushoverMessageBuilder builder = getBuilder();
        ContentProvider contentProvider = builder.build();
        assertNotNull(contentProvider);
        assertInstanceOf(MultiPartContentProvider.class, contentProvider);
    }

    @Test
    void testBuildThrowsExceptionIfMessageIsTooLong() {
        PushoverMessageBuilder builder = getBuilder().withMessage(new String(new byte[1111]));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> builder.build());
        assertThat(e.getMessage(), is("Skip sending the message as 'message' is longer than 1024 characters."));
    }

    @Test
    void testBuildThrowsExceptionIfTitleIsTooLong() {
        PushoverMessageBuilder builder = getBuilder().withTitle(new String(new byte[300]));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> builder.build());
        assertThat(e.getMessage(), is("Skip sending the message as 'title' is longer than 250 characters."));
    }

    @Test
    void testBuildThrowsExceptionIfUrlIsTooLong() {
        PushoverMessageBuilder builder = getBuilder().withUrl(new String(new byte[600]));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> builder.build());
        assertThat(e.getMessage(), is("Skip sending the message as 'url' is longer than 512 characters."));
    }

    @Test
    void testBuildThrowsExceptionIfUrlTitleIsTooLong() {
        PushoverMessageBuilder builder = getBuilder().withUrl("").withUrlTitle(new String(new byte[600]));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> builder.build());
        assertThat(e.getMessage(), is("Skip sending the message as 'urlTitle' is longer than 100 characters."));
    }

    private PushoverMessageBuilder getBuilder() {
        return PushoverMessageBuilder.getInstance(VALID_API_KEY, VALID_USER);
    }
}
