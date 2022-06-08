package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;

/**
 * Builds list of parameter id and parameter value.
 * <p>
 * The list can be sorted on parameter ids using the {@link #sort()} method
 * <p>
 * Any parameter which is not in the ParameterIdDb will be added. This includes the aggregates and arrays.
 */
class BasicParameterList {
    final ParameterIdDb parameterIdMap;
    final IntArray idArray = new IntArray();
    final List<BasicParameterValue> pvList = new ArrayList<>();

    public BasicParameterList(ParameterIdDb parameterIdMap) {
        this.parameterIdMap = parameterIdMap;
    }

    // add the parameter to the list but also expand if it is an aggregate or array
    void add(ParameterValue pv) {
        String fqn = pv.getParameterQualifiedName();
        if (pv.getEngValue() instanceof AggregateValue || pv.getEngValue() instanceof ArrayValue) {
            IntArray aggrray = new IntArray();
            add(fqn, pv, aggrray);
            parameterIdMap.createAndGetAggrray(fqn, aggrray);
        } else {
            add(fqn, pv, null);
        }
    }

    void add(String name, BasicParameterValue pv, IntArray aggrray) {
        Value engValue = pv.getEngValue();
        Value rawValue = pv.getRawValue();
        Type engType = engValue.getType();
        Type rawType = (rawValue == null) ? null : rawValue.getType();

        if (engValue instanceof AggregateValue) {
            addAggregate(name, pv, aggrray);
        } else if (engValue instanceof ArrayValue) {
            addArray(name, pv, aggrray);
        } else {
            int parameterId = parameterIdMap.createAndGet(name, engType, rawType);
            idArray.add(parameterId);
            pvList.add(pv);
            if (aggrray != null) {
                aggrray.add(parameterId);
            }
        }
    }

    private void addAggregate(String name, BasicParameterValue pv, IntArray aggrray) {
        AggregateValue engValue = (AggregateValue) pv.getEngValue();
        AggregateValue rawValue = (AggregateValue) pv.getRawValue();

        int n = engValue.numMembers();
        for (int i = 0; i < n; i++) {
            String mname = engValue.getMemberName(i);
            Value mEngvalue = engValue.getMemberValue(i);
            BasicParameterValue pv1 = new BasicParameterValue();
            pv1.setStatus(pv.getStatus());
            pv1.setEngValue(mEngvalue);
            pv1.setGenerationTime(pv.getGenerationTime());

            if (rawValue != null) {
                Value mRawValue = rawValue.getMemberValue(i);
                pv1.setRawValue(mRawValue);
            }
            add(name + "." + mname, pv1, aggrray);
        }
    }

    private void addArray(String name, BasicParameterValue pv, IntArray aggrray) {
        ArrayValue engValue = (ArrayValue) pv.getEngValue();
        ArrayValue rawValue = (ArrayValue) pv.getRawValue();

        int[] dim = engValue.getDimensions();
        int n = dim.length;
        int[] idx = new int[n];

        while (true) {
            String mname = toIndexSpecifier(idx);
            Value mEngvalue = engValue.getElementValue(idx);
            BasicParameterValue pv1 = new BasicParameterValue();
            pv1.setStatus(pv.getStatus());
            pv1.setEngValue(mEngvalue);
            if (rawValue != null) {
                Value mRawValue = rawValue.getElementValue(idx);
                pv1.setRawValue(mRawValue);
            }
            add(name + mname, pv1, aggrray);

            int k = n - 1;
            while (k >= 0 && ++idx[k] >= dim[k]) {
                k--;
            }
            if (k < 0) {
                break;
            }
            while (++k < n) {
                idx[k] = 0;
            }
        }
    }

    private static String toIndexSpecifier(int[] dims) {
        String[] dimStrings = Arrays.stream(dims).mapToObj(String::valueOf).toArray(String[]::new);
        return "[" + String.join("][", dimStrings) + "]";
    }

    public int size() {
        return idArray.size();
    }

    public IntArray getPids() {
        return idArray;
    }

    public List<BasicParameterValue> getValues() {
        return pvList;
    }

    // sort the parameters by id
    public void sort() {
        idArray.sort(pvList);
    }

    public String toString() {
        return pvList.toString();
    }
}
