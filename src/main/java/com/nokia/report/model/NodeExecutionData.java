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

    public NodeExecutionData(String nodeName, LinkedHashMap<String, CommandResultDetail> commands) {
        this.nodeName = nodeName;
        this.commands = commands;
    }

    public String getNodeName() { return nodeName; }
    public LinkedHashMap<String, CommandResultDetail> getCommands() { return commands; }
}
