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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * This class provides test utils for the Pushover binding.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@NonNullByDefault
public class PushoverTestUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushoverTestUtils.class);
    private static final Gson GSON = new Gson();

    public static <T> T getObjectFromJson(String filename, Class<T> clazz) throws IOException {
        return Objects.requireNonNull(GSON.fromJson(getJsonFromFile(filename), clazz));
    }

    public static String getJsonFromFile(String filename) throws IOException {
        try (InputStream inputStream = PushoverTestUtils.class.getResourceAsStream(filename)) {
            if (inputStream == null) {
                throw new IOException("InputStream is null");
            }
            byte[] bytes = inputStream.readAllBytes();
            if (bytes == null) {
                throw new IOException("Resulting byte-array is empty");
            }
            String rawData = new String(bytes, StandardCharsets.UTF_8);
            LOGGER.debug("Returning data: {}", rawData);
            return rawData;
        }
    }
}
