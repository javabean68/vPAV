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
package de.viadee.bpm.vPAV.processing.code.flow;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.viadee.bpm.vPAV.processing.model.data.ElementChapter;
import soot.toolkits.graph.Block;

public class Node extends AbstractNode implements Cloneable {

	private Block block;

	public Node(final BpmnElement parentElement, final Block block,
			final ElementChapter elementChapter) {
		super(parentElement, elementChapter);
		this.block = block;
	}

	/**
	 * Set the predecessor nodes of the current node
	 */
	@Override
	public void setPreds() {
		final Pattern blockPattern = Pattern.compile("(Block\\s#)(\\d)");
		final Pattern idPattern = Pattern
				.compile(this.getParentElement().getBaseElement().getId() + "__(\\d\\.)*(\\d)");

		for (Block block : this.block.getPreds()) {
			Matcher blockMatcher = blockPattern.matcher(block.toShortString());
			createIds(idPattern, blockMatcher, true);
		}
	}

	/**
	 * Set the successor nodes of the current node
	 */
	@Override
	public void setSuccs() {
		final Pattern blockPattern = Pattern.compile("(Block\\s#)(\\d)");
		final Pattern idPattern = Pattern
				.compile(this.getParentElement().getBaseElement().getId() + "__(\\d\\.)*(\\d)");

		for (Block block : this.block.getSuccs()) {
			Matcher blockMatcher = blockPattern.matcher(block.toShortString());
			createIds(idPattern, blockMatcher, false);
		}
	}

	public Block getBlock() {
		return block;
	}


	public Object clone() throws
			CloneNotSupportedException
	{
		Node myClone = (Node)super.clone();
		myClone.block = block;
		myClone.controlFlowGraph = controlFlowGraph;
		myClone.parentElement = parentElement;
		myClone.elementChapter = elementChapter;
		myClone.operations = new LinkedHashMap<>();
		myClone.defined = new LinkedHashMap<>();
		myClone.used = new LinkedHashMap<>();
		myClone.killed = new LinkedHashMap<>();
		myClone.outUnused = new LinkedHashMap<>();
		myClone.outUsed = new LinkedHashMap<>();
		myClone.inUnused = new LinkedHashMap<>();
		myClone.inUsed = new LinkedHashMap<>();
		myClone.predecessors = new LinkedHashMap<>();
		myClone.successors = new LinkedHashMap<>();

		return myClone;
	}
}
