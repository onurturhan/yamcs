package org.yamcs.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;

public class DHSHandler {
    private static final Logger log = LoggerFactory.getLogger(DHSHandler.class);

    private List<DHSData> entries = new ArrayList<>(100);
    private int currentEntry = 0;

    public DHSHandler() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(DHSHandler.class.getResourceAsStream("/landing_data/DHS.csv")))) {
            String line;
            line = in.readLine(); // skip column titles
            if (line == null) {
                throw new ConfigurationException("Empty DHS.csv file");
            }

            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");
                DHSData entry = new DHSData();

                entry.timestamp = new Float(parts[0]).floatValue();
                entry.primBusVoltage1 = new Float(parts[1]).floatValue();
                entry.primBusCurrent1 = new Float(parts[2]).floatValue();
                entry.primBusVoltage2 = new Float(parts[3]).floatValue();
                entry.primBusCurrent2 = new Float(parts[4]).floatValue();
                entry.secBusVoltage2 = new Float(parts[5]).floatValue();
                entry.secBusCurrent2 = new Float(parts[6]).floatValue();
                entry.secBusVoltage3 = new Float(parts[7]).floatValue();
                entry.secBusCurrent3 = new Float(parts[8]).floatValue();

                entries.add(entry);
            }
        } catch (IOException e) {
            System.out.println(e);
        }
        log.debug("have {} DHS data records", entries.size());
    }

    public void fillPacket(ByteBuffer buffer) {
        if (entries.isEmpty()) {
            return;
        }

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        DHSData entry = entries.get(currentEntry++);
        entry.fillPacket(buffer);
    }

    public int dataSize() {
        return DHSData.size();
    }
}
