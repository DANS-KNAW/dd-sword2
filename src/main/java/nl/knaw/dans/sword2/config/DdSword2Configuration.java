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

package nl.knaw.dans.sword2.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.HttpClientConfiguration;
import lombok.Data;
import nl.knaw.dans.sword2.config.AuthorizationConfig;
import nl.knaw.dans.sword2.config.Sword2Config;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
public class DdSword2Configuration extends Configuration {

    @Valid
    private AuthorizationConfig authorization;

    @Valid
    @NotNull
    private Sword2Config sword2;
    @Valid
    @NotNull
    private HttpClientConfiguration httpClient = new HttpClientConfiguration();
}
