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
package org.openhab.binding.openweathermap.internal.actions;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.binding.ThingActions;
import org.eclipse.smarthome.core.thing.binding.ThingActionsScope;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.openweathermap.internal.handler.OpenWeatherMapAPIHandler;
import org.openhab.core.automation.annotation.ActionOutput;
import org.openhab.core.automation.annotation.RuleAction;

/**
 * Some automation actions to be used with a {@link OpenWeatherMapAPIHandler}.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@ThingActionsScope(name = "openweathermap")
@NonNullByDefault
public class OpenWeatherMapThingActions implements ThingActions {

    private @Nullable OpenWeatherMapAPIHandler bridgeHandler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof OpenWeatherMapAPIHandler) {
            bridgeHandler = (OpenWeatherMapAPIHandler) handler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }

    @SuppressWarnings("null")
    @RuleAction(label = "@text/sendMeasurementsActionLabel", description = "@text/sendMeasurementsActionDescription")
    public @ActionOutput(name = "sent", label = "@text/sendMeasurementsActionOutputSentLabel", description = "@text/sendMeasurementsActionOutputSentDescription", type = "java.lang.Boolean") Boolean sendMeasurements() {
        if (bridgeHandler == null) {
            throw new RuntimeException("Cannot send measurements as 'bridgeHandler' is null.");
        }

        bridgeHandler.sendMeasurements();
        return Boolean.TRUE;
    }

    public static Boolean sendMeasurements(@Nullable ThingActions actions) {
        if (actions instanceof OpenWeatherMapThingActions) {
            return ((OpenWeatherMapThingActions) actions).sendMeasurements();
        } else {
            throw new IllegalArgumentException("Instance is not an OpenWeatherMapThingActions class.");
        }
    }

    @SuppressWarnings("null")
    @RuleAction(label = "@text/clearMeasurementsActionLabel", description = "@text/clearMeasurementsActionDescription")
    public @ActionOutput(name = "sent", label = "@text/clearMeasurementsActionOutputSentLabel", description = "@text/clearMeasurementsActionOutputSentDescription", type = "java.lang.Boolean") Boolean clearMeasurements() {
        if (bridgeHandler == null) {
            throw new RuntimeException("Cannot clear measurement cache as 'bridgeHandler' is null.");
        }

        bridgeHandler.clearMeasurements();
        return Boolean.TRUE;
    }

    public static Boolean clearMeasurements(@Nullable ThingActions actions) {
        if (actions instanceof OpenWeatherMapThingActions) {
            return ((OpenWeatherMapThingActions) actions).clearMeasurements();
        } else {
            throw new IllegalArgumentException("Instance is not an OpenWeatherMapThingActions class.");
        }
    }
}
