/*
 * Copyright (c) 2021 Leonard Sch�ngel
 * 
 * For licensing information see the included license (LICENSE.txt)
 */
package base.screen.networking.events;

import base.screen.eventbus.Event;
import base.screen.networking.SessionId;
import base.screen.networking.data.SessionInfo;

/**
 *
 * @author Leonard
 */
public class SessionChanged extends Event {

    private final SessionId sessionId;
    private final SessionInfo sessionInfo;
    private final boolean initialisation;

    public SessionChanged(SessionId sessionId,
            SessionInfo sessionInfo,
            boolean initialisation) {
        this.sessionId = sessionId;
        this.sessionInfo = sessionInfo;
        this.initialisation = initialisation;
    }

    public SessionId getSessionId() {
        return sessionId;
    }

    public SessionInfo getSessionInfo() {
        return sessionInfo;
    }
    
    public boolean isInitialisation(){
        return initialisation;
    }

}
