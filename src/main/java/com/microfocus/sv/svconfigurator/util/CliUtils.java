/*
 *  Certain versions of software and/or documents ("Material") accessible here may contain branding from
 *  Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 *  the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 *  and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 *  marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * Copyright (c) 2012-2018 Micro Focus or one of its affiliates.
 *
 * The only warranties for products and services of Micro Focus and its affiliates
 * and licensors ("Micro Focus") are set forth in the express warranty statements
 * accompanying such products and services. Nothing herein should be construed as
 * constituting an additional warranty. Micro Focus shall not be liable for technical
 * or editorial errors or omissions contained herein.
 * The information contained herein is subject to change without notice.
 * __________________________________________________________________
 *
 */
package com.microfocus.sv.svconfigurator.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microfocus.sv.svconfigurator.core.IProject;
import com.microfocus.sv.svconfigurator.core.impl.Server;
import com.microfocus.sv.svconfigurator.core.impl.exception.AbstractSVCException;
import com.microfocus.sv.svconfigurator.core.impl.exception.SVCParseException;
import com.microfocus.sv.svconfigurator.core.impl.processor.Credentials;
import com.microfocus.sv.svconfigurator.core.server.ServerParser;

public class CliUtils {

    private static final Logger LOG = LoggerFactory.getLogger(CliUtils.class);

    private static final String PARAM_URL = "url";
    private static final String LONG_PARAM_URL = "mgmt-url";
    private static final String PARAM_USER = "usr";
    private static final String LONG_PARAM_USER = "username";
    private static final String PARAM_PASS = "pwd";
    private static final String LONG_PARAM_PASS = "password";
    private static final String LONG_SERVERS_PARAM = "servers";
    private static final String LONG_USE_SERVER_PARAM = "use-server";
    public static final String DEFAULT_SERVER_ID = "Default";

    // ============================== STATIC ATTRIBUTES
    // ========================================

    // ============================== INSTANCE ATTRIBUTES
    // ======================================

    // ============================== STATIC METHODS
    // ===========================================

    /**
     * @param usage
     * @param properties
     *            -uri, -f, -r properties
     * @param mandatParams
     *            <project_file>, ... mandatParams
     */
    public static void printHelp(String usage, Options properties,
            Options mandatParams) {
        printPropertyHelp(usage, properties);
        printMandatParamsHelp(mandatParams);
    }

    public static void printPropertyHelp(String usage, Options props) {
        HelpFormatter formatter = new HelpFormatter();

        formatter.setLongOptPrefix("--");
        formatter.setLeftPadding(2);

        formatter.printHelp(usage, "Parameters: ", props, "");
    }

    public static void printMandatParamsHelp(Options param) {
        if (param == null) {
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos);

        pw.append("Mandatory Parameters: \n");

        HelpFormatter formatter = new HelpFormatter();

        formatter.setLongOptPrefix("");
        formatter.setOptPrefix("");

        formatter.printOptions(pw, formatter.getWidth(), param, 2, 3);
        pw.close();
        System.out.println(baos.toString());
    }

    /**
     * Appends the Management endpoint connection info options
     *
     * @param opts
     * @return
     */
    public static Options addConnectionOptions(Options opts) {
        opts.addOption(PARAM_URL, LONG_PARAM_URL, true,
                "URL of the server management endpoint.");
        opts.addOption(PARAM_USER, LONG_PARAM_USER, true,
                "Username for server management endpoint connection");
        opts.addOption(PARAM_PASS, LONG_PARAM_PASS, true,
                "Password for server management endpoint connection");
        opts.addOption(
                null,
                LONG_SERVERS_PARAM,
                true,
                "A file containing properties of servers (management URL, username, and password)");
        opts.addOption(null, LONG_USE_SERVER_PARAM, true,
                "Selected server ID from the property file described by --" + LONG_SERVERS_PARAM
                        + " parameter. Just the selected server will be used.");

        return opts;
    }

    public static Credentials obtainCredentials(CommandLine line) {
        String username = line.hasOption(PARAM_USER) ? line
                .getOptionValue(PARAM_USER) : null;
        String password = line.hasOption(PARAM_PASS) ? line
                .getOptionValue(PARAM_PASS) : null;
        Credentials credentials = username != null ? new Credentials(username,
                password) : null;
        return credentials;
    }

    public static List<Server> obtainServers(CommandLine line, IProject project)
            throws AbstractSVCException {
        return obtainServers(line, project, false);
    }

    public static List<Server> obtainServers(CommandLine line,
            IProject project, boolean justOneServer)
            throws AbstractSVCException {
        if (line.hasOption(LONG_SERVERS_PARAM)) {
            if (project != null) {
                LOG.info("Skipping project URL '" + project.getServerUrl()
                        + "'");
            }

            String filePath = line.getOptionValue(LONG_SERVERS_PARAM);
            File file = new File(filePath);
            if (!file.exists() && !file.isFile() && !file.canRead()) {
                throw new SVCParseException(
                        "Defined file '"
                                + file
                                + "' does not exist, or is not a file, or is not readable.");
            }

            List<Server> servers = ServerParser.parseServers(
                    file,
                    line.hasOption(LONG_USE_SERVER_PARAM) ? line
                            .getOptionValue(LONG_USE_SERVER_PARAM) : null);
            if (servers == null || servers.isEmpty()) {
                throw new SVCParseException(
                        "No server found in the defined server file '"
                                + filePath + "'");
            }

            if (justOneServer && servers.size() != 1) {
                throw new SVCParseException(
                        "Only one SV server is supported by this command. Use --"
                                + LONG_USE_SERVER_PARAM
                                + " <Server ID> to select just one server. Defined server IDs: "
                                + servers);
            }
            return servers;
        } else {
            Server srv = obtainMgmtEndpointInfo(line);
            if (srv == null
                    || (srv.getURL() == null && (project == null || project
                            .getServerUrl() == null))) {
                throw new SVCParseException("No server management URL defined");
            } else if (srv.getURL() == null) {
                srv = new Server(srv.getId(), project.getServerUrl(),
                        srv.getCredentials());
            }
            return Arrays.asList(srv);
        }
    }

    /**
     * Obtains the information about management endpoint connection from the
     * command line
     *
     * @param line
     * @return
     * @throws org.apache.commons.cli.ParseException
     *             if there is no management endpoint connection info in the
     *             command line.
     */
    public static Server obtainMgmtEndpointInfo(CommandLine line)
            throws SVCParseException {
        try {
            URL mgmtUri = line.hasOption(PARAM_URL) ? new URL(
                    line.getOptionValue(PARAM_URL)) : null;
            String username = line.hasOption(PARAM_USER) ? line
                    .getOptionValue(PARAM_USER) : null;
            String password = line.hasOption(PARAM_PASS) ? line
                    .getOptionValue(PARAM_PASS) : null;
            Credentials credentials = username != null ? new Credentials(
                    username, password) : null;

            return new Server(DEFAULT_SERVER_ID, mgmtUri, credentials);
        } catch (MalformedURLException e) {
            throw new SVCParseException("Invalid URL defined: '"
                    + line.getOptionValue(PARAM_URL) + "'", e);
        }
    }

    // ============================== CONSTRUCTORS
    // =============================================

    // ============================== ABSTRACT METHODS
    // =========================================

    // ============================== OVERRIDEN METHODS
    // ========================================

    // ============================== INSTANCE METHODS
    // =========================================

    // ============================== PRIVATE METHODS
    // ==========================================

    // ============================== GETTERS / SETTERS
    // ========================================

    // ============================== INNER CLASSES
    // ============================================

}
