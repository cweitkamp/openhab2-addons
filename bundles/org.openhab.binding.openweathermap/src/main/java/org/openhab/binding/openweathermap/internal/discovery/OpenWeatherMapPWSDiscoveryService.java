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
package org.openhab.binding.openweathermap.internal.discovery;

import static org.openhab.binding.openweathermap.internal.OpenWeatherMapBindingConstants.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.PointType;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerService;
import org.openhab.binding.openweathermap.internal.dto.stations.OpenWeatherMapJsonStationData;
import org.openhab.binding.openweathermap.internal.handler.OpenWeatherMapAPIHandler;
import org.openhab.binding.openweathermap.internal.handler.OpenWeatherMapPWSHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWeatherMapPWSDiscoveryService} creates things based on existing personal weather stations.
 *
 * @author Christoph Weitkamp - Initial Contribution
 */
@NonNullByDefault
public class OpenWeatherMapPWSDiscoveryService extends AbstractDiscoveryService
        implements DiscoveryService, ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(OpenWeatherMapPWSDiscoveryService.class);

    private static final int DISCOVERY_TIMEOUT_SECONDS = 2;
    private static final int DISCOVERY_INTERVAL_SECONDS = 60;
    private @Nullable ScheduledFuture<?> discoveryJob;

    private @NonNullByDefault({}) OpenWeatherMapAPIHandler bridgeHandler;

    /**
     * Creates an OpenWeatherMapPWSDiscoveryService.
     */
    public OpenWeatherMapPWSDiscoveryService() {
        super(OpenWeatherMapPWSHandler.SUPPORTED_THING_TYPES, DISCOVERY_TIMEOUT_SECONDS);
    }

    @Override
    protected void activate(@Nullable final Map<String, @Nullable Object> configProperties) {
        super.activate(configProperties);
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    protected void startScan() {
        logger.debug("Start manual OpenWeatherMap PWS discovery scan.");
        scanForNewPWS();
    }

    @Override
    protected synchronized void stopScan() {
        logger.debug("Stop manual OpenWeatherMap PWS discovery scan.");
        super.stopScan();
    }

    @Override
    protected void startBackgroundDiscovery() {
        ScheduledFuture<?> localDiscoveryJob = discoveryJob;
        if (localDiscoveryJob == null || localDiscoveryJob.isCancelled()) {
            logger.debug("Start OpenWeatherMap PWS background discovery job at interval {} s.",
                    DISCOVERY_INTERVAL_SECONDS);
            discoveryJob = scheduler.scheduleWithFixedDelay(this::scanForNewPWS, 0, DISCOVERY_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        ScheduledFuture<?> localDiscoveryJob = discoveryJob;
        if (localDiscoveryJob != null && !localDiscoveryJob.isCancelled()) {
            logger.debug("Stop OpenWeatherMap PWS background discovery job.");
            if (localDiscoveryJob.cancel(true)) {
                discoveryJob = null;
            }
        }
    }

    @Override
    public void setThingHandler(@NonNullByDefault({}) final ThingHandler handler) {
        if (handler instanceof OpenWeatherMapAPIHandler) {
            bridgeHandler = (OpenWeatherMapAPIHandler) handler;
            bridgeHandler.setPWSDiscoveryService(this);
        }
    }

    @Override
    public ThingHandler getThingHandler() {
        return bridgeHandler;
    }

    private void scanForNewPWS() {
        List<OpenWeatherMapJsonStationData> stations = bridgeHandler.getStations();
        if (stations != null) {
            stations.forEach(this::onStationAdded);
        }
    }

    public void onStationAdded(final OpenWeatherMapJsonStationData station) {
        logger.debug("PWS '{}' found -> Creating new discovery result.", station.id);
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        PointType location = new PointType(new DecimalType(station.latitude), new DecimalType(station.longitude),
                new DecimalType(station.altitude));
        Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_STATION_ID, station.id);
        properties.put(CONFIG_EXTERNAL_ID, station.externalId);
        properties.put(CONFIG_LOCATION, location.toString());
        thingDiscovered(DiscoveryResultBuilder.create(new ThingUID(THING_TYPE_PWS, bridgeUID, station.id))
                .withLabel(station.name).withProperties(properties).withRepresentationProperty(CONFIG_STATION_ID)
                .withBridge(bridgeUID).build());
    }

    public void onStationDeleted(final String stationId) {
        logger.debug("PWS '{}' deleted -> Removing discovery result.", stationId);
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        thingRemoved(new ThingUID(THING_TYPE_PWS, bridgeUID, stationId));
    }
}
