/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.nativeCallHandler;

import java.util.Collections;
import java.util.Set;

import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.AccessPath.ArrayTaintType;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;

public class DefaultNativeCallHandler extends AbstractNativeCallHandler {
	
	private static final String SIG_ARRAYCOPY =
			"<java.lang.System: void arraycopy(java.lang.Object,int,java.lang.Object,int,int)>";
	private static final String SIG_NEW_ARRAY =
			"<java.lang.reflect.Array: java.lang.Object newArray(java.lang.Class,int)>";
//
//	@Override
//		//check some evaluated methods:
//
//		//arraycopy:
//		//arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
//        //Copies an array from the specified source array, beginning at the specified position,
//		//to the specified position of the destination array.
//						AccessPath ap = manager.getAccessPathFactory().copyWithNewValue(source.getAccessPath(),
//						Abstraction abs = source.deriveNewAbstraction(ap, call);
//						abs.setCorrespondingCallSite(call);
//						return Collections.singleton(abs);
//						AccessPath ap = manager.getAccessPathFactory().copyWithNewValue(source.getAccessPath(),
//						Abstraction abs = source.deriveNewAbstraction(ap, call);
//						abs.setCorrespondingCallSite(call);
//						return Collections.singleton(abs);
//
//		return null;

	@Override
	public void initialize(PatternInfoflowManager manager) {

	}

	@Override
	public NormalState getTaintedValues(Stmt call, NormalState source, Value[] params) {
		return null;
	}

	@Override
	public boolean supportsCall(Stmt call) {
		return call.containsInvokeExpr()
				&& call.getInvokeExpr().getMethod().getSignature().equals(SIG_ARRAYCOPY);
	}
	
}
