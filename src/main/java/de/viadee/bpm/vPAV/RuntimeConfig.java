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
package de.viadee.bpm.vPAV;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.springframework.context.ApplicationContext;

import de.viadee.bpm.vPAV.config.model.RuleSet;
import de.viadee.bpm.vPAV.constants.ConfigConstants;

public class RuntimeConfig {

    private static RuntimeConfig instance;

    private ApplicationContext ctx;

    private Map<String, String> beanMap;

    private RuleSet ruleSet;

    private ClassLoader classLoader;

    private ResourceBundle resourceBundle;

    private boolean test = false; // TODO: Replace with parameterized method calls, to improve separation of test
    // and production code

    private static Logger LOGGER = Logger.getLogger(RuntimeConfig.class.getName());

    private final String[] viadeeConfigRules = { "CreateOutputHTML" };

    private final String[] viadeeElementRules = { "XorConventionChecker", "TimerExpressionChecker",
            "JavaDelegateChecker", "NoScriptChecker", "NoExpressionChecker", "EmbeddedGroovyScriptChecker",
            "VersioningChecker", "DmnTaskChecker", "ProcessVariablesNameConventionChecker",
            "TaskNamingConventionChecker", "ElementIdConventionChecker", "MessageEventChecker", "FieldInjectionChecker",
            "BoundaryErrorChecker", "ExtensionChecker", "OverlapChecker", "SignalEventChecker",
            "MessageCorrelationChecker" };

    private final String[] viadeeModelRules = { "ProcessVariablesModelChecker", "DataFlowChecker" };

    private final URL[] urls = setURLs();

    private RuntimeConfig() {
    }

    public static RuntimeConfig getInstance() {
        if (RuntimeConfig.instance == null) {
            RuntimeConfig.instance = new RuntimeConfig();
        }
        return RuntimeConfig.instance;
    }

    String findBeanByName(String string) {
        if (string != null && !string.isEmpty() && beanMap != null && !beanMap.isEmpty()) {
            return beanMap.get(string);
        } else
            return null;
    }

    public void setBeanMapping(Map<String, String> beanMap) {
        this.beanMap = beanMap;
    }

    public Map<String, String> getBeanMapping() {
        return beanMap;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public boolean isTest() {
        return test;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    public String[] getViadeeRules() {
        return Stream.of(viadeeConfigRules, viadeeElementRules, viadeeModelRules).flatMap(Stream::of)
                .toArray(String[]::new);
    }

    public String[] getViadeeElementRules() {
        return viadeeElementRules;
    }

    public ArrayList<String> getActiveRules() {
        return new ArrayList<>(ruleSet.getAllActiveRules().keySet());
    }

    public void setRuleSet(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    void setApplicationContext(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public ApplicationContext getApplicationContext() {
        return ctx;
    }

    /**
     * Retrieve locale. If locale can not be retrieved, use system locale
     */
    void retrieveLocale() {
        if (ConfigConstants.getInstance().getLanguage().equals("de_DE")) {
            getResource("de_DE");
        } else {
            getResource("en_US");
        }
    }

    /**
     * Set base directory and set ResourceBundle
     *
     * @param locale Locale extracted from ruleSet or either default system locale
     *               Localization
     */
    public void getResource(final String locale) {
        setResourceBundle(fromClassLoader("messages_" + locale));
    }

    /**
     * Retrieves ResourceBundle from base directy and returns it to RuntimeConfig
     *
     * @param bundleName Bundle name for localization
     * @return ResourceBundle
     */
    private ResourceBundle fromClassLoader(final String bundleName) {
        URL[] urls = getURLs();
        ClassLoader loader = new URLClassLoader(urls);
        return ResourceBundle.getBundle(bundleName, Locale.getDefault(), loader);
    }

    /**
     * Retrieves URLs from java classpath
     *
     * @return Array of URLs
     */
    private URL[] setURLs() {
        String pathSeparator = System.getProperty("path.separator");

        String[] classPathEntries = System.getProperty("java.class.path").split(pathSeparator);

        ArrayList<URL> urlArrayList = new ArrayList<>();
        Arrays.asList(classPathEntries).forEach(entry -> {
            try {
                urlArrayList.add((new File(entry)).toURI().toURL());
            } catch (MalformedURLException ignored) {
                LOGGER.warning("Resource " + entry + " could not be loaded");
            }
        });
        return urlArrayList.toArray(new URL[0]);
    }

    private URL[] getURLs() {
        return urls;
    }

    ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    private void setResourceBundle(ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
    }

}