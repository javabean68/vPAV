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
package de.viadee.bpm.vPAV.processing;

import de.viadee.bpm.vPAV.SootResolverSimplified;
import de.viadee.bpm.vPAV.ProcessVariablesCreator;
import de.viadee.bpm.vPAV.constants.CamundaMethodServices;
import de.viadee.bpm.vPAV.processing.code.flow.*;
import de.viadee.bpm.vPAV.processing.model.data.CamundaProcessVariableFunctions;
import de.viadee.bpm.vPAV.processing.model.data.ProcessVariableOperation;
import de.viadee.bpm.vPAV.processing.model.data.VariableOperation;
import soot.RefType;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.graph.Block;

import java.util.*;

// TODO rekursionen sind möglich
public class ObjectReader {

    private HashSet<Integer> processedBlocks = new HashSet<>();

    private ObjectVariable thisObject = new ObjectVariable();

    private HashMap<String, StringVariable> localStringVariables = new HashMap<>();

    private HashMap<String, ObjectVariable> localObjectVariables = new HashMap<>();

    private ProcessVariablesCreator processVariablesCreator;

    ObjectReader(HashMap<String, StringVariable> localStrings,
            HashMap<String, ObjectVariable> localObjects, ObjectVariable thisObject,
            ProcessVariablesCreator processVariablesCreator) {
        this.localStringVariables = localStrings;
        this.localObjectVariables = localObjects;
        this.thisObject = thisObject;
        this.processVariablesCreator = processVariablesCreator;
    }

    public ObjectReader(ProcessVariablesCreator processVariablesCreator) {
        this.processVariablesCreator = processVariablesCreator;
    }

    private ObjectReader(ProcessVariablesCreator processVariablesCreator, ObjectVariable thisObject) {
        this.processVariablesCreator = processVariablesCreator;
        this.thisObject = thisObject;
    }

    public Object processBlock(Block block, List<Value> args, List<Object> argValues, String thisName,
            String parentName) {
        // TODO loops etc are currently ignored
        // TODO recursion is possible, prevent infinite loops
        // Todo Only String variables are currently resolved
        // Eventually add objects, arrays and int
        if (processedBlocks.contains(hashBlock(block))) {
            processVariablesCreator.visitBlockAgain(block);
            return null;
        }
        processedBlocks.add(hashBlock(
                block)); // TODO add block here of before return?, what if method / block is called again?

        final Iterator<Unit> unitIt = block.iterator();

        if (thisName == null) {
            // Find out variable name of this reference, it's always defined in the first unit
            thisName = getThisNameFromUnit(unitIt.next());
        }

        Unit unit;
        while (unitIt.hasNext()) {
            unit = unitIt.next();
            // e. g. r2 := @parameter1: org.camunda.bpm.engine.delegate.DelegateExecution
            if (unit instanceof IdentityStmt) {
                handleIdentityStmt(unit, args, argValues, parentName);
            }
            // e. g. $r2 = staticinvoke ... (Assignment)
            else if (unit instanceof AssignStmt) {
                handleAssignStmt(block, unit, thisName);
            }
            // e. g. specialinvoke $r3.<de.viadee.bpm ... (Constuctor call of new object)
            else if (unit instanceof InvokeStmt) {
                InvokeExpr expr = ((InvokeStmt) unit).getInvokeExpr();
                handleInvokeExpr(block, expr, thisName);
            }
            // e. g. return temp$3
            else if (unit instanceof ReturnStmt) {
                return handleReturnStmt(block, unit, thisName);
            }
            // return
            else if (unit instanceof ReturnVoidStmt) {
                return null;
            }
        }
        // Process successors e.g. if or loop
        if (block.getSuccs().size() > 0) {
            processVariablesCreator.startSuccessorHandling(block);
            for (Block succ : block.getSuccs()) {
                this.processBlock(succ, args, argValues, thisName, parentName);
            }
            processVariablesCreator.endSuccessorHandling();
        }

        return null;
    }

    String getThisNameFromUnit(Unit unit) {
        IdentityStmt identityStmt = (IdentityStmt) unit;
        if (identityStmt.getRightOp() instanceof ThisRef) {
            return ((JimpleLocal) identityStmt.getLeftOp()).getName();
        } else {
            return null;
        }
    }

    Object handleReturnStmt(Block block, Unit unit, String thisName) {
        ReturnStmt returnStmt = (ReturnStmt) unit;
        if (returnStmt.getOp().getType().equals(RefType.v("java.lang.String"))) {
            return resolveStringValue(block, returnStmt.getOp(), thisName);
        } else {
            return resolveObjectVariable(block, returnStmt.getOp(), thisName);
        }
    }

