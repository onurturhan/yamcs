package org.yamcs.algorithms;

import java.util.List;

import org.yamcs.mdb.ProcessingData;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.OutputParameter;

/**
 * Represents the execution context of one algorithm.
 * <p>
 * An AlgorithmExecutor is reused upon each update of one or more of its InputParameters.
 * 
 */
public interface AlgorithmExecutor {
    Algorithm getAlgorithm();

    /**
     * This method is called each time new parameters are received (for example extracting them from a packet).
     * <p>
     * The executor should copy its inputs if updated or should use the list to determine if it should
     * run.
     * 
     * @return true if the algorithm should run
     */
    boolean update(ProcessingData processingData);

    /**
     * Runs the associated algorithm with the latest InputParameters.
     * <p>
     * Should throw an exception if there is an error within the algorithm.
     * <p>
     * The error message and error count will be remembered and available to external clients via the API.
     * <p>
     * The processing data parameter is passed if required to calibrate the output values. It shall not be updated by
     * the executor as it is done in the {@link ActiveAlgorithm}
     * 
     * @param acqTime
     * @param genTime
     * @return the output parameters, if any
     * 
     */
    AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) throws AlgorithmException;

    /**
     * 
     * @return the execution context in which the executor activates
     */
    AlgorithmExecutionContext getExecutionContext();

    /**
     * Some algorithms have dynamic outputs which are not known until the algorithm is instantiated.
     * <p>
     * This method can be used to return the list of parameters those algorithms can provide as outputs.
     * <p>
     * The output list is used in the replays when only some parameters are to be extracted, in order to figure out
     * which algorithms have to be run (only those providing the required parameters are run).
     * <p>
     * This list is used only if the algorithm definition includes no output list. If the algorithm definition includes
     * an output list, this method is not called (and the algorithm executor will not be instantiated at all if the
     * algorithm outputs are not required).
     *
     * @return list of parameters which can be provided as output
     */
    default List<OutputParameter> getOutputSet() {
        return null;
    }
}
