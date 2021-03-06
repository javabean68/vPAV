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
package de.viadee.bpm.vPAV.processing.model.graph;

import de.viadee.bpm.vPAV.BpmnScanner;
import de.viadee.bpm.vPAV.FileScanner;
import de.viadee.bpm.vPAV.RuntimeConfig;
import de.viadee.bpm.vPAV.config.model.RuleSet;
import de.viadee.bpm.vPAV.processing.ElementGraphBuilder;
import de.viadee.bpm.vPAV.processing.ProcessVariablesScanner;
import de.viadee.bpm.vPAV.processing.code.flow.FlowAnalysis;
import de.viadee.bpm.vPAV.processing.model.data.Anomaly;
import de.viadee.bpm.vPAV.processing.model.data.AnomalyContainer;
import de.viadee.bpm.vPAV.processing.model.data.ProcessVariableOperation;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Unit Tests for data flow graph creation and calculation of invalid paths
 */
public class GraphCreationTest {

    private static final String BASE_PATH = "src/test/resources/";

    private static ClassLoader cl;

    @BeforeClass
    public static void setup() throws MalformedURLException {
        RuntimeConfig.getInstance().setTest(true);
        final File file = new File(".");
        final String currentPath = file.toURI().toURL().toString();
        final URL classUrl = new URL(currentPath + "src/test/java");
        final URL[] classUrls = { classUrl };
        cl = new URLClassLoader(classUrls);
        RuntimeConfig.getInstance().setClassLoader(cl);
    }

    @AfterClass
    public static void tearDown() {
        RuntimeConfig.getInstance().setTest(false);
    }

    @Test
    public void testBlockSplitOrder() {
        final Map<String, String> beanMapping = new HashMap<>();
        beanMapping.put("blockSplitDelegate", "de/viadee/bpm/vPAV/delegates/BlockSplitDelegate.class");
        RuntimeConfig.getInstance().setBeanMapping(beanMapping);

        final ProcessVariablesScanner scanner = new ProcessVariablesScanner(null);
        final FileScanner fileScanner = new FileScanner(new RuleSet());
        final String PATH = BASE_PATH + "ProcessVariablesReader_BlockSplit.bpmn";
        final File processDefinition = new File(PATH);

        // parse bpmn model
        final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(processDefinition);

        final ElementGraphBuilder graphBuilder = new ElementGraphBuilder(new BpmnScanner(PATH));
        // create data flow graphs

        FlowAnalysis flowAnalysis = new FlowAnalysis();
        final Collection<Graph> graphCollection = graphBuilder.createProcessGraph(fileScanner, modelInstance,
                processDefinition.getPath(), new ArrayList<>(), scanner, flowAnalysis);

        flowAnalysis.analyze(graphCollection);

        // calculate invalid paths based on data flow graphs
        final Map<AnomalyContainer, List<Path>> invalidPathMap = graphBuilder.createInvalidPaths(graphCollection);

        // no anomaly expected
        Assert.assertEquals(0, invalidPathMap.size());
    }

    @Test
    public void testMethodInvocationOrder() {
        final Map<String, String> beanMapping = new HashMap<>();
        beanMapping.put("methodDelegate", "de/viadee/bpm/vPAV/delegates/MethodInvocationDelegate.class");
        RuntimeConfig.getInstance().setBeanMapping(beanMapping);

        final ProcessVariablesScanner scanner = new ProcessVariablesScanner(null);
        final FileScanner fileScanner = new FileScanner(new RuleSet());
        final String PATH = BASE_PATH + "ProcessVariablesReader_MethodInvocation.bpmn";
        final File processDefinition = new File(PATH);

        // parse bpmn model
        final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(processDefinition);

        final ElementGraphBuilder graphBuilder = new ElementGraphBuilder(new BpmnScanner(PATH));
        // create data flow graphs

        FlowAnalysis flowAnalysis = new FlowAnalysis();
        final Collection<Graph> graphCollection = graphBuilder.createProcessGraph(fileScanner, modelInstance,
                processDefinition.getPath(), new ArrayList<>(), scanner, flowAnalysis);

        flowAnalysis.analyze(graphCollection);

        // calculate invalid paths based on data flow graphs
        final Map<AnomalyContainer, List<Path>> invalidPathMap = graphBuilder.createInvalidPaths(graphCollection);

        // DU + DR anomaly
        Assert.assertEquals(2, invalidPathMap.size());
    }

