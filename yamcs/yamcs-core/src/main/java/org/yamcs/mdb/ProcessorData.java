package org.yamcs.mdb;

import static org.yamcs.xtce.XtceDb.YAMCS_SPACESYSTEM_NAME;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.yamcs.Processor;
import org.yamcs.ProcessorConfig;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.events.QuietEventProducer;
import org.yamcs.logging.Log;
import org.yamcs.mdb.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.XtceDb;

/**
 * Holds information related and required for XTCE processing. It is separated from Processor because it has to be
 * usable when not a full blown processor is available (e.g. XTCE packet processing)
 * 
 * <p>
 * Contains a cache of encoders, decoders, calibrators
 *
 */
public class ProcessorData {
    /**
     * converts raw values into engineering values
     */
    final ParameterTypeProcessor parameterTypeProcessor;

    private Map<Calibrator, CalibratorProc> calibrators = new HashMap<>();
    private Map<DataEncoding, DataDecoder> decoders = new HashMap<>();
    private Map<DataEncoding, DataEncoder> encoders = new HashMap<>();
    private Map<MatchCriteria, MatchCriteriaEvaluator> evaluators = new HashMap<>();

    final XtceDb xtcedb;
    final Log log;
    final EventProducer eventProducer;
    final String processorName;

    private Map<String, Object> userData = new HashMap<>();

    final private LastValueCache lastValueCache;
    /**
     * used to store parameter types which are changed dynamically (so they don't correspond anymore to MDB)
     */
    Map<Parameter, ParameterType> typeOverrides = new HashMap<>();
    String yamcsInstance;

    private ProcessorConfig processorConfig;

    public ProcessorData(Processor proc, ProcessorConfig config) {
        this(proc.getInstance(), proc.getName(), proc.getXtceDb(), config);

        long genTime = TimeEncoding.getWallclockTime();
        // populate with /yamcs/processor variables (these never change)
        ParameterValue procNamePv = getProcessorPV(xtcedb, genTime, "name", proc.getName(),
                "The name of the current processor");
        lastValueCache.add(procNamePv);

        String mode = proc.isReplay() ? "replay" : "realtime";
        ParameterValue procModePv = getProcessorPV(xtcedb, genTime, "mode", mode,
                "The mode of the current processor (either replay or realtime)");
        lastValueCache.add(procModePv);
    }

    /**
     * @param xtcedb
     * @param config
     *            - generate events in case of errors when processing data
     */
    public ProcessorData(String instance, String procName, XtceDb xtcedb, ProcessorConfig config) {
        this.yamcsInstance = instance;
        this.xtcedb = xtcedb;
        this.processorConfig = config;
        this.processorName = procName;

        parameterTypeProcessor = new ParameterTypeProcessor(this);
        log = new Log(this.getClass(), instance);
        log.setContext(procName);

        if ((instance != null) && config.generateEvents()) {
            eventProducer = EventProducerFactory.getEventProducer(instance);
        } else {// instance can be null when running in test or as a library - in this case we don't generate events
            eventProducer = new QuietEventProducer();
        }
        eventProducer.setSource("PROCESSOR(" + procName + ")");
        eventProducer.setRepeatedEventReduction(true, 10000);

        // populate last value cache with the default (initial) value for each parameter that has one
        long genTime = TimeEncoding.getWallclockTime();

        List<ParameterValue> constants = xtcedb.getParameters().stream()
                .filter(p -> p.getParameterType() != null && p.getDataSource() == DataSource.CONSTANT)
                .map(p -> getInitialValue(genTime, p))
                .filter(pv -> pv != null)
                .collect(Collectors.toList());

        lastValueCache = new LastValueCache(constants);

        xtcedb.getParameters().stream()
                .filter(p -> p.getParameterType() != null && p.getDataSource() != DataSource.CONSTANT)
                .map(p -> getInitialValue(genTime, p))
                .filter(pv -> pv != null)
                .forEach(pv -> lastValueCache.add(pv));

        log.debug("Initialized lastValueCache with {} entries", lastValueCache.size());

    }

    private ParameterValue getInitialValue(long genTime, Parameter p) {
        ParameterType ptype = p.getParameterType();

        Object o = p.getInitialValue();

        if (o == null) {
            o = ptype.getInitialValue();
        }
        if (o != null) {
            Value v = DataTypeProcessor.getValueForType(ptype, o);
            ParameterValue pv = new ParameterValue(p);
            pv.setEngValue(v);
            pv.setGenerationTime(genTime);
            pv.setAcquisitionTime(genTime);
            return pv;
        }
        return null;
    }

