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
import de.viadee.bpm.vPAV.processing.CheckName;
import de.viadee.bpm.vPAV.processing.code.flow.BpmnElement;
import de.viadee.bpm.vPAV.processing.code.flow.ControlFlowGraph;
import de.viadee.bpm.vPAV.processing.code.flow.FlowAnalysis;
import de.viadee.bpm.vPAV.processing.model.data.CheckerIssue;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.BusinessRuleTask;
import org.junit.*;
import org.junit.jupiter.api.AfterEach;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

/**
 * Unit Tests for DmnTaskChecker
 *
 */
public class DmnTaskCheckerTest {

	private static final String BASE_PATH = "src/test/resources/";

	private static DmnTaskChecker checker;

	private static ClassLoader cl;

	private final Rule rule = new Rule("DmnTaskChecker", true, null, null, null, null);

	@BeforeClass
	public static void setup() throws MalformedURLException {
		final File file = new File(".");
		final String currentPath = file.toURI().toURL().toString();
		final URL classUrl = new URL(currentPath + "src/test/java");
		final URL[] classUrls = { classUrl };
		cl = new URLClassLoader(classUrls);
		RuntimeConfig.getInstance().setClassLoader(cl);
		RuntimeConfig.getInstance().getResource("en_US");
	}

	/**
	 * Case: DMN task with correct DMN-File
	 *
	 */

	@Test
    public void testCorrectDMN() {
		final String PATH = BASE_PATH + "DmnTaskCheckerTest_CorrectDMN.bpmn";
		checker = new DmnTaskChecker(rule, new BpmnScanner(PATH));

		// parse bpmn model
		final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(new File(PATH));

		final Collection<BusinessRuleTask> baseElements = modelInstance.getModelElementsByType(BusinessRuleTask.class);

		final BpmnElement element = new BpmnElement(PATH, baseElements.iterator().next(), new ControlFlowGraph(),
				new FlowAnalysis());

        checker.check(element);

        if (IssueService.getInstance().getIssues().size() > 0) {
			Assert.fail("correct DMN-File generates an issue");
		}
	}

	/**
	 * Case: DMN task without a reference should produce an error
	 *
	 */

	@Test
    public void testDMNTaskWithoutReference() {
		final String PATH = BASE_PATH + "DmnTaskCheckerTest_WrongDmnTask.bpmn";
		checker = new DmnTaskChecker(rule, new BpmnScanner(PATH));

		// parse bpmn model
		final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(new File(PATH));

		final Collection<BusinessRuleTask> baseElements = modelInstance.getModelElementsByType(BusinessRuleTask.class);

		final BpmnElement element = new BpmnElement(PATH, baseElements.iterator().next(), new ControlFlowGraph(),
				new FlowAnalysis());
		final BaseElement baseElement = element.getBaseElement();

        checker.check(element);

        final Collection<CheckerIssue> issues = IssueService.getInstance().getIssues();

		if (issues.size() != 1) {
			Assert.fail("collection with the issues is bigger or smaller as expected");
		} else {
			Assert.assertEquals("Task '" + CheckName.checkName(baseElement) + "' with no dmn reference.",
					issues.iterator().next().getMessage());
		}
	}

	/**
	 * Case: DMN task with wrong DMN-File
	 *
	 */

	@Test
    public void testDMNTaskWithWrongDMN() {
		final String PATH = BASE_PATH + "DmnTaskCheckerTest_wrongDMNReference.bpmn";
		checker = new DmnTaskChecker(rule, new BpmnScanner(PATH));

		// parse bpmn model
		final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(new File(PATH));

		final Collection<BusinessRuleTask> baseElements = modelInstance.getModelElementsByType(BusinessRuleTask.class);

		final BpmnElement element = new BpmnElement(PATH, baseElements.iterator().next(), new ControlFlowGraph(),
				new FlowAnalysis());
		final BaseElement baseElement = element.getBaseElement();

        checker.check(element);

        final Collection<CheckerIssue> issues = IssueService.getInstance().getIssues();

		if (issues.size() != 1) {
			Assert.fail("collection with the issues is bigger or smaller as expected");
		} else {
			Assert.assertEquals("Dmn file for task '" + CheckName.checkName(baseElement) + "' not found.",
					issues.iterator().next().getMessage());
		}
	}

	/**
	 * Case: read referenced DMN
	 *
	 */

	@Test
    public void testReadReferencedDMNFile() {
		final String PATH = BASE_PATH + "DmnTaskCheckerTest_ReadReferencedDMN.bpmn";
		checker = new DmnTaskChecker(rule, new BpmnScanner(PATH));

		// parse bpmn model
		final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(new File(PATH));

		final Collection<BusinessRuleTask> baseElements = modelInstance.getModelElementsByType(BusinessRuleTask.class);

		final BpmnElement element = new BpmnElement(PATH, baseElements.iterator().next(), new ControlFlowGraph(),
				new FlowAnalysis());

        checker.check(element);

        if (IssueService.getInstance().getIssues().size() > 1) {
			Assert.fail("collection with the issues is bigger or smaller as expected");
		}
	}

	@Before
    public void clearIssues() {
        IssueService.getInstance().clear();
    }
}
