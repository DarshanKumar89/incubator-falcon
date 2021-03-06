/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.falcon.security;

import org.apache.commons.lang.Validate;
import org.apache.falcon.FalconException;
import org.apache.falcon.service.FalconService;
import org.apache.falcon.util.StartupProperties;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authentication.server.KerberosAuthenticationHandler;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Properties;


/**
 * Authentication Service at startup that initializes the authentication credentials
 * based on authentication type. If Kerberos is enabled, it logs in the user with the key tab.
 */
public class AuthenticationInitializationService implements FalconService {

    private static final Logger LOG = Logger.getLogger(AuthenticationInitializationService.class);

    /**
     * Constant for the configuration property that indicates the prefix.
     */
    protected static final String CONFIG_PREFIX = "falcon.service.authentication.";

    /**
     * Constant for the configuration property that indicates the keytab file path.
     */
    protected static final String KERBEROS_KEYTAB = CONFIG_PREFIX + KerberosAuthenticationHandler.KEYTAB;
    /**
     * Constant for the configuration property that indicates the kerberos principal.
     */
    protected static final String KERBEROS_PRINCIPAL = CONFIG_PREFIX + KerberosAuthenticationHandler.PRINCIPAL;


    @Override
    public String getName() {
        return "Authentication initialization service";
    }

    @Override
    public void init() throws FalconException {

        if (SecurityUtil.isSecurityEnabled()) {
            LOG.info("Falcon Kerberos Authentication Enabled!");
            initializeKerberos();
        } else {
            LOG.info("Falcon Simple Authentication Enabled!");
            Configuration ugiConf = new Configuration();
            ugiConf.set("hadoop.security.authentication", "simple");
            UserGroupInformation.setConfiguration(ugiConf);
        }
    }

    protected void initializeKerberos() throws FalconException {
        try {
            Properties configuration = StartupProperties.get();
            String principal = configuration.getProperty(KERBEROS_PRINCIPAL);
            Validate.notEmpty(principal,
                    "Missing required configuration property: " + KERBEROS_PRINCIPAL);
            principal = org.apache.hadoop.security.SecurityUtil.getServerPrincipal(
                    principal, SecurityUtil.getLocalHostName());

            String keytabFilePath = configuration.getProperty(KERBEROS_KEYTAB);
            Validate.notEmpty(keytabFilePath,
                    "Missing required configuration property: " + KERBEROS_KEYTAB);
            checkIsReadable(keytabFilePath);

            Configuration conf = new Configuration();
            conf.set("hadoop.security.authentication", "kerberos");

            UserGroupInformation.setConfiguration(conf);
            UserGroupInformation.loginUserFromKeytab(principal, keytabFilePath);

            LOG.info("Got Kerberos ticket, keytab: " + keytabFilePath
                    + ", Falcon principal principal: " + principal);
        } catch (Exception ex) {
            throw new FalconException("Could not initialize " + getName()
                    + ": " + ex.getMessage(), ex);
        }
    }

    private static void checkIsReadable(String keytabFilePath) {
        File keytabFile = new File(keytabFilePath);
        if (!keytabFile.exists()) {
            throw new IllegalArgumentException("The keytab file does not exist! " + keytabFilePath);
        }

        if (!keytabFile.isFile()) {
            throw new IllegalArgumentException("The keytab file cannot be a directory! " + keytabFilePath);
        }

        if (!keytabFile.canRead()) {
            throw new IllegalArgumentException("The keytab file is not readable! " + keytabFilePath);
        }
    }

    @Override
    public void destroy() throws FalconException {
    }
}