    private ParameterValue getProcessorPV(XtceDb mdb, long time, String name, String value, String description) {
        String fqn = NameDescription.qualifiedName(YAMCS_SPACESYSTEM_NAME, "processor", name);
        Parameter p = SystemParametersService.createSystemParameter(mdb, fqn, Yamcs.Value.Type.STRING, description);

        ParameterValue pv = new ParameterValue(p);

        pv.setAcquisitionTime(time);
        pv.setGenerationTime(time);
        pv.setStringValue(value);
        return pv;
    }

    /**
     * Set some object to be shared with all the users of this processor data
     * 
     * @param key
     * @param value
     */
    public <T> void setUserData(String key, T value) {
        userData.put(key, value);
    }

    /**
     * Get the instance of the user defined object if any. Returns null if no data has been set.
     * 
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getUserData(String key) {
        return (T) userData.get(key);
    }

    /**
     * returns a calibrator processor for the given data encoding. Can be null if the DataEncoding does not define a
     * calibrator.
     * 
     * @param pdata
     *            - contains the current list of parameters being extracted (not yet added to the lastValueCache). The
     *            list is used when evaluating contexts matches and have priority over the parameters in the
     *            lastValueCache.
     *            <p>
     *            Could be null if the calibration is not running as part of packet processing.
     * @return a calibrator processor or null
     */
    public CalibratorProc getCalibrator(ProcessingData pdata, DataEncoding de) {
        if (de instanceof NumericDataEncoding) {
            NumericDataEncoding nde = (NumericDataEncoding) de;
            Calibrator c = nde.getDefaultCalibrator();

            List<ContextCalibrator> clist = nde.getContextCalibratorList();
            if (clist != null) {
                for (ContextCalibrator cc : clist) {
                    MatchCriteriaEvaluator evaluator = getEvaluator(cc.getContextMatch());
                    if (evaluator.evaluate(pdata) == MatchResult.OK) {
                        c = cc.getCalibrator();
                        break;
                    }
                }
            }
            try {
                return getCalibrator(c);
            } catch (Exception e) {
                eventProducer.sendWarning("Could not get calibrator processor for " + c + ": " + e.toString());
                return null;
            }
        } else {
            return null;
        }
    }

    public CalibratorProc getDecalibrator(DataEncoding de) {
        return getCalibrator(null, de);
    }

    private CalibratorProc getCalibrator(Calibrator c) {
        if (c == null) {
            return null;
        }
        CalibratorProc calibrator = calibrators.get(c);
        if (calibrator == null) {
            if (c instanceof PolynomialCalibrator) {
                calibrator = new PolynomialCalibratorProc((PolynomialCalibrator) c);
            } else if (c instanceof SplineCalibrator) {
                calibrator = new SplineCalibratorProc((SplineCalibrator) c);
            } else if (c instanceof JavaExpressionCalibrator) {
                calibrator = JavaExpressionCalibratorFactory.compile((JavaExpressionCalibrator) c);
            } else if (c instanceof MathOperationCalibrator) {
                calibrator = MathOperationCalibratorFactory.compile((MathOperationCalibrator) c);
            } else {
                throw new IllegalStateException("No calibrator processor for " + c);
            }
            calibrators.put(c, calibrator);
        }
        return calibrator;
    }

    public MatchCriteriaEvaluator getEvaluator(MatchCriteria mc) {
        return evaluators.computeIfAbsent(mc,
                k -> MatchCriteriaEvaluatorFactory.getEvaluator(k));
    }

    public DataDecoder getDataDecoder(DataEncoding de) {
        DataDecoder dd = decoders.get(de);
        if (dd == null) {
            dd = DataDecoderFactory.get(de.getFromBinaryTransformAlgorithm());
        }

        return dd;
    }

    public DataEncoder getDataEncoder(DataEncoding de) {
        DataEncoder enc = encoders.get(de);
        if (enc == null) {
            enc = DataEncoderFactory.get(de.getToBinaryTransformAlgorithm());
        }

        return enc;
    }

    public XtceDb getXtceDb() {
        return xtcedb;
    }

    /**
     * Returns the parameter type processor (this is the guy that converts from raw to engineering value) used by the
     * associated processor.
     * 
     * @return
     */
    public ParameterTypeProcessor getParameterTypeProcessor() {
        return parameterTypeProcessor;
    }

    public EventProducer getEventProducer() {
        return eventProducer;
    }

    public LastValueCache getLastValueCache() {
        return lastValueCache;
    }

    public ParameterType getParameterType(Parameter parameter) {
        ParameterType pt = typeOverrides.get(parameter);
        if (pt == null) {
            pt = parameter.getParameterType();
        }
        return pt;
    }

