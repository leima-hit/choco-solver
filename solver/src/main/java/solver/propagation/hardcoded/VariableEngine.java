/**
 *  Copyright (c) 1999-2011, Ecole des Mines de Nantes
 *  All rights reserved.
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *      * Neither the name of the Ecole des Mines de Nantes nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package solver.propagation.hardcoded;

import choco.kernel.memory.IEnvironment;
import com.sun.istack.internal.NotNull;
import gnu.trove.list.array.TIntArrayList;
import solver.ICause;
import solver.Solver;
import solver.constraints.Constraint;
import solver.constraints.propagators.Propagator;
import solver.exception.ContradictionException;
import solver.propagation.IPropagationEngine;
import solver.propagation.IPropagationStrategy;
import solver.propagation.queues.CircularQueue;
import solver.recorders.coarse.AbstractCoarseEventRecorder;
import solver.recorders.fine.AbstractFineEventRecorder;
import solver.variables.EventType;
import solver.variables.Variable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This engine is variable-oriented one.
 * <br/>On a call to {@code onVariableUpdate}, it stores the event generated and schedules in a queue the variable for future revision.
 * <br/>A propagator can schedule itself on a call to {@code schedulePropagator}, in this case, the propagator is pushed into
 * second queue for delayed propagation.
 * <br/>On a call to {@code propagate} a variable is removed from the queue and a loop over its active propagator is achieved.
 * <br/>The queue of variables is always emptied before treating one element of the propagator one.
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 05/07/12
 */
public class VariableEngine implements IPropagationEngine {
    protected final ContradictionException exception; // the exception in case of contradiction
    protected final IEnvironment environment; // environment of backtrackable objects
    protected final Variable[] variables;
    protected final Propagator[] propagators;

    protected final CircularQueue<Variable> var_queue;
    protected Variable lastVar;
    protected final CircularQueue<Propagator> pro_queue;
    protected Propagator lastProp;
    protected final boolean[] schedule;
    protected final int[][] masks_f;
    protected final int[] masks_c;


    public VariableEngine(Solver solver) {
        this.exception = new ContradictionException();
        this.environment = solver.getEnvironment();

        variables = solver.getVars();
        int maxID = variables[0].getId();
        TIntArrayList nbProps = new TIntArrayList();
        nbProps.add(variables[0].getPropagators().length);
        for (int i = 1; i < variables.length; i++) {
            if (maxID < variables[i].getId()) {
                maxID = variables[i].getId();
            }
            nbProps.add(variables[i].getPropagators().length);
        }

        Constraint[] constraints = solver.getCstrs();
        List<Propagator> _propagators = new ArrayList();
        for (int c = 0; c < constraints.length; c++) {
            _propagators.addAll(Arrays.asList(constraints[c].propagators));
        }
        propagators = _propagators.toArray(new Propagator[_propagators.size()]);

        var_queue = new CircularQueue<Variable>(variables.length / 2);
        pro_queue = new CircularQueue<Propagator>(propagators.length);

        int size = solver.getNbIdElt();
        schedule = new boolean[size];
        masks_f = new int[maxID + 1][];
        for (int i = 0; i < variables.length; i++) {
            masks_f[i] = new int[nbProps.get(i)];
        }
        masks_c = new int[propagators.length];
    }

    @Override
    public void fails(ICause cause, Variable variable, String message) throws ContradictionException {
        throw exception.set(cause, variable, message);
    }

    @Override
    public ContradictionException getContradictionException() {
        return exception;
    }

    @Override
    public void init(Solver solver) {
        for (int p = 0; p < propagators.length; p++) {
            schedulePropagator(propagators[p], EventType.FULL_PROPAGATION);
        }
    }

