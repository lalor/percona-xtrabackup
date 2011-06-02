/*
   Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; version 2 of the License.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301  USA
*/

package com.mysql.clusterj.core.query;

import java.util.List;

import com.mysql.clusterj.ClusterJException;
import com.mysql.clusterj.ClusterJUserException;
import com.mysql.clusterj.core.store.IndexScanOperation;
import com.mysql.clusterj.core.store.ScanFilter;
import com.mysql.clusterj.core.store.ScanOperation;
import com.mysql.clusterj.core.store.IndexScanOperation.BoundType;
import com.mysql.clusterj.core.store.ScanFilter.BinaryCondition;
import com.mysql.clusterj.core.store.ScanFilter.Group;

public class InPredicateImpl extends PredicateImpl {

    /** The property */
    protected PropertyImpl property;

    /** The parameter containing the values */
    protected ParameterImpl parameter;

    public InPredicateImpl(QueryDomainTypeImpl<?> dobj,
            PropertyImpl property, ParameterImpl parameter) {
        super(dobj);
        this.property = property;
        this.parameter = parameter;
    }

    @Override
    public void markParameters() {
        parameter.mark();
    }

    @Override
    public void unmarkParameters() {
        parameter.unmark();
    }

    void markBoundsForCandidateIndices(QueryExecutionContextImpl context,
            CandidateIndexImpl[] candidateIndices) {
        if (parameter.getParameterValue(context) == null) {
            // null parameters cannot be used with index scans
            return;
        }
        property.markInBound(candidateIndices, this);
    }

    /** Set bound for the multi-valued parameter identified by the index.
     * 
     * @param context the query execution context
     * @param op the operation to set bounds on
     * @param index the index into the parameter list
     * @param lastColumn if true, can set strict bound
     */
    public void operationSetBound(
            QueryExecutionContextImpl context, IndexScanOperation op, int index, boolean lastColumn) {
        if (lastColumn) {
            // last column can be strict
            operationSetBound(context, op, index, BoundType.BoundEQ);
        } else {
            // not last column cannot be strict
            operationSetBound(context, op, index, BoundType.BoundLE);
            operationSetBound(context, op, index, BoundType.BoundGE);
        }
    }

    public void operationSetUpperBound(QueryExecutionContextImpl context, IndexScanOperation op, int index) {
        operationSetBound(context, op, index, BoundType.BoundGE);
    }

    public void operationSetLowerBound(QueryExecutionContextImpl context, IndexScanOperation op, int index) {
        operationSetBound(context, op, index, BoundType.BoundLE);
    }

    private void operationSetBound(
            QueryExecutionContextImpl context, IndexScanOperation op, int index, BoundType boundType) {
    Object parameterValue = parameter.getParameterValue(context);
        if (parameterValue == null) {
            throw new ClusterJUserException(
                    local.message("ERR_Parameter_Cannot_Be_Null", "operator in", parameter.parameterName));
        } else if (parameterValue instanceof List<?>) {
            List<?> parameterList = (List<?>)parameterValue;
            Object value = parameterList.get(index);
            property.operationSetBounds(value, boundType, op);
            if (logger.isDetailEnabled()) logger.detail("InPredicateImpl.operationSetBound for " + property.fmd.getName() + " List index: " + index + " value: " + value + " boundType: " + boundType);
        } else if (parameterValue.getClass().isArray()) {
            Object[] parameterArray = (Object[])parameterValue;
            Object value = parameterArray[index];
            property.operationSetBounds(value, boundType, op);
            if (logger.isDetailEnabled()) logger.detail("InPredicateImpl.operationSetBound for " + property.fmd.getName() + "  array index: " + index + " value: " + value + " boundType: " + boundType);
        } else {
            throw new ClusterJUserException(
                    local.message("ERR_Parameter_Wrong_Type", parameter.parameterName,
                            parameterValue.getClass().getName(), "List<?> or Object[]"));
        }
    }