    public void clearParameterOverrides(Parameter p) {
        typeOverrides.remove(p);
    }

    public void clearParameterCalibratorOverrides(Parameter p) {
        modifyNumericTypeOverride(p, b -> {
            b.setEncoding(p.getParameterType().getEncoding().toBuilder());
        });
    }

    public void setDefaultCalibrator(Parameter p, Calibrator defaultCalibrator) {
        modifyNumericTypeOverride(p, b -> {
            DataEncoding.Builder<?> enc = b.getEncoding();
            ((NumericDataEncoding.Builder<?>) enc).setDefaultCalibrator(defaultCalibrator);
        });
    }

    public void setContextCalibratorList(Parameter p, List<ContextCalibrator> contextCalibrator) {
        modifyNumericTypeOverride(p, b -> {
            DataEncoding.Builder<?> enc = b.getEncoding();
            ((NumericDataEncoding.Builder<?>) enc).setContextCalibratorList(contextCalibrator);
        });
    }

    private void modifyNumericTypeOverride(Parameter p, Consumer<NumericParameterType.Builder<?>> c) {
        NumericParameterType ptype = (NumericParameterType) typeOverrides.get(p);

        if (ptype == null) {
            if (!(p.getParameterType() instanceof NumericParameterType)) {
                throw new IllegalArgumentException("'" + p.getParameterType().getName() + "' is a non numeric type");
            }
            ptype = (NumericParameterType) p.getParameterType();
        }
        NumericParameterType.Builder<?> builder = ptype.toBuilder();
        c.accept(builder);
        typeOverrides.put(p, builder.build());
    }

    private void modifyEnumeratedTypeOverride(Parameter p, Consumer<EnumeratedParameterType.Builder> c) {
        EnumeratedParameterType ptype = (EnumeratedParameterType) typeOverrides.get(p);

        if (ptype == null) {
            if (!(p.getParameterType() instanceof EnumeratedParameterType)) {
                throw new IllegalArgumentException("'" + p.getParameterType().getName() + "' is a non enumerated type");
            }
            ptype = (EnumeratedParameterType) p.getParameterType();
        }
        EnumeratedParameterType.Builder builder = ptype.toBuilder();
        c.accept(builder);
        typeOverrides.put(p, builder.build());
    }

    public void clearParameterAlarmOverrides(Parameter p) {
        ParameterType ptype = typeOverrides.get(p);
        if (ptype == null) {
            return;
        }

        if (ptype instanceof NumericParameterType) {
            NumericParameterType mdbType = (NumericParameterType) p.getParameterType();
            NumericParameterType.Builder<?> builder = ((NumericParameterType) ptype).toBuilder();

            builder.setDefaultAlarm(mdbType.getDefaultAlarm());
            typeOverrides.put(p, builder.build());

        } else if (ptype instanceof EnumeratedParameterType) {
            EnumeratedParameterType mdbType = (EnumeratedParameterType) p.getParameterType();
            EnumeratedParameterType.Builder builder = ((EnumeratedParameterType) ptype).toBuilder();
            builder.setDefaultAlarm(mdbType.getDefaultAlarm());
            typeOverrides.put(p, builder.build());
        } else {
            throw new IllegalArgumentException("Can only have alarms on numeric and enumerated parameters");
        }
    }

    public void removeDefaultCalibrator(Parameter p) {
        setDefaultCalibrator(p, null);
    }

    public void removeDefaultAlarm(Parameter p) {
        modifyNumericTypeOverride(p, b -> b.setDefaultAlarm(null));
    }

    public void setDefaultNumericAlarm(Parameter p, NumericAlarm alarm) {
        modifyNumericTypeOverride(p, b -> b.setDefaultAlarm(alarm));
    }

    public void setNumericContextAlarm(Parameter p, List<NumericContextAlarm> contextAlarmList) {
        modifyNumericTypeOverride(p, b -> b.setContextAlarmList(contextAlarmList));
    }

    public void setDefaultEnumerationAlarm(Parameter p, EnumerationAlarm alarm) {
        modifyEnumeratedTypeOverride(p, b -> b.setDefaultAlarm(alarm));
    }

    public void setEnumerationContextAlarm(Parameter p, List<EnumerationContextAlarm> contextAlarmList) {
        modifyEnumeratedTypeOverride(p, b -> b.setContextAlarmList(contextAlarmList));
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public ProcessorConfig getProcessorConfig() {
        return processorConfig;
    }

    public String getProcessorName() {
        return processorName;
    }

}
