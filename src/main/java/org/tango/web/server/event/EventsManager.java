/*
 * Copyright 2019 Tango Controls
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tango.web.server.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tango.client.ez.proxy.*;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 11/8/18
 */
public class EventsManager {
    public static final long FALLBACK_POLLING_DELAY = 1L;
    public static final long EVENTS_CLEANUP_DELAY = 30L;

    private final Logger logger = LoggerFactory.getLogger(EventsManager.class);

    private final ConcurrentMap<EventImpl.Target, EventImpl> events = new ConcurrentHashMap<>();
    private final AtomicInteger eventId = new AtomicInteger(0);

    private final TangoSseBroadcasterFactory tangoSseBroadcasterFactory;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "event-system-fallback-polling-thread");
        }
    });

    public EventsManager(TangoSseBroadcasterFactory tangoSseBroadcasterFactory) {
        this.tangoSseBroadcasterFactory = tangoSseBroadcasterFactory;

        scheduler.scheduleAtFixedRate(() -> {
            events.entrySet().stream()
                    .filter(targetEventEntry -> ((EventImpl)targetEventEntry.getValue()).broadcaster.size() == 0)
                    .forEach(targetEventEntry -> {
                        EventImpl event = (EventImpl) targetEventEntry.getValue();

                        events.remove(event.target);
                        event.broadcaster.close();

                        try {
                            TangoProxy proxy = event.proxy;
                            proxy.removeEventListener(event.target.attribute, TangoEvent.valueOf(event.target.type.toUpperCase()),event.tangoEventListener);
                            proxy.unsubscribeFromEvent(event.target.attribute, TangoEvent.valueOf(event.target.type.toUpperCase()));
                        } catch (TangoProxyException e) {
                            logger.error("Failed to unsubscribe from event {} due to {}", event.target, e.getMessage());
                            logger.error("Failed to unsubscribe from event:", e);
                        }
                    });
        },EVENTS_CLEANUP_DELAY, EVENTS_CLEANUP_DELAY, TimeUnit.MINUTES);
    }

    public Optional<EventImpl> lookupEvent(EventImpl.Target target){
        return Optional.ofNullable(events.get(target));
    }

    public EventImpl newEvent(EventImpl.Target target) throws TangoProxyException, NoSuchAttributeException {
        TangoSseBroadcaster broadcaster = tangoSseBroadcasterFactory.newInstance();

        final TangoProxy proxy = TangoProxies.newDeviceProxyWrapper(target.toTangoDeviceURLString());

        int id = eventId.incrementAndGet();
        logger.debug("Create new Event for {}. Id = {}", target, id);


        EventImpl event = new EventImpl(id, target, proxy, new SseTangoEventListener(broadcaster,tangoSseBroadcasterFactory.getSse(),id), broadcaster);
        EventImpl event0 = events.putIfAbsent(target, event);
        //race condition -> use previous
        if(event0 != null){
            logger.debug("Re-use Event for {}. Id = {}", target, event0.id);
            eventId.decrementAndGet();
            return event0;
        }


        try {
            logger.debug("Subscribe to {}", target);
            event.initialize();
        } catch (TangoProxyException e) {
            logger.debug("Failed to subscribe to {}. Fall back to polling...", target);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    ValueTime<Object> valueTime = proxy.readAttributeValueAndTime(target.attribute);
                    event.tangoEventListener.onEvent(new EventData<>(valueTime.getValue(), valueTime.getTime(), null));
                } catch (ReadAttributeException|NoSuchAttributeException e1) {
                    event.tangoEventListener.onError(e1);
                }
            }, EventsManager.FALLBACK_POLLING_DELAY, EventsManager.FALLBACK_POLLING_DELAY, TimeUnit.SECONDS);
        }

        return event;
    }

}
