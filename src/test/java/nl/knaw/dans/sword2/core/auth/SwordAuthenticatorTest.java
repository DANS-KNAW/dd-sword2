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
import nl.knaw.dans.sword2.config.DefaultUserConfig;
import nl.knaw.dans.sword2.config.PasswordDelegateConfig;
import nl.knaw.dans.sword2.config.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.Mockito;

import javax.ws.rs.core.MultivaluedHashMap;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwordAuthenticatorTest {

    private final AuthenticationService authenticationService = Mockito.mock(AuthenticationService.class);
    private final URL passwordDelegate = new URL("http://test.com/");

    SwordAuthenticatorTest() throws MalformedURLException {
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(authenticationService);
    }

    private SwordAuthenticator getAuthenticator(List<UserConfig> userConfigs) {
        var delegate = new PasswordDelegateConfig();
        delegate.setForwardHeaders(List.of("x-dataverse-key", "authorization"));
        delegate.setUrl(passwordDelegate);
        var defaultUserConfig = new DefaultUserConfig();
        defaultUserConfig.setPasswordDelegate(delegate);
        defaultUserConfig.setCollections(Collections.emptyList());
        return new SwordAuthenticator(userConfigs, defaultUserConfig, authenticationService);
    }

    private HeaderCredentials buildCredentials(String username, String password, String header) {
        var headers = new MultivaluedHashMap<String, String>();

        if (header != null) {
            headers.put("x-dataverse-key", List.of(header));
        }

        if (username != null) {
            var formatted = String.format("%s:%s", username, password);
            var pass = Base64.getEncoder().encodeToString(formatted.getBytes());
            headers.putSingle("Authorization", "Basic " + pass);
        }

        return new HeaderCredentials(headers);
    }

    @Test
    public void authenticate_should_return_empty_optional_if_no_users_are_configured() {
        var result = assertDoesNotThrow(() ->
            getAuthenticator(List.of()).authenticate(
                buildCredentials("user", "password", null)
            ));

        assertTrue(result.isEmpty());
    }

    @Test
    public void authenticate_should_return_empty_optional_if_password_is_incorrect() throws AuthenticationException {
        var encryptedPassword = BCrypt.hashpw("password", BCrypt.gensalt());
        var userList = List.of(new UserConfig("user001", encryptedPassword, false, new ArrayList<>(), false));

        var result = getAuthenticator(userList).authenticate(buildCredentials("user001", "incorrect_password", null));
        assertTrue(result.isEmpty());
    }

    @Test
    public void authenticate_should_return_user_if_username_and_password_are_correct() throws AuthenticationException {
        var encryptedPassword = BCrypt.hashpw("password", BCrypt.gensalt());
        var userList = List.of(new UserConfig("user001", encryptedPassword, false, new ArrayList<>(), false));

        var result = getAuthenticator(userList).authenticate(buildCredentials("user001", "password", null));
        assertThat(result).isPresent();
        assertEquals("user001", result.get().getName());
    }

    @Test
    public void authenticate_should_call_delegate_http_service_if_config_says_so() throws AuthenticationException {
        var userList = List.of(new UserConfig("user001", null, false, new ArrayList<>(), false));

        Mockito.when(authenticationService.authenticateWithHeaders(Mockito.any()))
            .thenReturn(Optional.of("user001"));

        var result = getAuthenticator(userList).authenticate(buildCredentials("user001", "password", null));
        assertThat(result).isPresent();
        assertEquals("user001", result.get().getName());
    }

    @Test
    public void authenticate_should_return_empty_optional_if_delegate_returns_different_username() throws AuthenticationException {
        var userList = List.of(new UserConfig("user001", null, false, new ArrayList<>(), false));

        Mockito.when(authenticationService.authenticateWithHeaders(Mockito.any()))
            .thenReturn(Optional.of("user002"));

        assertThrows(AuthenticationException.class, () -> getAuthenticator(userList).authenticate(buildCredentials("user001", "password", null)));
    }

    @Test
    public void authenticate_should_return_empty_optional_if_delegate_returns_401_unauthorized_with_basic_authentication() throws AuthenticationException {
        var userList = List.of(new UserConfig("user001", null, false, new ArrayList<>(), false));

        Mockito.when(authenticationService.authenticateWithHeaders(Mockito.any()))
            .thenReturn(Optional.empty());

        var result = getAuthenticator(userList).authenticate(buildCredentials("user001", "password", null));
        assertThat(result).isEmpty();
    }

    @Test
    public void authenticate_should_return_user_if_header_credentials_are_correct() throws AuthenticationException {
        var userList = List.of(new UserConfig("user001", null, false, new ArrayList<>(), false));

        Mockito.when(authenticationService.authenticateWithHeaders(Mockito.any()))
            .thenReturn(Optional.of("user001"));

        var headers = new MultivaluedHashMap<String, String>();
        headers.putSingle("x-dataverse-key", "test");

        var result = getAuthenticator(userList).authenticate(new HeaderCredentials(headers));
        assertThat(result).isPresent();
        assertEquals("user001", result.get().getName());
    }

    @Test
    public void authenticate_should_return_empty_optional_if_delegate_returns_401_unauthorized_with_other_authentication() throws AuthenticationException {
        var userList = List.of(new UserConfig("user001", null, false, new ArrayList<>(), false));

        Mockito.when(authenticationService.authenticateWithHeaders(Mockito.any()))
            .thenReturn(Optional.empty());

        var headers = new MultivaluedHashMap<String, String>();
        headers.putSingle("x-dataverse-key", "test");
        var result = getAuthenticator(userList).authenticate(new HeaderCredentials(headers));
        assertThat(result).isEmpty();
    }

    @Test
    public void authenticate_should_return_empty_optional_if_authenticationService_is_null() {
        var userList = List.of(new UserConfig("user001", null, false, new ArrayList<>(), false));

        var authenticator = new SwordAuthenticator(userList, null, null);
        var result = assertDoesNotThrow(() -> authenticator.authenticate(buildCredentials("user001", "password", null)));
        assertTrue(result.isEmpty());
    }

    @Test
    public void authenticate_should_propagate_AuthenticationException() throws AuthenticationException {
        var userList = List.of(new UserConfig("user001", null, false, new ArrayList<>(), false));

        Mockito.doThrow(AuthenticationException.class)
            .when(authenticationService).authenticateWithHeaders(Mockito.any());
        assertThrows(AuthenticationException.class, () -> getAuthenticator(userList)
            .authenticate(
                buildCredentials("user001", "password", null)
            )
        );
    }
}
