/**
 * BSD 3-Clause License
 *
 * Copyright © 2019, viadee Unternehmensberatung AG
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.bpm.vPAV.config.reader;

import de.viadee.bpm.vPAV.RuntimeConfig;
import de.viadee.bpm.vPAV.config.model.Rule;
import de.viadee.bpm.vPAV.config.model.RuleSet;
import de.viadee.bpm.vPAV.constants.ConfigConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XmlConfigReaderTest {

    private static ClassLoader cl;

    @BeforeClass
    public static void setup() throws MalformedURLException {
        final File file = new File(".");
        final String currentPath = file.toURI().toURL().toString();
        final URL classUrl = new URL(currentPath + "src/test/java");
        final URL[] classUrls = { classUrl };
        cl = new URLClassLoader(classUrls);
        RuntimeConfig.getInstance().setClassLoader(cl);
    }

    /**
     * Test loading a correct config file
     *
     * @throws ConfigReaderException
     */
    @Test()
    public void testLoadingCorrectXMLConfigFile() throws ConfigReaderException {
        // Given
        XmlConfigReader reader = new XmlConfigReader();

        // When
        RuleSet result = reader.read(ConfigConstants.RULESET);

        // Then
        assertFalse("No rules could be read", result.getElementRules().isEmpty());
        for (Map.Entry<String, Map<String, Rule>> entry : result.getElementRules().entrySet()) {
            assertFalse("Rules of type " + entry.getKey() + " could not be read.", entry.getValue().isEmpty());
        }
        assertTrue("ID of XorNamingConventionChecker was not loaded.", result.getElementRules().get("XorNamingConventionChecker").containsKey("XorCheckerId"));
    }

    /**
     * Test loading a non-existing config file and check for defaults
     *
     * @throws ConfigReaderException
     */
    @Test()
    public void testLoadingNonExistingXMLConfigFile() throws ConfigReaderException {
        // Given
        XmlConfigReader reader = new XmlConfigReader();

        // When
        try {
            RuleSet result = reader.read("non-existing.xml");
            assertTrue("Exception expected, but no one was thrown.", result != null);
        } catch (ConfigReaderException e) {
            // load DefaultRuleSet
            Map<String, Map<String, Rule>> result = reader.read("ruleSetDefault.xml").getElementRules();

            // Default rules correct
            assertTrue("False Default ruleSet ", result.get("JavaDelegateChecker").get("JavaDelegateChecker").isActive());
            assertTrue("False Default ruleSet ", result.get("EmbeddedGroovyScriptChecker").get("EmbeddedGroovyScriptChecker").isActive());
            assertFalse("False Default ruleSet ", result.get("VersioningChecker").get("VersioningChecker").isActive());
            assertFalse("False Default ruleSet ", result.get("DmnTaskChecker").get("DmnTaskChecker").isActive());
//            assertFalse("False Default ruleSet ", result.get("ProcessVariablesModelChecker").isActive());
            assertFalse("False Default ruleSet ", result.get("ProcessVariablesNameConventionChecker").get("ProcessVariablesNameConventionChecker").isActive());
            assertFalse("False Default ruleSet ", result.get("TaskNamingConventionChecker").get("TaskNamingConventionChecker").isActive());
        }
    }

    /**
     * Test loading an incorrect config file (rulename empty)
     *
     *
     */
    @Test(expected = ConfigReaderException.class)
    public void testLoadingIncorrectXMLNameConfigFile() throws ConfigReaderException {
        // Given
        XmlConfigReader reader = new XmlConfigReader();

        // When Then
        reader.read("ruleSetIncorrectName.xml");

    }

    /**
     * Test loading an incorrect config file (no xml)
     */
    @Test(expected = ConfigReaderException.class)
    public void testLoadingIncorrectXMLConfigFile() throws ConfigReaderException {
        // Given
        XmlConfigReader reader = new XmlConfigReader();

        // When Then
        reader.read("ruleSetIncorrect.xml");

    }

}
