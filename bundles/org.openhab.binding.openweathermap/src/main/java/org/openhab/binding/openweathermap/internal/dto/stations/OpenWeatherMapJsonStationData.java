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

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Generated Plain Old Java Objects class for {@link OpenWeatherMapJsonStationData} from JSON.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
public class OpenWeatherMapJsonStationData {
    @SerializedName(value = "id", alternate = { "ID" })
    public String id;
    @SerializedName("updated_at")
    public String updatedAt;
    @SerializedName("created_at")
    public String createdAt;
    @SerializedName("user_id")
    public @Nullable String userId;
    @SerializedName("external_id")
    public String externalId;
    public String name;
    public double latitude;
    public double longitude;
    public double altitude;
    @SerializedName("source_type")
    public @Nullable Integer sourceType;
    public @Nullable Integer rank;
}
