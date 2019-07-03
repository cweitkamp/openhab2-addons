/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
package org.openhab.binding.kodi.internal.actions;

import java.math.BigDecimal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.binding.ThingActions;
import org.eclipse.smarthome.core.thing.binding.ThingActionsScope;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.kodi.internal.handler.KodiHandler;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.RuleAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the automation engine action handler service for Kodi actions.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@ThingActionsScope(name = "kodi")
@NonNullByDefault
public class KodiActions implements ThingActions {

    private final Logger logger = LoggerFactory.getLogger(KodiActions.class);

    private @Nullable KodiHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (KodiHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return this.handler;
    }

    @RuleAction(label = "@text/showNotificationActionLabel", description = "@text/showNotificationActionDescription")
    public void showNotification(
            @ActionInput(name = "message", label = "@text/showNotificationInputMessageLabel", description = "@text/showNotificationInputMessageDescription", required = true) @Nullable String message) {
        showNotificationWithTitle("openHAB", message);
    }

    @RuleAction(label = "@text/showNotificationWithTitleActionLabel", description = "@text/showNotificationWithTitleActionDescription")
    public void showNotificationWithTitle(
            @ActionInput(name = "title", label = "@text/showNotificationInputTitleLabel", description = "@text/showNotificationInputTitleDescription", required = true) @Nullable String title,
            @ActionInput(name = "message", label = "@text/showNotificationInputMessageLabel", description = "@text/showNotificationInputMessageDescription", required = true) @Nullable String message) {
        KodiHandler kodiHandler = handler;
        if (kodiHandler == null) {
            logger.warn("Kodi Actions: ThingHandler is null!");
            return;
        }

        if (title == null) {
            logger.warn("Kodi Actions: Skipping 'showNotification' due to null value for 'title'.");
            return;
        }

        if (message == null) {
            logger.warn("Kodi Actions: Skipping 'showNotification' due to null value for 'message'.");
            return;
        }

        kodiHandler.showNotification(title, BigDecimal.valueOf(5000), null, message);
    }

    public static void showNotification(@Nullable ThingActions actions, @Nullable String message) {
        if (actions instanceof KodiActions) {
            ((KodiActions) actions).showNotification(message);
        } else {
            throw new IllegalArgumentException("Instance is not a KodiActions class.");
        }
    }

    public static void showNotificationWithTitle(@Nullable ThingActions actions, @Nullable String title,
            @Nullable String message) {
        if (actions instanceof KodiActions) {
            ((KodiActions) actions).showNotificationWithTitle(title, message);
        } else {
            throw new IllegalArgumentException("Instance is not a KodiActions class.");
        }
    }
}