    void handleIdentityStmt(Unit unit, List<Value> args, List<Object> argValues, String parentName) {
        IdentityStmt identityStmt = (IdentityStmt) unit;
        // Resolve method parameters
        if (identityStmt.getRightOp() instanceof ParameterRef) {
            int idx = ((ParameterRef) identityStmt.getRightOp()).getIndex();
            if (args.get(idx).getType().equals(RefType.v("java.lang.String"))) {
                StringVariable var = new StringVariable((String) argValues.get(idx));
                localStringVariables.put(((JimpleLocal) identityStmt.getLeftOp()).getName(), var);
            } else if (!args.get(idx).getType().equals(CamundaMethodServices.DELEGATE_EXECUTION_TYPE)) {
                localObjectVariables
                        .put(((JimpleLocal) identityStmt.getLeftOp()).getName(), (ObjectVariable) argValues.get(idx));
            }
        }
    }

    private void handleAssignStmt(Block block, Unit unit, String thisName) {
        AssignStmt assignUnit = (AssignStmt) unit;
        Value leftValue = assignUnit.getLeftOpBox().getValue();
        Value rightValue = assignUnit.getRightOpBox().getValue();

        if (leftValue instanceof JimpleLocal) {
            handleLocalAssignment(block, leftValue, rightValue, thisName);
        } else if (leftValue instanceof JInstanceFieldRef) {
            handleFieldAssignment(block, leftValue, rightValue, thisName);
        } else {
            // TODO when does that happen, does that happen at all?
            assert (false);
        }
    }

    Object handleInvokeExpr(Block block, InvokeExpr expr, String thisName) {
        CamundaProcessVariableFunctions foundMethod = CamundaProcessVariableFunctions
                .findByNameAndNumberOfBoxes(expr.getMethod().getName(),
                        expr.getMethod().getDeclaringClass().getName(), expr.getArgCount());

        if (foundMethod != null) {
            notifyVariablesReader(block, expr, foundMethod);
            return null;
        } else {
            List<Value> args = expr.getArgs();
            List<Object> argValues = resolveArgs(args, thisName);
            ObjectVariable targetObj;

            if (expr instanceof AbstractInstanceInvokeExpr) {
                // Instance method is called
                String targetObjName = ((AbstractInstanceInvokeExpr) expr).getBase().toString();

                // Method on this object is called
                if (targetObjName.equals(thisName)) {
                    return this
                            .processBlock(SootResolverSimplified.getBlockFromMethod(expr.getMethod()), args, argValues,
                                    null,
                                    thisName);
                } else {
                    // Method on another object is called
                    targetObj = localObjectVariables.get(targetObjName);
                }
            } else {
                // Static method is called -> create phantom variable
                targetObj = new ObjectVariable();
            }

            // Process method from another class/object
            ObjectReader or = new ObjectReader(processVariablesCreator, targetObj);
            SootMethod method = expr.getMethod();

            if (method.getDeclaringClass().getPackageName().startsWith("java.")) {
                // Skip native java classes
                return null;
            }
            return or.processBlock(SootResolverSimplified.getBlockFromMethod(method), args, argValues, null, thisName);
        }
    }

    void handleLocalAssignment(Block block, Value leftValue, Value rightValue, String thisName) {
        // TODO int and other basic types are handled as objects
        // Local string variable is updated
        if (leftValue.getType().equals(RefType.v("java.lang.String"))) {
            String newValue = resolveStringValue(block, rightValue, thisName);
            localStringVariables.put(leftValue.toString(), new StringVariable(newValue));
        }
        // Object variable is updated/created
        else {
            ObjectVariable ob = resolveObjectVariable(block, rightValue, thisName);
            if (ob != null) {
                localObjectVariables.put(leftValue.toString(), ob);
            }
        }
    }

    void handleFieldAssignment(Block block, Value leftValue, Value rightValue, String thisName) {
        // TODO int and other basic types are handled as objects
        // String field of object is updated
        if (leftValue.getType().equals(RefType.v("java.lang.String"))) {
            String newValue = resolveStringValue(block, rightValue, thisName);
            String objIdentifier = getObjIdentifierFromFieldRef(leftValue);
            String varName = getVarNameFromFieldRef(leftValue);

            if (objIdentifier.equals(thisName)) {
                // this object is referenced (ignore all other objects at the moment)
                thisObject.updateStringField(varName, newValue);
            }
        }
        // Object field is updated
        else {
            String objIdentifier = getObjIdentifierFromFieldRef(leftValue);
            String varName = getVarNameFromFieldRef(leftValue);
            ObjectVariable objectVar = resolveObjectVariable(block, rightValue, thisName);
            // Only consider this object at the moment
            if (objIdentifier.equals(thisName) && objectVar != null) {
                thisObject.putObjectField(varName, objectVar);
            }
        }
    }

