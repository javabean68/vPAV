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
import de.viadee.bpm.vPAV.processing.code.flow.*;
import de.viadee.bpm.vPAV.processing.model.data.CamundaProcessVariableFunctions;
import de.viadee.bpm.vPAV.processing.model.data.ProcessVariableOperation;
import de.viadee.bpm.vPAV.processing.model.data.VariableOperation;
import soot.RefType;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.graph.Block;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

// TODO rekursionen sind möglich
public class ObjectReader {

    private ObjectVariable thisObject = new ObjectVariable();

    private HashMap<String, StringVariable> localStringVariables = new HashMap<>();

    private HashMap<String, ObjectVariable> localObjectVariables = new HashMap<>();

    private ProcessVariablesCreator processVariablesCreator;

    ObjectReader(HashMap<String, StringVariable> localStrings,
            HashMap<String, ObjectVariable> localObjects, ObjectVariable thisObject) {
        this.localStringVariables = localStrings;
        this.localObjectVariables = localObjects;
        this.thisObject = thisObject;
    }

    public ObjectReader(ProcessVariablesCreator processVariablesCreator) {
        this.processVariablesCreator = processVariablesCreator;
    }

    private ObjectReader(ProcessVariablesCreator processVariablesCreator, ObjectVariable thisObject) {
        this.processVariablesCreator = processVariablesCreator;
        this.thisObject = thisObject;
    }

