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

package nl.knaw.dans.sword2.api.error;

import nl.knaw.dans.sword2.core.config.SwordError;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "error", namespace = "http://purl.org/net/sword/")
@XmlAccessorType(XmlAccessType.FIELD)
public class Error {

    @XmlElement(namespace = "http://www.w3.org/2005/Atom")
    private String title;
    @XmlElement(namespace = "http://www.w3.org/2005/Atom")
    private String updated;
    @XmlElement(namespace = "http://www.w3.org/2005/Atom")
    private Generator generator;
    @XmlElement(namespace = "http://purl.org/net/sword/")
    private String treatment;
    @XmlElement(namespace = "http://www.w3.org/2005/Atom")
    private String summary;
    @XmlElement(namespace = "http://purl.org/net/sword/")
    private String verboseDescription;

    @XmlAttribute(name="href", required = true)
    private SwordError errorCode;

    public SwordError getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(SwordError errorCode) {
        this.errorCode = errorCode;
    }

    public String getVerboseDescription() {
        return verboseDescription;
    }

    public void setVerboseDescription(String verboseDescription) {
        this.verboseDescription = verboseDescription;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public Generator getGenerator() {
        return generator;
    }

    public void setGenerator(Generator generator) {
        this.generator = generator;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public String toString() {
        return "Error{" +
            "title='" + title + '\'' +
            ", updated='" + updated + '\'' +
            ", generator=" + generator +
            ", treatment='" + treatment + '\'' +
            ", summary='" + summary + '\'' +
            ", verboseDescription='" + verboseDescription + '\'' +
            '}';
    }
}