    @SuppressWarnings({"NullableProblems"})
    @Override
    public void propagate() throws ContradictionException {
        int id, mask;
        do {
            while (!var_queue.isEmpty()) {
                lastVar = var_queue.pollFirst();
                // revision of the variable
                id = lastVar.getId();
                schedule[id] = false;
                Propagator[] vProps = lastVar.getPropagators();
                int[] pindices = lastVar.getPIndices();
                for (int p = 0; p < vProps.length; p++) {
                    Propagator prop = vProps[p];
                    mask = masks_f[id][p];
                    if (mask > 0) {
                        masks_f[id][p] = 0;
                        prop.fineERcalls++;
                        prop.propagate(null, pindices[p], mask);
                    }
                }
            }
            if (!pro_queue.isEmpty()) {
                lastProp = pro_queue.pollFirst();
                id = lastProp.getId();
                // revision of the propagator
                schedule[id] = false;
                mask = masks_c[id];
                masks_c[id] = 0;
                lastProp.coarseERcalls++;
                lastProp.propagate(mask);
                if (lastProp.isStateLess()) {
                    lastProp.setActive();
                } else {
                    onPropagatorExecution(lastProp);
                }
            }
        } while (!var_queue.isEmpty() || !pro_queue.isEmpty());

    }

    @Override
    public void flush() {
        int id;
        if (lastVar != null) {
            id = lastVar.getId();
            Arrays.fill(masks_f[id], 0);
            schedule[id] = false;
        }
        while (!var_queue.isEmpty()) {
            lastVar = var_queue.pollFirst();
            // revision of the variable
            id = lastVar.getId();
            Arrays.fill(masks_f[id], 0);
            schedule[id] = false;
        }
        while (!pro_queue.isEmpty()) {
            lastProp = pro_queue.pollFirst();
            id = lastProp.getId();
            schedule[id] = false;
            masks_c[id] = 0;
        }
    }

    @Override
    public void onVariableUpdate(Variable variable, EventType type, ICause cause) throws ContradictionException {
        int vid = variable.getId();
        boolean _schedule = false;
        Propagator[] vProps = variable.getPropagators();
        int[] pindices = variable.getPIndices();
        for (int p = 0; p < vProps.length; p++) {
            Propagator prop = vProps[p];
            if (cause != prop && prop.isActive()) {
                if ((type.mask & prop.getPropagationConditions(pindices[p])) != 0) {
                    masks_f[vid][p] |= type.strengthened_mask;
                    _schedule = true;
                }
            }
        }
        if (!schedule[vid] && _schedule) {
            var_queue.addLast(variable);
            schedule[vid] = true;
        }

    }

    @Override
    public void schedulePropagator(@NotNull Propagator propagator, EventType event) {
        int pid = propagator.getId();
        if (!schedule[pid]) {
            pro_queue.addLast(propagator);
            schedule[pid] = true;
            masks_c[pid] |= event.getStrengthenedMask();
        }
    }

    @Override
    public void onPropagatorExecution(Propagator propagator) {
        desactivatePropagator(propagator);
    }

    @Override
    public void activatePropagator(Propagator propagator) {
        // void
    }

    @Override
    public void desactivatePropagator(Propagator propagator) {
        int pid = propagator.getId();
        Variable[] variables = propagator.getVars();
        int[] vindices = propagator.getVIndices();
        for (int i = 0; i < variables.length; i++) {
            masks_f[variables[i].getId()][vindices[i]] = 0;
        }
        if (schedule[pid]) {
            schedule[pid] = false;
            masks_c[pid] = 0;
            pro_queue.remove(propagator);
        }
    }

    @Override
    public void clear() {
        // void
    }

    ////////////// USELESS ///////////////

    @Override
    public boolean initialized() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean forceActivation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPropagationEngine set(IPropagationStrategy propagationStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void prepareWM(Solver solver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearWatermark(int id1, int id2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMarked(int id1, int id2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEventRecorder(AbstractFineEventRecorder fer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEventRecorder(AbstractCoarseEventRecorder er) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void activateFineEventRecorder(AbstractFineEventRecorder fer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void desactivateFineEventRecorder(AbstractFineEventRecorder fer) {
        throw new UnsupportedOperationException();
    }
}
