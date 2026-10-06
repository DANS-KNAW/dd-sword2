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

import io.dropwizard.util.DataSize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.knaw.dans.sword2.core.DepositState;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionConfig {
    @NotEmpty
    private String name;
    @NotEmpty
    private String path;
    @NotNull
    private Path uploads;
    @NotNull
    private Path deposits;
    @NotNull
    private DataSize diskSpaceMargin = DataSize.gigabytes(2);
    @NotNull
    private List<Path> depositTrackingPath = new ArrayList<>();
    private List<DepositState> autoClean = new ArrayList<>();
}
