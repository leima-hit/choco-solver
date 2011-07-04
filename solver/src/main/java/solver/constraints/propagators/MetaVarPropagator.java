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
package solver.constraints.propagators;

import solver.Solver;
import solver.constraints.Constraint;
import solver.exception.ContradictionException;
import solver.requests.IRequest;
import solver.variables.EventType;
import solver.variables.MetaVariable;
import solver.variables.Variable;
import choco.kernel.ESat;

/**When a variable of vars is modified then the metavariable (to which it should belong) is notified
 * @author Jean-Guillaume Fages
 *
 */
public class MetaVarPropagator extends Propagator {
	
	MetaVariable meta;

	public MetaVarPropagator(Variable[] vars, MetaVariable meta, Solver solver, Constraint constraint) {
		super(vars, solver, constraint, PropagatorPriority.UNARY, true);
		this.meta = meta;
	}

	@Override
	public int getPropagationConditions(int vIdx) {
		return EventType.ALL_MASK();
	}

	@Override
	public void propagate() throws ContradictionException {}

	@Override
	public void propagateOnRequest(IRequest request, int idxVarInProp, int mask) throws ContradictionException {
		meta.notifyPropagators(EventType.META, this);
	}

	@Override
	public ESat isEntailed() {
		for(int i=0;i<vars.length; i++){
			if(!vars[i].instantiated()){
				return ESat.UNDEFINED;
			}
		}
		return ESat.TRUE;
	}
}