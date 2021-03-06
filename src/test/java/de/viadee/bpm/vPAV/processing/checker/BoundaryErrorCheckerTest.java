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
package de.viadee.bpm.vPAV.processing.checker;

import de.viadee.bpm.vPAV.BpmnScanner;
import de.viadee.bpm.vPAV.IssueService;
import de.viadee.bpm.vPAV.RuntimeConfig;
import de.viadee.bpm.vPAV.config.model.Rule;
import de.viadee.bpm.vPAV.processing.code.flow.BpmnElement;
import de.viadee.bpm.vPAV.processing.code.flow.ControlFlowGraph;
import de.viadee.bpm.vPAV.processing.code.flow.FlowAnalysis;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.ServiceTaskImpl;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.junit.*;
import org.junit.jupiter.api.AfterEach;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Used model: single task (+ boundary event)
 */
public class BoundaryErrorCheckerTest {

    private static final String BASE_PATH = "src/test/resources/";

    private static final String PATH = BASE_PATH + "Model_With_Boundary_Event.bpmn";

    private static final String DELEGATE_PACKAGE = "de.viadee.bpm.vPAV.delegates.BoundaryError.";

    private static BoundaryErrorChecker checker;

    private static ClassLoader cl;

    private final Rule rule = new Rule("BoundaryErrorChecker", true, null, null, null, null);

    @BeforeClass
    public static void setup() throws MalformedURLException {

        final File file = new File(".");
        final String currentPath = file.toURI().toURL().toString();
        final URL classUrl = new URL(currentPath + "src/test/java");
        final URL[] classUrls = { classUrl };
        cl = new URLClassLoader(classUrls);
        RuntimeConfig.getInstance().setClassLoader(cl);
        RuntimeConfig.getInstance().setTest(true);
        RuntimeConfig.getInstance().getResource("en_US");

        // Bean-Mapping
        final Map<String, String> beanMapping = new HashMap<>();
        beanMapping.put("correctBoundaryErrorEvent", DELEGATE_PACKAGE + "BoundaryErrorEventDelegateCorrect");
        beanMapping.put("wrongBoundaryErrorEvent", DELEGATE_PACKAGE + "BoundaryErrorEventDelegateWrong");
        RuntimeConfig.getInstance().setBeanMapping(beanMapping);
    }

    private void setupAndRunChecker(String delegate, boolean delegateExpression) {
        BpmnScanner scanner = new BpmnScanner(PATH);
        checker = new BoundaryErrorChecker(rule, scanner);

        // parse bpmn model
        final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(new File(PATH));

        // Set delegate
        ServiceTaskImpl task = modelInstance.getModelElementById("Task_0759yva");
        if (delegateExpression) {
            // Use delegate expression
            task.setAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "delegateExpression", delegate);
        } else {
            // Use class
            task.setCamundaClass(DELEGATE_PACKAGE + delegate);

        }
        scanner.setModelInstance(modelInstance);

        // Run checker
        final Collection<BaseElement> baseElements = modelInstance.getModelElementsByType(BaseElement.class);

        for (BaseElement baseElement : baseElements) {
            final BpmnElement element = new BpmnElement(PATH, baseElement, new ControlFlowGraph(), new FlowAnalysis());
            checker.check(element);
        }
    }

    /**
     * Case: Correct BoundaryErrorEvent with corresponding ErrorCodes
     * Model - Task Implementation: Java Class Delegate (BoundaryErrorEventDelegateCorrect)
     */
    @Test
    public void testBoundaryErrorEventClass_Correct() {
        setupAndRunChecker("BoundaryErrorEventDelegateCorrect", false);

        if (IssueService.getInstance().getIssues().size() > 0) {
            Assert.fail("correct model generates an issue");
        }
    }

    /**
     * Case: Correct BoundaryErrorEvent with corresponding ErrorCodes
     * Model - Task Implementation: Java Class Delegate (BoundaryErrorEventDelegateCorrectWithVariable)
     */
    @Test
    public void testBoundaryErrorEventClassWithVariables_Correct() {
        setupAndRunChecker("BoundaryErrorEventDelegateCorrectWithVariable", false);

        if (IssueService.getInstance().getIssues().size() > 0) {
            Assert.fail("correct model generates an issue");
        }
    }

    /**
     * Case: Correct BoundaryErrorEvent with not corresponding ErrorCodes
     * Model - Task Implementation: Java Class Delegate (BoundaryErrorEventDelegateWrong)
     */
    @Test
    public void testBoundaryErrorEventClass_Wrong() {
        setupAndRunChecker("BoundaryErrorEventDelegateWrong", false);

        if (IssueService.getInstance().getIssues().size() != 1) {
            Assert.fail("Incorrect model should generate an issue");
        }
    }

    /**
     * Case: Correct BoundaryErrorEvent with corresponding ErrorCodes, usage of bean
     * Model - Task Implementation: Delegate Expression with Bean
     */
    @Test
    public void testBoundaryErrorEventBean_Correct() {
        setupAndRunChecker("${correctBoundaryErrorEvent}", true);

        if (IssueService.getInstance().getIssues().size() > 0) {
            Assert.fail("correct model generates an issue");
        }
    }

    /**
     * Case: Correct BoundaryErrorEvent with not corresponding ErrorCodes, usage of
     * bean
     * Model - Task Implementation: Delegate Expression with Bean
     */
    @Test
    public void testBoundaryErrorEventBean_Wrong() {
        setupAndRunChecker("${wrongBoundaryErrorEvent}", true);

        if (IssueService.getInstance().getIssues().size() != 1) {
            Assert.fail("Incorrect model should generate an issue");
        }
    }

    @Before
    public void clearIssues() {
        IssueService.getInstance().clear();
    }
}
