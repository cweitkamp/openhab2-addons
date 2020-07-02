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
package org.openhab.binding.openweathermap.internal.dto.stations;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Generated Plain Old Java Objects class for {@link OpenWeatherMapJsonMeasurementsData} from JSON.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@NonNullByDefault
public class OpenWeatherMapJsonMeasurementsData {

    // supported values (https://openweathermap.org/stations#measurement)
    public static final String HUMIDITY = "humidity";
    public static final String PRESSURE = "pressure";
    public static final String RAIN = "rain";
    public static final String TEMPERATURE = "temperature";
    public static final String TIMESTAMP = "timestamp";
    public static final String VISIBILITY = "visibility";
    public static final String WIND_DEG = "wind_deg";
    public static final String WIND_GUST = "wind_gust";
    public static final String WIND_SPEED = "wind_speed";

    public static final Set<String> SUPPORTED_VALUES = Collections.unmodifiableSet(
            Stream.of(TIMESTAMP, TEMPERATURE, WIND_SPEED, WIND_GUST, WIND_DEG, PRESSURE, HUMIDITY, RAIN, VISIBILITY)
                    .collect(Collectors.toSet()));

    @SerializedName(value = "station_id")
    private final String stationId;
    @SerializedName(value = "dt")
    public long timestamp = ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES).toEpochSecond();
    public @Nullable Double temperature;
    @SerializedName(value = "wind_speed")
    public @Nullable Double windSpeed;
    @SerializedName(value = "wind_gust")
    public @Nullable Double gustSpeed;
    @SerializedName(value = "wind_deg")
    public @Nullable Integer windDegree;
    public @Nullable Double pressure;
    public @Nullable Integer humidity;
    @SerializedName(value = "rain_1h")
    public @Nullable Double rain;
    @SerializedName(value = "visibility_distance")
    public @Nullable Integer visibility;

    public OpenWeatherMapJsonMeasurementsData(String stationId) {
        this.stationId = stationId;
    }
}
