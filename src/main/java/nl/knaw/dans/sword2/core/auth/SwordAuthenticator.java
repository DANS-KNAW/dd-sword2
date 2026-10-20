/*
 * Copyright (C) 2022 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.sword2.core.auth;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import nl.knaw.dans.sword2.config.DefaultUserConfig;
import nl.knaw.dans.sword2.config.UserConfig;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SwordAuthenticator implements Authenticator<HeaderCredentials, Depositor> {

    private static final Logger log = LoggerFactory.getLogger(SwordAuthenticator.class);

    private final List<UserConfig> userConfigs;

    private final DefaultUserConfig defaultUserConfig;

    private final AuthenticationService authenticationService;

    public SwordAuthenticator(List<UserConfig> userConfigs, DefaultUserConfig defaultUserConfig, AuthenticationService authenticationService) {
        this.userConfigs = userConfigs;
        this.defaultUserConfig = defaultUserConfig;
        this.authenticationService = authenticationService;
    }

    @Override
    public Optional<Depositor> authenticate(HeaderCredentials credentials) throws AuthenticationException {
        Optional<Depositor> depositor = Optional.empty();
        Optional<String> userName;
        var basicCredentials = credentials.getBasicCredentials();
        if (basicCredentials != null) {
            log.debug("Basic credentials found, checking if user is configured locally");
            var optUserConfig = getUserConfigByName(basicCredentials.getUsername());

            if (optUserConfig.isPresent()) {
                log.debug("User specific config found");
                var userConfig = optUserConfig.get();
                if (userConfig.getPasswordHash() != null) {
                    log.debug("User is configured with a password hash, validating password for user {}", userConfig.getName());

                    if (checkPassword(basicCredentials.getPassword(), userConfig)) {
                        depositor = Optional.of(new Depositor(userConfig.getName(), userConfig.getFilepathMapping(), Set.copyOf(userConfig.getCollections())));
                    }
                }
                else {
                    log.debug("User is not configured with a password hash, forwarding request to passwordDelegate");
                    userName = delegateAuthentication(credentials);
                    if (userName.isPresent()) {
                        if (!userConfig.getName().equals(userName.get())) {
                            throw new AuthenticationException("Usernames do not match, expected " + userConfig.getName() + " but got " + userName.get() + " from passwordDelegate");
                        }
                        depositor = Optional.of(new Depositor(userConfig.getName(), userConfig.getFilepathMapping(), Set.copyOf(userConfig.getCollections())));
                    }
                    else {
                        depositor = Optional.empty();
                    }
                }
            }
            else {
                log.debug("User is not found in config file; using delegate and default user config by default");
                userName = delegateAuthentication(credentials);
                depositor = userName
                    .map(u -> new Depositor(u, defaultUserConfig.getFilepathMapping(), new HashSet<>(defaultUserConfig.getCollections())));
            }
        }
        else {
            log.debug("No basic credentials provided, forwarding request to passwordDelegate");
            userName = delegateAuthentication(credentials);
            depositor = userName
                .map(u -> new Depositor(u, defaultUserConfig.getFilepathMapping(), new HashSet<>(defaultUserConfig.getCollections())));
        }

        return depositor;
    }

    private boolean checkPassword(String password, UserConfig userConfig) throws AuthenticationException {
        if (!userConfig.isUnsaltedSha1()) {
            return BCrypt.checkpw(password, userConfig.getPasswordHash());
        }
        try {
            var digest = MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(Base64.getEncoder().encode(digest), userConfig.getPasswordHash().getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException e) {
            throw new AuthenticationException("SHA-1 algorithm is unavailable", e);
        }
    }

    private Optional<String> delegateAuthentication(HeaderCredentials credentials) throws AuthenticationException {
        if (authenticationService != null) {
            return authenticationService.authenticateWithHeaders(credentials.getHeaders());
        }
        return Optional.empty();
    }

    Optional<UserConfig> getUserConfigByName(String name) {
        return userConfigs.stream()
            .filter(u -> u.getName().equals(name))
            .findFirst();
    }
}
