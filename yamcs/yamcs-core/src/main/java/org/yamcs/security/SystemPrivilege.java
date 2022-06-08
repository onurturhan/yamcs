package org.yamcs.security;

import java.util.Objects;

/**
 * A system privilege is the right to perform a particular action or to perform an action on any object of a particular
 * type.
 * <p>
 * There is no 'wildcard' that represent all system privileges. For such behaviour you should instead consider granting
 * a user the 'superuser' attribute.
 * <p>
 * This is not an enum because of extensibility reasons.
 */
public class SystemPrivilege {

    public static final SystemPrivilege ControlProcessor = new SystemPrivilege("ControlProcessor");
    public static final SystemPrivilege ReadCommandHistory = new SystemPrivilege("ReadCommandHistory");
    public static final SystemPrivilege ModifyCommandHistory = new SystemPrivilege("ModifyCommandHistory");

    /**
     * Allows to control the state of command queues.
     */
    public static final SystemPrivilege ControlCommandQueue = new SystemPrivilege("ControlCommandQueue");

    /**
     * Allows to clear users for commanding.
     */
    public static final SystemPrivilege ControlCommandClearances = new SystemPrivilege("ControlCommandClearances");

    /**
     * Allows to control file transfers.
     */
    public static final SystemPrivilege ControlFileTransfers = new SystemPrivilege("ControlFileTransfers");

    /**
     * Allows specifying command options (extra attributes in the command history, disable/modify verifiers)
     */
    public static final SystemPrivilege CommandOptions = new SystemPrivilege("CommandOptions");

    /**
     * Allows to read the entire Mission Database.
     */
    public static final SystemPrivilege GetMissionDatabase = new SystemPrivilege("GetMissionDatabase");

    /**
     * Allows to read alarm state
     */
    public static final SystemPrivilege ReadAlarms = new SystemPrivilege("ReadAlarms");

    /**
     * Allows to read alarm state
     */
    public static final SystemPrivilege ControlAlarms = new SystemPrivilege("ControlAlarms");

    public static final SystemPrivilege ControlArchiving = new SystemPrivilege("ControlArchiving");

    /**
     * Allows to read link state.
     */
    public static final SystemPrivilege ReadLinks = new SystemPrivilege("ReadLinks");

    /**
     * Allows to read link state.
     */
    public static final SystemPrivilege ControlLinks = new SystemPrivilege("ControlLinks");

    /**
     * Allows to control the lifecycle of services
     */
    public static final SystemPrivilege ControlServices = new SystemPrivilege("ControlServices");

    public static final SystemPrivilege CreateInstances = new SystemPrivilege("CreateInstances");

    /**
     * Allows to manage buckets of any kind
     */
    public static final SystemPrivilege ManageAnyBucket = new SystemPrivilege("ManageAnyBucket");

    public static final SystemPrivilege ReadEvents = new SystemPrivilege("ReadEvents");
    public static final SystemPrivilege WriteEvents = new SystemPrivilege("WriteEvents");
    public static final SystemPrivilege WriteTables = new SystemPrivilege("WriteTables");
    public static final SystemPrivilege ReadTables = new SystemPrivilege("ReadTables");

    /**
     * Allows to read thread information
     */
    public static final SystemPrivilege ReadThreads = new SystemPrivilege("ReadThreads");

    /**
     * Allows to change online the MDB (calibrators, alarms and algorithms)
     */
    public static final SystemPrivilege ChangeMissionDatabase = new SystemPrivilege("ChangeMissionDatabase");

    /**
     * Allows to control time correlation
     */
    public static final SystemPrivilege ControlTimeCorrelation = new SystemPrivilege("ControlTimeCorrelation");

    /**
     * Allows to view the timeline
     */
    public static final SystemPrivilege ReadTimeline = new SystemPrivilege("ReadTimeline");

    /**
     * Allows to modify the timeline
     */
    public static final SystemPrivilege ControlTimeline = new SystemPrivilege("ControlTimeline");

    private String name;

    public SystemPrivilege(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SystemPrivilege) {
            return Objects.equals(name, ((SystemPrivilege) obj).name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
