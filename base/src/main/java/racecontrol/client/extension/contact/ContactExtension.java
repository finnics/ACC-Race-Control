/*
 * Copyright (c) 2021 Leonard Sch�ngel
 * 
 * For licensing information see the included license (LICENSE.txt)
 */
package racecontrol.client.extension.contact;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import racecontrol.client.data.SessionId;
import racecontrol.client.events.AfterPacketReceivedEvent;
import racecontrol.client.events.BroadcastingEventEvent;
import racecontrol.eventbus.Event;
import racecontrol.eventbus.EventBus;
import racecontrol.client.data.BroadcastingEvent;
import racecontrol.client.data.enums.BroadcastingEventType;
import racecontrol.utility.TimeUtils;
import racecontrol.client.extension.replayoffset.ReplayOffsetExtension;
import racecontrol.client.AccBroadcastingClient;
import java.util.logging.Logger;
import racecontrol.eventbus.EventListener;
import racecontrol.logging.UILogger;
import racecontrol.client.ClientExtension;
import racecontrol.client.data.CarInfo;
import racecontrol.client.data.SessionInfo;
import racecontrol.client.events.ConnectionOpenedEvent;
import racecontrol.client.events.RealtimeUpdateEvent;
import racecontrol.client.extension.dangerdetection.YellowFlagEvent;

/**
 * This extension listens for contact between cars during a session and creates
 * a ContactEvent for them.
 *
 * @author Leonard
 */
public class ContactExtension
        implements EventListener, ClientExtension {

    /**
     * Singelton instance.
     */
    private static ContactExtension instance = null;
    /**
     * This classes logger.
     */
    private static final Logger LOG = Logger.getLogger(ContactExtension.class.getName());
    /**
     * Reference to the connection client.
     */
    private final AccBroadcastingClient client;
    /**
     * Last accident that is waiting to be commited.
     */
    private ContactInfo stagedAccident = null;
    /**
     * Reference to the replay offset extension
     */
    private final ReplayOffsetExtension replayOffsetExtension;
    /**
     * Size of the meetings array
     */
    private int meetingsSize;
    /**
     * List of meetings going back in time.
     */
    private final List<Map<Integer, Map<Integer, Float>>> meetings = new LinkedList<>();

    public static ContactExtension getInstance() {
        if (instance == null) {
            instance = new ContactExtension();
        }
        return instance;
    }

    private ContactExtension() {
        EventBus.register(this);
        client = AccBroadcastingClient.getClient();
        replayOffsetExtension = ReplayOffsetExtension.getInstance();
    }

    @Override
    public void onEvent(Event e) {
        if (e instanceof AfterPacketReceivedEvent) {
            afterPacketReceived(((AfterPacketReceivedEvent) e).getType());
        } else if (e instanceof BroadcastingEventEvent) {
            BroadcastingEvent event = ((BroadcastingEventEvent) e).getEvent();
            if (event.getType() == BroadcastingEventType.ACCIDENT) {
                onAccident(event);
            }
        } else if (e instanceof YellowFlagEvent) {
            onYellow(((YellowFlagEvent) e).getCar());
        } else if (e instanceof RealtimeUpdateEvent) {
            onSessionUpdate(((RealtimeUpdateEvent) e).getSessionInfo());
        } else if (e instanceof ConnectionOpenedEvent) {
            meetingsSize = 3000 / client.getUpdateInterval();
        }
    }

    public void afterPacketReceived(byte type) {
        if (stagedAccident != null) {
            long now = System.currentTimeMillis();
            if (now - stagedAccident.getSystemTimestamp() > 1000) {
                commitAccident(stagedAccident);
                stagedAccident = null;
            }
        }
    }

    public void onAccident(BroadcastingEvent event) {
        // an accident event is usually 5000 ms after the contact.
        // to get an accurate timing we subtract that offset.
        int sessionTime = client.getModel().getSessionInfo().getSessionTime() - 5000;
        int replaytime = replayOffsetExtension.getReplayTimeFromSessionTime(sessionTime);
        String logMessage = "Contact: " + client.getModel().getCar(event.getCarId()).getCarNumberString()
                + "\t\t" + TimeUtils.asDuration(sessionTime)
                + "\t" + TimeUtils.asDuration(replaytime);
        UILogger.log(logMessage);
        LOG.info(logMessage);

        SessionId sessionId = client.getSessionId();
        if (stagedAccident == null) {
            stagedAccident = new ContactInfo(sessionTime,
                    replaytime,
                    client.getModel().getCar(event.getCarId()),
                    sessionId);
        } else {
            float timeDif = stagedAccident.getSessionLatestTime() - sessionTime;
            if (timeDif > 1000) {
                commitAccident(stagedAccident);
                stagedAccident = new ContactInfo(sessionTime,
                        replaytime,
                        client.getModel().getCar(event.getCarId()),
                        sessionId);
            } else {
                stagedAccident = stagedAccident.addCar(sessionTime,
                        client.getModel().getCar(event.getCarId()),
                        System.currentTimeMillis());
            }
        }
    }

    private void onYellow(CarInfo car) {
        int trackLength = client.getModel().getTrackInfo().getTrackMeters();
        float pos = car.getRealtime().getSplinePosition();

        Map<Integer, Float> smallestDistance = new HashMap<>();
        Map<Integer, Integer> timeAgo = new HashMap<>();

        int carId = car.getCarId();
        int c = meetingsSize;
        for (var m : meetings) {
            int t = -(c-- * client.getUpdateInterval());
            if (m.containsKey(carId)) {
                for (var entry : m.get(carId).entrySet()) {
                    int otherId = entry.getKey();
                    float distance = entry.getValue();

                    if (Math.abs(distance) < smallestDistance.getOrDefault(otherId, 10000f)) {
                        smallestDistance.put(otherId, distance);
                        timeAgo.put(otherId, t);
                    }
                }
            }
        }

        for (int otherId : smallestDistance.keySet()) {
            CarInfo other = client.getModel().getCar(otherId);
            float distance = smallestDistance.get(otherId);
            int t = timeAgo.get(otherId);
            LOG.info(String.format("\t%s\t%.1fm\t%ss",
                    other.getCarNumberString(),
                    distance,
                    TimeUtils.asDelta(t)
            ));
        }

    }

    private void onSessionUpdate(SessionInfo info) {
        Map<Integer, Map<Integer, Float>> m = new HashMap<>();
        int trackMeters = client.getModel().getTrackInfo().getTrackMeters();

        for (var car : client.getModel().getCarsInfo().values()) {
            float pos = car.getRealtime().getSplinePosition();
            for (var other : client.getModel().getCarsInfo().values()) {
                if (car.getCarId() == other.getCarId()) {
                    continue;
                }
                float distance = (pos - other.getRealtime().getSplinePosition()) * trackMeters;
                if (Math.abs(distance) < 2) {
                    if (!m.containsKey(car.getCarId())) {
                        m.put(car.getCarId(), new HashMap<>());
                    }
                    m.get(car.getCarId()).put(other.getCarId(), distance);
                }
            }
        }

        meetings.add(m);
        if (meetings.size() > meetingsSize) {
            meetings.remove(0);
        }
    }

    private void commitAccident(ContactInfo a) {
        EventBus.publish(new ContactEvent(a));
    }

}
