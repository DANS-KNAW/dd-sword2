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
package nl.knaw.dans.sword2.core.config;

import javax.ws.rs.core.MediaType;
import javax.xml.namespace.QName;
import java.util.List;


public class UriRegistry {
    // Namespace prefixes
    public static final String SWORD_PREFIX = "sword";
    public static final String ORE_PREFIX = "ore";
    public static final String APP_PREFIX = "app";
    public static final String DC_PREFIX = "dcterms";
    public static final String ATOM_PREFIX = "atom";

    // Namespaces
    public static final String SWORD_TERMS_NAMESPACE = "http://purl.org/net/sword/terms/";
    public static final String APP_NAMESPACE = "http://www.w3.org/2007/app";
    public static final String DC_NAMESPACE = "http://purl.org/dc/terms/";
    public static final String ORE_NAMESPACE = "http://www.openarchives.org/ore/terms/";
    public static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";

    // QNames for Extension Elements
    public static final QName SWORD_VERSION = new QName(SWORD_TERMS_NAMESPACE, "version");
    public static final QName SWORD_MAX_UPLOAD_SIZE = new QName(SWORD_TERMS_NAMESPACE, "maxUploadSize");
    public static final QName SWORD_COLLECTION_POLICY = new QName(SWORD_TERMS_NAMESPACE, "collectionPolicy");
    public static final QName SWORD_MEDIATION = new QName(SWORD_TERMS_NAMESPACE, "mediation");
    public static final QName SWORD_TREATMENT = new QName(SWORD_TERMS_NAMESPACE, "treatment");
    public static final QName SWORD_ACCEPT_PACKAGING = new QName(SWORD_TERMS_NAMESPACE, "acceptPackaging");
    public static final QName SWORD_SERVICE = new QName(SWORD_TERMS_NAMESPACE, "service");
    public static final QName SWORD_PACKAGING = new QName(SWORD_TERMS_NAMESPACE, "packaging");
    public static final QName SWORD_VERBOSE_DESCRIPTION = new QName(SWORD_TERMS_NAMESPACE, "verboseDescription");
    public static final QName APP_ACCEPT = new QName(APP_NAMESPACE, "accept");
    public static final QName DC_ABSTRACT = new QName(DC_NAMESPACE, "abstract");

    // URIs for the statement
    public static final String SWORD_DEPOSITED_BY = SWORD_TERMS_NAMESPACE + "depositedBy";
    public static final String SWORD_DEPOSITED_ON_BEHALF_OF = SWORD_TERMS_NAMESPACE + "depositedOnBehalfOf";
    public static final String SWORD_DEPOSITED_ON = SWORD_TERMS_NAMESPACE + "depositedOn";
    public static final String SWORD_ORIGINAL_DEPOSIT = SWORD_TERMS_NAMESPACE + "originalDeposit";
    public static final String SWORD_STATE_DESCRIPTION = SWORD_TERMS_NAMESPACE + "stateDescription";
    public static final String SWORD_STATE = SWORD_TERMS_NAMESPACE + "state";

    // rel values
    public static final String REL_STATEMENT = "http://purl.org/net/sword/terms/statement";
    public static final String REL_SWORD_EDIT = "http://purl.org/net/sword/terms/add";
    public static final String REL_ORIGINAL_DEPOSIT = "http://purl.org/net/sword/terms/originalDeposit";
    public static final String REL_DERIVED_RESOURCE = "http://purl.org/net/sword/terms/derivedResource";

    // Package Formats
    public static final String PACKAGE_SIMPLE_ZIP = "http://purl.org/net/sword/package/SimpleZip";
    public static final String PACKAGE_BINARY = "http://purl.org/net/sword/package/Binary";
    public static final String PACKAGE_BAGIT = "http://purl.org/net/sword/package/BagIt";
    public static final  List<MediaType> supportedContentType = List.of( new MediaType("application","zip"), new MediaType("application","octet-stream"));
}