    public Object processBlock(Block block, List<Value> args) {
        // TODO loops etc are currently ignored
        // TODO recursion is possible, prevent infinite loops
        // Todo Only String variables are currently resolved
        // Eventually add objects, arrays and int

        final Iterator<Unit> unitIt = block.iterator();
        // Find out variable name of this reference, it's always defined in the first unit
        String thisName = getThisNameFromUnit(unitIt.next());

        Unit unit;
        while (unitIt.hasNext()) {
            unit = unitIt.next();
            // e. g. r2 := @parameter1: org.camunda.bpm.engine.delegate.DelegateExecution
            if (unit instanceof IdentityStmt) {
                handleIdentityStmt(unit, args);
            }
            // e. g. $r2 = staticinvoke ... (Assignment)
            else if (unit instanceof AssignStmt) {
                handleAssignStmt(unit, thisName);
            }
            // e. g. specialinvoke $r3.<de.viadee.bpm ... (Constuctor call of new object)
            else if (unit instanceof InvokeStmt) {
                if (((InvokeStmt) unit).getInvokeExpr().getMethod().getDeclaringClass().getName()
                        .equals("org.camunda.bpm.engine.delegate.DelegateExecution")) {
                    notifyVariablesReader(block, ((InvokeStmt) unit).getInvokeExpr());
                    // TODO there are a lot more classes than only delegate execution (check camunda
                    //  CamundaProcessVariableFunctions)
                } else {
                    handleInvokeExpr(((InvokeStmt) unit).getInvokeExpr(), thisName);
                }
            }
            // e. g. return temp$3
            else if (unit instanceof ReturnStmt) {
                return handleReturnStmt(unit, thisName);
            }
            // return
            else if (unit instanceof ReturnVoidStmt) {
                return null;
            }
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

    Object handleReturnStmt(Unit unit, String thisName) {
        ReturnStmt returnStmt = (ReturnStmt) unit;
        if (returnStmt.getOp().getType().equals(RefType.v("java.lang.String"))) {
            return resolveStringValue(returnStmt.getOp(), thisName);
        } else {
            return resolveObjectVariable(returnStmt.getOp());
        }
    }

    void handleIdentityStmt(Unit unit, List<Value> args) {
        IdentityStmt identityStmt = (IdentityStmt) unit;
        // Resolve method parameters
        if (identityStmt.getRightOp() instanceof ParameterRef) {
            int idx = ((ParameterRef) identityStmt.getRightOp()).getIndex();
            if (args.get(idx).getType().toString().equals("java.lang.String")) {
                StringVariable var = new StringVariable(((StringConstant) args.get(idx)).value);
                localStringVariables.put(((JimpleLocal) identityStmt.getLeftOp()).getName(), var);
            }
        }
    }

    private void handleAssignStmt(Unit unit, String thisName) {
        AssignStmt assignUnit = (AssignStmt) unit;
        Value leftValue = assignUnit.getLeftOpBox().getValue();
        Value rightValue = assignUnit.getRightOpBox().getValue();

        if (leftValue instanceof JimpleLocal) {
            handleLocalAssignment(leftValue, rightValue, thisName);
        } else if (leftValue instanceof JInstanceFieldRef) {
            handleFieldAssignment(leftValue, rightValue, thisName);
        } else {
            // TODO when does that happen, does that happen at all?
            assert (false);
        }
    }

    Object handleInvokeExpr(InvokeExpr expr, String thisName) {
        List<Value> args = expr.getArgs();
        ObjectVariable targetObj;

        if (expr instanceof AbstractInstanceInvokeExpr) {
            // Instance method is called
            String targetObjName = ((AbstractInstanceInvokeExpr) expr).getBase().toString();

            // Method on this object is called
            if (targetObjName.equals(thisName)) {
                return this.processBlock(SootResolverSimplified.getBlockFromMethod(expr.getMethod()), args);
            }
            else {
                // Method on another object is called
                targetObj = localObjectVariables.get(targetObjName);
            }
        } else{
            // Static method is called -> create phantom variable
            targetObj = new ObjectVariable();
        }

        // Process method from another class/object
        ObjectReader or = new ObjectReader(processVariablesCreator, targetObj);
        return or.processBlock(SootResolverSimplified.getBlockFromMethod(expr.getMethod()), args);
    }

    void handleLocalAssignment(Value leftValue, Value rightValue, String thisName) {
        // TODO int and other basic types are handled as objects
        // Local string variable is updated
        if (leftValue.getType().equals(RefType.v("java.lang.String"))) {
            String newValue = resolveStringValue(rightValue, thisName);
            localStringVariables.put(leftValue.toString(), new StringVariable(newValue));
        }
        // Object variable is updated/created
        else {
            ObjectVariable ob = resolveObjectVariable(rightValue);
            if (ob != null) {
                localObjectVariables.put(leftValue.toString(), resolveObjectVariable(rightValue));
            }
        }
    }

    void handleFieldAssignment(Value leftValue, Value rightValue, String thisName) {
        // TODO int and other basic types are handled as objects
        // String field of object is updated
        if (leftValue.getType().equals(RefType.v("java.lang.String"))) {
            String newValue = resolveStringValue(rightValue, thisName);
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
            ObjectVariable objectVar = resolveObjectVariable(rightValue);
            // Only consider this object at the moment
            if (objIdentifier.equals(thisName) && objectVar != null) {
                thisObject.putObjectField(varName, objectVar);
            }
        }
    }

    String resolveStringValue(Value rightValue, String thisName) {
        if (rightValue instanceof StringConstant) {
            return getValueFromStringConstant(rightValue);
        } else if (rightValue instanceof JimpleLocal) {
            return localStringVariables.get(rightValue.toString()).getValue();
        } else if (rightValue instanceof FieldRef) {
            return thisObject.getStringField(getVarNameFromFieldRef(rightValue)).getValue();
        } else if (rightValue instanceof InvokeExpr) {
            return (String) handleInvokeExpr((InvokeExpr) rightValue, thisName);
        } else {
            // TODO When does that happen?
            assert (false);
            return null;
        }
    }

    ObjectVariable resolveObjectVariable(Value rightValue) {
        if (rightValue instanceof JimpleLocal) {
            String localVar = rightValue.toString();
            return localObjectVariables.get(localVar);
        } else if (rightValue instanceof FieldRef) {
            // TODO do not implicitly assert that fieldref refers to this object
            return thisObject.getObjectField(getVarNameFromFieldRef(rightValue));
        } else if (rightValue instanceof NewExpr) {
            // New object is instantiated, we add an empty object as constructors are not resolved yet
            return new ObjectVariable();
        } else {
            return null;
        }
    }

    private String getObjIdentifierFromFieldRef(Value value) {
        return ((JInstanceFieldRef) value).getBase().toString();
    }

    private String getVarNameFromFieldRef(Value value) {
        int spaceIdx = value.toString().lastIndexOf(" ") + 1;
        ((JInstanceFieldRef) value).getFieldRef().getSignature();
        int gtsIdx = value.toString().lastIndexOf(">");
        return value.toString().substring(spaceIdx, gtsIdx);
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

    ProcessVariableOperation createProcessVariableOperationFromInvocation(InvokeExpr expr) {
        String methodName = expr.getMethod().getName();
        int numberOfArg = expr.getArgCount();
        String delegatingClass = expr.getMethod().getDeclaringClass().getName();
        CamundaProcessVariableFunctions foundMethod = CamundaProcessVariableFunctions
                .findByNameAndNumberOfBoxes(methodName, delegatingClass, numberOfArg);

        if (foundMethod != null) {
            int location = foundMethod.getLocation() - 1;
            VariableOperation type = foundMethod.getOperationType();
            // TODO variables extractor decides on scope id for variable map
            // TODO do we need the thisName? is that possible?
            String variableName = resolveStringValue(expr.getArgBox(location).getValue(), "");
            return new ProcessVariableOperation(variableName, type, null);
        }

        return null;
    }

    // TODO call this method when executiondelegate method is executed
    public void notifyVariablesReader(Block block, InvokeExpr expr) {
        ProcessVariableOperation pvo = createProcessVariableOperationFromInvocation(expr);
        processVariablesCreator.handleProcessVariableManipulation(block, pvo);
    }
}