    String resolveStringValue(Block block, Value rightValue, String thisName) {
        if (rightValue instanceof StringConstant) {
            return getValueFromStringConstant(rightValue);
        } else if (rightValue instanceof JimpleLocal) {
            return localStringVariables.containsKey(rightValue.toString()) ?
                    localStringVariables.get(rightValue.toString()).getValue() :
                    null;
        } else if (rightValue instanceof FieldRef) {
            return thisObject.getStringField(getVarNameFromFieldRef(rightValue)).getValue();
        } else if (rightValue instanceof InvokeExpr) {
            return (String) handleInvokeExpr(block, (InvokeExpr) rightValue, thisName);
        } else if (rightValue instanceof CastExpr) {
            return resolveStringValue(block, ((CastExpr) rightValue).getOp(), thisName);
        } else {
            // TODO When does that happen?
            assert (false);
            return null;
        }
    }

    ObjectVariable resolveObjectVariable(Block block, Value rightValue, String thisName) {
        if (rightValue instanceof JimpleLocal) {
            String localVar = rightValue.toString();
            return localObjectVariables.get(localVar);
        } else if (rightValue instanceof FieldRef) {
            // TODO do not implicitly assert that fieldref refers to this object
            return thisObject.getObjectField(getVarNameFromFieldRef(rightValue));
        } else if (rightValue instanceof NewExpr) {
            // New object is instantiated, we add an empty object as constructors are not resolved yet
            return new ObjectVariable();
        } else if (rightValue instanceof InvokeExpr) {
            // TODO not sure what to return
            // TODO add test for this branch
            return (ObjectVariable) handleInvokeExpr(block, (InvokeExpr) rightValue, thisName);
        } else {
            return null;
        }
    }

    private String getObjIdentifierFromFieldRef(Value value) {
        return ((JInstanceFieldRef) value).getBase().toString();
    }

    private String getVarNameFromFieldRef(Value value) {
        int spaceIdx = value.toString().lastIndexOf(" ") + 1;
        if (value instanceof JInstanceFieldRef) {
            // TODO why do i call getSignature?
            ((JInstanceFieldRef) value).getFieldRef().getSignature();
            int gtsIdx = value.toString().lastIndexOf(">");
            return value.toString().substring(spaceIdx, gtsIdx);
        } else if (value instanceof StaticFieldRef) {
            int gtsIdx = value.toString().lastIndexOf(">");
            return value.toString().substring(spaceIdx, gtsIdx);
        } else {
            assert (false);
            return "";
        }

    }

    private String getValueFromStringConstant(Value value) {
        return ((StringConstant) value).value;
    }

    HashMap<String, StringVariable> getLocalStringVariables() {
        return localStringVariables;
    }

    HashMap<String, ObjectVariable> getLocalObjectVariables() {
        return localObjectVariables;
    }

    ObjectVariable getThisObject() {
        return thisObject;
    }

    // TODO call this method when executiondelegate method is executed
    public void notifyVariablesReader(Block block, InvokeExpr expr, CamundaProcessVariableFunctions camundaMethod) {
        int location = camundaMethod.getLocation() - 1;
        VariableOperation type = camundaMethod.getOperationType();
        // TODO variables extractor decides on scope id for variable map
        // TODO do we need the thisName? is that possible?
        String variableName = resolveStringValue(block, expr.getArgBox(location).getValue(), "");
        // TODO add test for scope id (not yet included)

        ProcessVariableOperation pvo = new ProcessVariableOperation(variableName, type,
                processVariablesCreator.getScopeId());
        processVariablesCreator.handleProcessVariableManipulation(block, pvo);
    }

    public static int hashBlock(Block block) {
        return Objects.hash(block.getHead(), block.getTail(), block.getBody(),
                block.getIndexInMethod());
    }

    public List<Object> resolveArgs(List<Value> args, String thisName) {
        ArrayList<Object> list = new ArrayList<>();
        for (Value arg : args) {
            if (arg.getType().equals(RefType.v("java.lang.String"))) {
                list.add(resolveStringValue(null, arg, thisName));
            } else if (arg.getType().equals(CamundaMethodServices.DELEGATE_EXECUTION_TYPE)) {
                list.add(null);
            } else {
                list.add(resolveObjectVariable(null, arg, thisName));
            }
        }
        return list;

    }
}