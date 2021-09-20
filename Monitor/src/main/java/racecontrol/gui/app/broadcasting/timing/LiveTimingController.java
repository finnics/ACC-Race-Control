/*
 * Copyright (c) 2021 Leonard Sch�ngel
 * 
 * For licensing information see the included license (LICENSE.txt)
 */
package racecontrol.gui.app.broadcasting.timing;

import java.util.ArrayList;
import java.util.List;
import racecontrol.gui.app.broadcasting.timing.tablemodels.LiveTimingTableModel;
import racecontrol.client.events.RealtimeUpdateEvent;
import racecontrol.eventbus.Event;
import racecontrol.eventbus.EventBus;
import racecontrol.eventbus.EventListener;
import racecontrol.client.AccBroadcastingClient;
import racecontrol.client.events.SessionChangedEvent;
import racecontrol.client.data.SessionInfo;
import racecontrol.gui.lpui.LPContainer;
import java.util.logging.Logger;
import static racecontrol.client.extension.statistics.CarProperties.CAR_ID;
import racecontrol.client.extension.statistics.CarStatistics;
import racecontrol.client.extension.statistics.StatisticsExtension;
import racecontrol.gui.app.broadcasting.timing.tablemodels.QualifyingTableModel;
import racecontrol.gui.lpui.LPTable;

/**
 *
 * @author Leonard
 */
public class LiveTimingController
        implements EventListener {

    /**
     * This classes logger.
     */
    private static final Logger LOG = Logger.getLogger(LiveTimingController.class.getName());
    /**
     * Reference to the connection client.
     */
    private final AccBroadcastingClient client;
    /**
     * The statistics extension.
     */
    private final StatisticsExtension statisticsExtension;
    /**
     * The live timing table.
     */
    private final LPTable table = new LPTable();
    /**
     * Table model to display the live timing.
     */
    private LiveTimingTableModel model = new QualifyingTableModel();
    /**
     * timestamp for the last time the table was clicked.
     */
    private long lastTableClick = 0;
    /**
     * Last row that was clicked.
     */
    private int lastTableClickRow = -1;

    public LiveTimingController() {
        EventBus.register(this);
        statisticsExtension = StatisticsExtension.getInstance();
        client = AccBroadcastingClient.getClient();
        table.setTableModel(model);
        table.setCellClickAction((column, row) -> onCellClickAction(column, row));
    }

    public LPContainer getPanel() {
        return table;
    }

    @Override
    public void onEvent(Event e) {
        if (e instanceof RealtimeUpdateEvent) {
            onRealtimeUpdate(((RealtimeUpdateEvent) e).getSessionInfo());
        }
    }

    public void onRealtimeUpdate(SessionInfo sessionInfo) {
        List<CarStatistics> cars = new ArrayList<>();
        client.getModel().getCarsInfo().values().forEach(
                car -> cars.add(statisticsExtension.getCar(car.getCarId()))
        );

        model.setEntries(cars);
        model.sort();
        table.invalidate();

    }

    private void onCellClickAction(int column, int row) {
        //We want to change the focused car when we double click
        if (row == lastTableClickRow) {
            long now = System.currentTimeMillis();
            if (now - lastTableClick < 500) {
                //was double click
                client.sendChangeFocusRequest(
                        ((CarStatistics) model.getEntry(row)).get(CAR_ID));
            }
        }
        lastTableClickRow = row;
        lastTableClick = System.currentTimeMillis();
    }

}