    /** Set bounds for the multi-valued parameter identified by the index.
     * There is only one column in the bound, so set each bound and then end the bound.
     * 
     * @param context the query execution context
     * @param op the operation to set bounds on
     * @param index the index into the parameter list
     */
    public void operationSetAllBounds(QueryExecutionContextImpl context,
            IndexScanOperation op) {
        Object parameterValue = parameter.getParameterValue(context);
        int index = 0;
        if (parameterValue == null) {
            throw new ClusterJUserException(
                    local.message("ERR_Parameter_Cannot_Be_Null", "operator in", parameter.parameterName));
        } else if (parameterValue instanceof List<?>) {
            List<?> parameterList = (List<?>)parameterValue;
            for (Object value: parameterList) {
                property.operationSetBounds(value, BoundType.BoundEQ, op);
                if (logger.isDetailEnabled()) logger.detail("InPredicateImpl.operationSetAllBounds for List index: " + index + " value: " + value);
                op.endBound(index++);
            }
        } else if (parameterValue.getClass().isArray()) {
            Object[] parameterArray = (Object[])parameterValue;
            for (Object value: parameterArray) {
                property.operationSetBounds(value, BoundType.BoundEQ, op);
                if (logger.isDetailEnabled()) logger.detail("InPredicateImpl.operationSetAllBounds for array index: " + index + " value: " + value);
                op.endBound(index++);
            }
        } else {
            throw new ClusterJUserException(
                    local.message("ERR_Parameter_Wrong_Type", parameter.parameterName,
                            parameterValue.getClass().getName(), "List<?> or Object[]"));
        }
    }

    /** Create a filter for the operation. Call the property to set the filter
     * from the parameter values.
     * @param context the query execution context with the parameter values
     * @param op the operation
     */
    public void filterCmpValue(QueryExecutionContextImpl context,
            ScanOperation op) {
        try {
            ScanFilter filter = op.getScanFilter(context);
            filterCmpValue(context, op, filter);
        } catch (ClusterJException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ClusterJException(
                    local.message("ERR_Get_NdbFilter"), ex);
        }
    }

    /** Use an existing filter for the operation. Call the property to set the filter
     * from the parameter values.
     * @param context the query execution context with the parameter values
     * @param op the operation
     * @param filter the existing filter
     */
    public void filterCmpValue(QueryExecutionContextImpl context,
            ScanOperation op, ScanFilter filter) {
        try {
            filter.begin(Group.GROUP_OR);
            Object parameterValue = parameter.getParameterValue(context);
            if (parameterValue == null) {
                throw new ClusterJUserException(
                        local.message("ERR_Parameter_Cannot_Be_Null", "operator in", parameter.parameterName));
            } else if (parameterValue instanceof Iterable<?>) {
                Iterable<?> iterable = (Iterable<?>)parameterValue;
                for (Object value: iterable) {
                    property.filterCmpValue(value, BinaryCondition.COND_EQ, filter);
                }
            } else if (parameterValue.getClass().isArray()) {
                Object[] parameterArray = (Object[])parameterValue;
                for (Object parameter: parameterArray) {
                    property.filterCmpValue(parameter, BinaryCondition.COND_EQ, filter);
                }
            } else {
                throw new ClusterJUserException(
                        local.message("ERR_Parameter_Wrong_Type", parameter.parameterName,
                                parameterValue.getClass().getName(), "Iterable<?> or Object[]"));
            }
            filter.end();
        } catch (ClusterJException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ClusterJException(
                    local.message("ERR_Get_NdbFilter"), ex);
        }
    }

    public int getParameterSize(QueryExecutionContextImpl context) {
        int result = 1;
        Object parameterValue = parameter.getParameterValue(context);
        if (parameterValue instanceof List<?>) {
            result = ((List<?>)parameterValue).size();
        }
        Class<?> cls = parameterValue.getClass();
        if (cls.isArray()) {
            if (!Object.class.isAssignableFrom(cls.getComponentType())) {
                throw new ClusterJUserException(local.message("ERR_Wrong_Parameter_Type_For_In",
                        property.fmd.getName()));
            }
            Object[] parameterArray = (Object[])parameterValue;
            result = parameterArray.length;
        }
        if (result > 4096) {
            throw new ClusterJUserException(local.message("ERR_Parameter_Too_Big_For_In",
                        property.fmd.getName(), result));
        }
        // single value parameter
        return result;
    }

}