/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.PreorderedValueSequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements an XQuery let-expression.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class LetExpr extends BindingExpression {

	public LetExpr(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BindingExpression#analyze(org.exist.xquery.Expression, int, org.exist.xquery.OrderSpec[])
	 */
	public void analyze(Expression parent, int flags, OrderSpec orderBy[]) throws XPathException {
        // Save the local variable stack
		LocalVariable mark = context.markLocalVariables(false);
		
		inputSequence.analyze(this, flags);
		
		// Declare the iteration variable
        LocalVariable inVar = new LocalVariable(QName.parse(context, varName, null));
        inVar.setSequenceType(sequenceType);
		context.declareVariableBinding(inVar);
		
		if(whereExpr != null) {
		    whereExpr.analyze(this, flags | IN_PREDICATE | IN_WHERE_CLAUSE);
		}
		if(returnExpr instanceof BindingExpression) {
		    ((BindingExpression)returnExpr).analyze(this, flags, orderBy);
		} else {
			if(orderBy != null) {
			    for(int i = 0; i < orderBy.length; i++)
			        orderBy[i].analyze(this, flags);
			}
			returnExpr.analyze(this, flags);
		}
		
		// restore the local variable stack
		context.popLocalVariables(mark);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem, Sequence resultSequence)
		throws XPathException {
		try {
			// Save the local variable stack
			LocalVariable mark = context.markLocalVariables(false);
			
			// evaluate input sequence
			Sequence in = inputSequence.eval(null, null);
			
			// Declare the iteration variable
			LocalVariable var = new LocalVariable(QName.parse(context, varName, null));
			var.setSequenceType(sequenceType);
			context.declareVariableBinding(var);
			clearContext(in);
			var.setValue(in);
			var.checkType();
			
			Sequence filtered = null;
			if (whereExpr != null) {
				filtered = applyWhereExpression(null);
				// TODO: don't use returnsType here
				if (filtered.getItemType() == Type.BOOLEAN) {
					if (!filtered.effectiveBooleanValue())
						return Sequence.EMPTY_SEQUENCE;
				} else if (filtered.getLength() == 0)
					return Sequence.EMPTY_SEQUENCE;
			}
			
			// Check if we can speed up the processing of the "order by" clause.
			boolean fastOrderBy = checkOrderSpecs(in);
			
			//	PreorderedValueSequence applies the order specs to all items
			// in one single processing step
			if(fastOrderBy) {
				in = new PreorderedValueSequence(orderSpecs, in.toNodeSet());
			}
			
			// Otherwise, if there's an order by clause, wrap the result into
			// an OrderedValueSequence. OrderedValueSequence will compute
			// order expressions for every item when it is added to the result sequence.
			if(resultSequence == null) {
				if(orderSpecs != null && !fastOrderBy)
					resultSequence = new OrderedValueSequence(orderSpecs, in.getLength());
				else
					resultSequence = new ValueSequence();
			}
			
			if(returnExpr instanceof BindingExpression) {
				((BindingExpression)returnExpr).eval(null, null, resultSequence);
			} else {
				in = returnExpr.eval(null);
				resultSequence.addAll(in);
			}
			if(orderSpecs != null && !fastOrderBy)
				((OrderedValueSequence)resultSequence).sort();
			
			// Restore the local variable stack
			context.popLocalVariables(mark);
		} catch (XPathException e) {
			// add stack trace information (line numbers)
            if (e.getLine() == 0)
                e.setASTNode(getASTNode());
            throw e;
		}
		return resultSequence;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("let ", getASTNode());
        dumper.startIndent();
        dumper.display("$").display(varName);
        dumper.display(" := ");
        inputSequence.dump(dumper);
        dumper.endIndent();
        if(whereExpr != null) {
            dumper.nl().display("where ");
            whereExpr.dump(dumper);
        }
        if(orderSpecs != null) {
            dumper.nl().display("order by ");
            for(int i = 0; i < orderSpecs.length; i++) {
                if(i > 0)
                    dumper.display(", ");
                dumper.display(orderSpecs[i].toString());
            }
        }
        dumper.nl().display("return ");
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent();
    }
}
