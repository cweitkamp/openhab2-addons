/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.openweathermap.internal.config;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.openweathermap.internal.handler.OpenWeatherMapAPIHandler;

/**
 * The {@link OpenWeatherMapAPIConfiguration} is the class used to match the {@link OpenWeatherMapAPIHandler}s
 * configuration.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@NonNullByDefault
public class OpenWeatherMapAPIConfiguration {
    // supported languages (see https://openweathermap.org/api/one-call-api#multi)
    public static final Set<String> SUPPORTED_LANGUAGES = Collections.unmodifiableSet(Stream.of("af", "al", "ar", "az",
            "bg", "ca", "cz", "da", "de", "el", "en", "es", "eu", "fa", "fi", "fr", "gl", "he", "hi", "hr", "hu", "id",
            "it", "ja", "kr", "la", "lt", "mk", "nl", "no", "pl", "pt", "pt_br", "ro", "ru", "se", "sk", "sl", "sp",
            "sr", "sv", "th", "tr", "ua", "uk", "vi", "zh_cn", "zh_tw", "zu").collect(Collectors.toSet()));

    public @Nullable String apikey;
    public int refreshInterval;
    public @Nullable String language;
}
