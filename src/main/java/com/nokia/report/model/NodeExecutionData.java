package com.nokia.report.model;

import java.util.LinkedHashMap;

/**
 * Holds execution data for a single node.
 *
 * commands: insertion-ordered map of command string → result detail,
 * preserving the execution sequence from the JSON file.
 */
public class NodeExecutionData {

    private final String nodeName;
    private final LinkedHashMap<String, CommandResultDetail> commands;
    private String activityStartTime;
    private String activityEndTime;

    public NodeExecutionData(String nodeName, LinkedHashMap<String, CommandResultDetail> commands) {
        this.nodeName = nodeName;
        this.commands = commands;
    }

    public String getNodeName()                                      { return nodeName; }
    public LinkedHashMap<String, CommandResultDetail> getCommands()  { return commands; }

    public String getActivityStartTime()                             { return activityStartTime; }
    public void setActivityStartTime(String activityStartTime)       { this.activityStartTime = activityStartTime; }

    public String getActivityEndTime()                               { return activityEndTime; }
    public void setActivityEndTime(String activityEndTime)           { this.activityEndTime = activityEndTime; }
}