    /**
     * Case: Data flow graph creation and calculation of invalid paths
     */
    @Test
    public void testGraph() {
        final ProcessVariablesScanner scanner = new ProcessVariablesScanner(null);
        final FileScanner fileScanner = new FileScanner(new RuleSet());
        final String PATH = BASE_PATH + "ProcessVariablesModelCheckerTest_GraphCreation.bpmn";
        final File processDefinition = new File(PATH);
        // parse bpmn model
        final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(processDefinition);
        final ElementGraphBuilder graphBuilder = new ElementGraphBuilder(new BpmnScanner(PATH));
        // create data flow graphs
        FlowAnalysis flowAnalysis = new FlowAnalysis();
        final Collection<Graph> graphCollection = graphBuilder.createProcessGraph(fileScanner, modelInstance,
                processDefinition.getPath(), new ArrayList<>(), scanner, flowAnalysis);
        flowAnalysis.analyze(graphCollection);
        // calculate invalid paths based on data flow graphs
        final Map<AnomalyContainer, List<Path>> invalidPathMap = graphBuilder.createInvalidPaths(graphCollection);
        Iterator<Map.Entry<AnomalyContainer, List<Path>>> iterator = invalidPathMap.entrySet().iterator();
        // get invalid paths
        Map.Entry<AnomalyContainer, List<Path>> entry1 = iterator.next();
        AnomalyContainer anomalyContainer1 = entry1.getKey();
        final List<Path> geloeschteVarTest = entry1.getValue();
        ProcessVariableOperation geloeschteVarOperation = Mockito.mock(ProcessVariableOperation.class);
        Mockito.when(geloeschteVarOperation.getIndex()).thenReturn(4);
        Assert.assertEquals("AnomalyContainer geloeschteVariable does not equal actual container",
                new AnomalyContainer("geloeschteVariable", Anomaly.DU, "SequenceFlow_0bi6kaa__0",
                        "SequenceFlow_0bi6kaa", "", geloeschteVarOperation), anomalyContainer1);
        Assert.assertEquals(
                "[[SequenceFlow_09j6ilt, ExclusiveGateway_0su45e1, SequenceFlow_1mggduw, Task_11t5rso, BoundaryEvent_11udorz, SequenceFlow_0bi6kaa]]",
                geloeschteVarTest.toString());
        Map.Entry<AnomalyContainer, List<Path>> entry2 = iterator.next();
        AnomalyContainer anomalyContainer2 = entry2.getKey();
        final List<Path> jepppaTest = entry2.getValue();
        ProcessVariableOperation jepppaOperation = Mockito.mock(ProcessVariableOperation.class);
        Mockito.when(jepppaOperation.getIndex()).thenReturn(11);
        Assert.assertEquals("AnomalyContainer jepppa does not equal actual container",
                new AnomalyContainer("jepppa", Anomaly.DD,
                        "SequenceFlow_0btqo3y__0",
                        "SequenceFlow_0btqo3y",
                        null,
                        jepppaOperation), anomalyContainer2);
        Assert.assertEquals(
                "[[SequenceFlow_1aapyv6, ServiceTask_108g52x, SequenceFlow_0yhv5j2, ServiceTask_05g4a96, SequenceFlow_09j6ilt, ExclusiveGateway_0su45e1, SequenceFlow_0t7iwpj, Task_0546a8y, SequenceFlow_1m6lt2o, ExclusiveGateway_0fsjxd1, SequenceFlow_0btqo3y], [SequenceFlow_1aapyv6, ServiceTask_108g52x, SequenceFlow_0yhv5j2, ServiceTask_05g4a96, SequenceFlow_09j6ilt, ExclusiveGateway_0su45e1, SequenceFlow_1mggduw, Task_11t5rso, SequenceFlow_06ehu4z, ExclusiveGateway_0fsjxd1, SequenceFlow_0btqo3y]]",
                jepppaTest.toString());
        Map.Entry<AnomalyContainer, List<Path>> entry3 = iterator.next();
        AnomalyContainer anomalyContainer3 = entry3.getKey();
        final List<Path> testHallo2 = entry3.getValue();
        ProcessVariableOperation hallo2Operation = Mockito.mock(ProcessVariableOperation.class);
        Mockito.when(hallo2Operation.getIndex()).thenReturn(2);
        Assert.assertEquals("AnomalyContainer hallo2 does not equal actual container",
                new AnomalyContainer("hallo2", Anomaly.UR,
                        "BusinessRuleTask_119jb6t__0",
                        "BusinessRuleTask_119jb6t", "", hallo2Operation), anomalyContainer3);
        Assert.assertEquals("[[BusinessRuleTask_119jb6t]]", testHallo2.toString());
        Map.Entry<AnomalyContainer, List<Path>> entry4 = iterator.next();
        AnomalyContainer anomalyContainer4 = entry4.getKey();
        final List<Path> validVarTest = entry4.getValue();
        ProcessVariableOperation intHalloOperation = Mockito.mock(ProcessVariableOperation.class);
        Assert.assertEquals("AnomalyContainer validVar does not equal actual container",
                new AnomalyContainer("intHallo", Anomaly.UR, "ServiceTask_05g4a96", "Service Task2", intHalloOperation), anomalyContainer4);
        Assert.assertEquals("[[ServiceTask_05g4a96]]", validVarTest.toString());
    }
}
