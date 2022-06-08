package org.yamcs.xtce;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.xml.XtceAliasSet;

/**
 * XtceDB database
 *
 * It contains a SpaceSystem as defined in the Xtce schema and has lots of hashes to help find things quickly
 *
 * @author nm
 */
public class XtceDb implements Serializable {
    private static final long serialVersionUID = 57L;

    final SpaceSystem rootSystem;

    // rwLock is used to guard the read/write of parameters and spaceSystems which are the only ones that can change
    // dynamically as of now
    ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Namespaces system parameters
     */
    public static final String YAMCS_SPACESYSTEM_NAME = "/yamcs";
    public static final String YAMCS_CMD_SPACESYSTEM_NAME = "/yamcs/cmd";
    public static final String YAMCS_CMDARG_SPACESYSTEM_NAME = "/yamcs/cmd/arg";
    public static final String YAMCS_CMDHIST_SPACESYSTEM_NAME = "/yamcs/cmdHist";

    transient static Logger log = LoggerFactory.getLogger(XtceDb.class);

    // map from the fully qualified names to the objects
    private HashMap<String, SpaceSystem> spaceSystems = new HashMap<>();
    private Map<String, SequenceContainer> sequenceContainers = new LinkedHashMap<>();
    private Map<String, Parameter> parameters = new LinkedHashMap<>();
    private Map<String, ParameterType> parameterTypes = new LinkedHashMap<>();
    private HashMap<String, Algorithm> algorithms = new HashMap<>();
    private HashMap<String, MetaCommand> commands = new HashMap<>();

    @SuppressWarnings("rawtypes")
    private HashMap<Class<?>, NonStandardData> nonStandardDatas = new HashMap<>();

    // different namespaces
    private NamedDescriptionIndex<SpaceSystem> spaceSystemAliases = new NamedDescriptionIndex<>();
    private NamedDescriptionIndex<Parameter> parameterAliases = new NamedDescriptionIndex<>();
    private NamedDescriptionIndex<NameDescription> parameterTypeAliases = new NamedDescriptionIndex<>();
    private NamedDescriptionIndex<SequenceContainer> sequenceContainerAliases = new NamedDescriptionIndex<>();
    private NamedDescriptionIndex<Algorithm> algorithmAliases = new NamedDescriptionIndex<>();
    private NamedDescriptionIndex<MetaCommand> commandAliases = new NamedDescriptionIndex<>();
    private Map<String, List<IndirectParameterRefEntry>> indirectParameterRefEntries = new HashMap<>();

    private Set<String> namespaces = new HashSet<>();

    // this is the sequence container where the xtce processors start processing
    // we should perhaps have possibility to specify different ones for different streams
    SequenceContainer rootSequenceContainer;

    /**
     * Maps the Parameter to a list of ParameterEntry such that we know from which container we can extract this
     * parameter
     */
    private HashMap<Parameter, ArrayList<ParameterEntry>> parameter2ParameterEntryMap;

    /**
     * maps the SequenceContainer to a list of other EntryContainers in case of aggregation
     */
    private HashMap<SequenceContainer, ArrayList<ContainerEntry>> sequenceContainer2ContainerEntryMap;

    /**
     * maps the SequenceContainer to a list of containers inheriting this one
     */
    private HashMap<SequenceContainer, ArrayList<SequenceContainer>> sequenceContainer2InheritingContainerMap;

    public XtceDb(SpaceSystem spaceSystem) {
        this.rootSystem = spaceSystem;
    }

    public SequenceContainer getSequenceContainer(String qualifiedName) {
        return sequenceContainers.get(qualifiedName);
    }

    public SequenceContainer getSequenceContainer(String namespace, String name) {
        return sequenceContainerAliases.get(namespace, name);
    }

    public SequenceContainer getSequenceContainer(NamedObjectId id) {
        if (id.hasNamespace()) {
            return sequenceContainerAliases.get(id.getNamespace(), id.getName());
        } else {
            return sequenceContainerAliases.get(id.getName());
        }
    }

    public Parameter getParameter(String qualifiedName) {
        rwLock.readLock().lock();
        try {
            int idx = qualifiedName.indexOf('/');
            if (idx == 0) {
                return parameters.get(qualifiedName);
            } else if (idx > 0) {
                String namespace = qualifiedName.substring(0, idx);
                String name = qualifiedName.substring(idx + 1);
                return getParameter(namespace, name);
            }
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static NamedObjectId toNamedObjectId(String qualifiedName) {
        int idx = qualifiedName.indexOf('/');
        if (idx == 0) {
            return NamedObjectId.newBuilder().setName(qualifiedName).build();
        } else if (idx > 0) {
            return NamedObjectId.newBuilder()
                    .setNamespace(qualifiedName.substring(0, idx))
                    .setName(qualifiedName.substring(idx + 1))
                    .build();
        }
        throw new IllegalArgumentException("Invalid parameter id " + qualifiedName);
    }

    public Parameter getParameter(String namespace, String name) {
        rwLock.readLock().lock();
        try {
            return parameterAliases.get(namespace, name);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Parameter getParameter(NamedObjectId id) {
        rwLock.readLock().lock();
        try {
            if (id.hasNamespace()) {
                return parameterAliases.get(id.getNamespace(), id.getName());
            } else {
                return parameterAliases.get(id.getName());
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public ParameterType getParameterType(String qualifiedName) {
        return parameterTypes.get(qualifiedName);
    }

    public ParameterType getParameterType(String namespace, String name) {
        return (ParameterType) parameterTypeAliases.get(namespace, name);
    }

    public ParameterType getParameterType(NamedObjectId id) {
        if (id.hasNamespace()) {
            return (ParameterType) parameterTypeAliases.get(id.getNamespace(), id.getName());
        } else {
            return (ParameterType) parameterTypeAliases.get(id.getName());
        }
    }

    public SequenceContainer getRootSequenceContainer() {
        return rootSequenceContainer;
    }

    public void setRootSequenceContainer(SequenceContainer sc) {
        this.rootSequenceContainer = sc;
    }

    public Algorithm getAlgorithm(String qualifiedName) {
        return algorithmAliases.get(qualifiedName);
    }

    public Algorithm getAlgorithm(String namespace, String name) {
        return algorithmAliases.get(namespace, name);
    }

    public Algorithm getAlgorithm(NamedObjectId id) {
        if (id.hasNamespace()) {
            return algorithmAliases.get(id.getNamespace(), id.getName());
        } else {
            return algorithmAliases.get(id.getName());
        }
    }

    public Collection<Algorithm> getAlgorithms() {
        return algorithms.values();
    }

    public Collection<Parameter> getParameters() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(parameters.values());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Collection<ParameterType> getParameterTypes() {
        return parameterTypes.values();
    }

    public boolean containsNamespace(String namespace) {
        return namespaces.contains(namespace);
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    /**
     * Returns a meta command by fully qualified name.
     * 
     * @param qualifiedName
     *            - fully qualified name of the command to be returned.
     * @return the meta command having the given qualified name. If no such command exists, <code>null</code> is
     *         returned.
     */
    public MetaCommand getMetaCommand(String qualifiedName) {
        rwLock.readLock().lock();
        try {
            return commandAliases.get(qualifiedName);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns a command based on a name in a namespace
     * 
     * @param namespace
     * @param name
     * @return the meta command having the given name in the given namespace. If no such meta command exists,
     *         <code>null</code> is returned.
     */
    public MetaCommand getMetaCommand(String namespace, String name) {
        rwLock.readLock().lock();
        try {
            return commandAliases.get(namespace, name);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public MetaCommand getMetaCommand(NamedObjectId id) {
        rwLock.readLock().lock();
        try {
            if (id.hasNamespace()) {
                return commandAliases.get(id.getNamespace(), id.getName());
            } else {
                return commandAliases.get(id.getName());
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the list of MetaCommmands in the XTCE database
     * 
     * @return
     */
    public Collection<MetaCommand> getMetaCommands() {
        rwLock.readLock().lock();
        try {
            return commands.values();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public SpaceSystem getRootSpaceSystem() {
        return rootSystem;
    }

    public SpaceSystem getSpaceSystem(String qualifiedName) {
        rwLock.readLock().lock();
        try {
            return spaceSystemAliases.get(qualifiedName);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public SpaceSystem getSpaceSystem(String namespace, String name) {
        rwLock.readLock().lock();
        try {
            return spaceSystemAliases.get(namespace, name);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public SpaceSystem getSpaceSystem(NamedObjectId id) {
        rwLock.readLock().lock();
        try {
            if (id.hasNamespace()) {
                return spaceSystemAliases.get(id.getNamespace(), id.getName());
            } else {
                return spaceSystemAliases.get(id.getName());
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Collection<SequenceContainer> getSequenceContainers() {
        return sequenceContainers.values();
    }

    /**
     *
     * @return list of ParameterEntry corresponding to a given parameter or <code>null</code> if no such entry exists.
     */
    public List<ParameterEntry> getParameterEntries(Parameter p) {
        return parameter2ParameterEntryMap.get(p);
    }

    /**
     * @return list of ContainerEntry corresponding to a given sequence container or <code>null</code> if no such entry
     *         exists.
     */
    public List<ContainerEntry> getContainerEntries(SequenceContainer sc) {
        return sequenceContainer2ContainerEntryMap.get(sc);
    }

    public Collection<String> getParameterNames() {
        return parameters.keySet();
    }

    @SuppressWarnings("unchecked")
    public <T extends NonStandardData<T>> T getNonStandardDataOfType(Class<T> clazz) {
        if (nonStandardDatas.containsKey(clazz)) {
            return (T) nonStandardDatas.get(clazz);
        } else {
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    public Collection<NonStandardData> getNonStandardData() {
        return nonStandardDatas.values();
    }

    /**
     * Called after the database has been populated to build the maps for quickly finding things
     *
     */
    public void buildIndexMaps() {
        buildSpaceSystemsMap(rootSystem);
        buildParameterMap(rootSystem);
        buildParameterTypeMap(rootSystem);
        buildSequenceContainerMap(rootSystem);
        buildAlgorithmMap(rootSystem);
        buildMetaCommandMap(rootSystem);
        buildNonStandardDataMap(rootSystem);

        parameter2ParameterEntryMap = new HashMap<>();
        sequenceContainer2ContainerEntryMap = new HashMap<>();
        sequenceContainer2InheritingContainerMap = new HashMap<>();
        for (SequenceContainer sc : sequenceContainers.values()) {
            for (SequenceEntry se : sc.getEntryList()) {
                if (se instanceof ParameterEntry) {
                    ParameterEntry pe = (ParameterEntry) se;
                    Parameter param = pe.getParameter();
                    ArrayList<ParameterEntry> al = parameter2ParameterEntryMap.computeIfAbsent(param,
                            k -> new ArrayList<>());
                    al.add(pe);
                } else if (se instanceof ContainerEntry) {
                    ContainerEntry ce = (ContainerEntry) se;
                    ArrayList<ContainerEntry> al = sequenceContainer2ContainerEntryMap
                            .computeIfAbsent(ce.getRefContainer(), k -> new ArrayList<>());
                    al.add(ce);
                } else if (se instanceof IndirectParameterRefEntry) {
                    IndirectParameterRefEntry ipe = (IndirectParameterRefEntry) se;
                    List<IndirectParameterRefEntry> l = indirectParameterRefEntries
                            .computeIfAbsent(ipe.getAliasNameSpace(), k -> new ArrayList<>());
                    l.add(ipe);
                }
            }
            if (sc.baseContainer != null) {
                ArrayList<SequenceContainer> al_sc = sequenceContainer2InheritingContainerMap
                        .get(sc.baseContainer);
                if (al_sc == null) {
                    al_sc = new ArrayList<>();
                    sequenceContainer2InheritingContainerMap.put(sc.getBaseContainer(), al_sc);
                }
                al_sc.add(sc);
            }
        }

        // build aliases maps
        for (SpaceSystem ss : spaceSystems.values()) {
            spaceSystemAliases.add(ss);
            XtceAliasSet aliases = ss.getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for (SequenceContainer sc : sequenceContainers.values()) {
            sequenceContainerAliases.add(sc);
            XtceAliasSet aliases = sc.getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for (Parameter p : parameters.values()) {
            parameterAliases.add(p);
            XtceAliasSet aliases = p.getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for (ParameterType t : parameterTypes.values()) {
            parameterTypeAliases.add((NameDescription) t);
            XtceAliasSet aliases = ((NameDescription) t).getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for (Algorithm a : algorithms.values()) {
            algorithmAliases.add(a);
            XtceAliasSet aliases = a.getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for (MetaCommand mc : commands.values()) {
            commandAliases.add(mc);
            XtceAliasSet aliases = mc.getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }
    }

    private void buildSpaceSystemsMap(SpaceSystem ss) {
        spaceSystems.put(ss.getQualifiedName(), ss);
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            buildSpaceSystemsMap(ss1);
        }
    }

    private void buildParameterMap(SpaceSystem ss) {
        for (Parameter p : ss.getParameters()) {
            parameters.put(p.getQualifiedName(), p);
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            buildParameterMap(ss1);
        }
    }

    private void buildParameterTypeMap(SpaceSystem ss) {
        for (ParameterType t : ss.getParameterTypes()) {
            String qualifiedName = ((NameDescription) t).getQualifiedName();
            parameterTypes.put(qualifiedName, t);
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            buildParameterTypeMap(ss1);
        }
    }

    private void buildSequenceContainerMap(SpaceSystem ss) {
        for (SequenceContainer sc : ss.getSequenceContainers()) {
            sequenceContainers.put(sc.getQualifiedName(), sc);
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            buildSequenceContainerMap(ss1);
        }
    }

    private void buildAlgorithmMap(SpaceSystem ss) {
        for (Algorithm a : ss.getAlgorithms()) {
            algorithms.put(a.getQualifiedName(), a);
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            buildAlgorithmMap(ss1);
        }
    }

    private void buildMetaCommandMap(SpaceSystem ss) {
        for (MetaCommand mc : ss.getMetaCommands()) {
            commands.put(mc.getQualifiedName(), mc);
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            buildMetaCommandMap(ss1);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void buildNonStandardDataMap(SpaceSystem ss) {
        for (NonStandardData data : ss.getNonStandardData()) {
            if (nonStandardDatas.containsKey(data.getClass())) {
                NonStandardData mergeResult = nonStandardDatas.get(data.getClass()).mergeWithChild(data);
                nonStandardDatas.put(data.getClass(), mergeResult);
            } else {
                nonStandardDatas.put(data.getClass(), data);
            }
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            buildNonStandardDataMap(ss1);
        }
    }

    /**
     * Get the list of containers inheriting from the given container
     * 
     * @param container
     * @return
     */
    public List<SequenceContainer> getInheritingContainers(SequenceContainer container) {
        return sequenceContainer2InheritingContainerMap.get(container);
    }

    /**
     * Adds a new parameter to the XTCE db.
     *
     *
     * If the SpaceSystem where this parameter belongs does not exist, and createSpaceSystem is false, throws an
     * IllegalArgumentException If the SpaceSystem where this parameter belongs exists and already contains an parameter
     * by this name, throws and IllegalArgumentException If the SpaceSystem where this parameter belongs does not exist,
     * and createSpaceSystem is true, the whole SpaceSystem hierarchy is created.
     * 
     * Note that this method is used to create parameters on the fly. The parameters are not saved anywhere and they
     * will not be available when this object is created by the XtceDbFactory.
     *
     * @param p
     * @param createSpaceSystem
     *            - if true, create all the necessary space systems
     */
    public void addParameter(Parameter p, boolean createSpaceSystem) {
        log.debug("Adding parameter {} , createSpaceSystem: {}", p.getQualifiedName(), createSpaceSystem);
        rwLock.writeLock().lock();
        try {
            if (parameters.containsKey(p.getQualifiedName())) {
                throw new IllegalArgumentException(
                        "There is already a parameter with qualified name '" + p.getQualifiedName() + "'");
            }
            String ssname = p.getSubsystemName();
            SpaceSystem ss = spaceSystems.get(ssname);
            if (ss == null) {
                if (!createSpaceSystem) {
                    throw new IllegalArgumentException("No SpaceSystem by name '" + ssname + "'");
                } else {
                    createAllSpaceSystems(ssname);
                }
                ss = spaceSystems.get(ssname);
            }
            ss.addParameter(p);
            parameters.put(p.getQualifiedName(), p);

            parameterAliases.add(p);
            XtceAliasSet aliases = p.getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Adds a new parameter type to the XTCE db.
     * <p>
     *
     * If the SpaceSystem where this parameter type does not exist, and createSpaceSystem is false, throws an
     * IllegalArgumentException.
     * <p>
     * If the SpaceSystem where this parameter belongs exists and already contains an parameter type by this name,
     * throws and IllegalArgumentException
     * <p>
     * If the SpaceSystem where this parameter belongs does not exist, and createSpaceSystem is true, the whole
     * SpaceSystem hierarchy is created.
     * <p>
     * 
     *
     * @param ptype
     *            - the parameter type to be added
     * @param createSpaceSystem
     *            - if true, create all the necessary space systems
     */
    public void addParameterType(ParameterType ptype, boolean createSpaceSystem) {
        String fqn = ptype.getQualifiedName();
        log.debug("Adding parameter type {} , createSpaceSystem: {}", fqn, createSpaceSystem);
        rwLock.writeLock().lock();
        try {
            if (parameterTypes.containsKey(fqn)) {
                throw new IllegalArgumentException(
                        "There is already a parameter with qualified name '" + fqn + "'");
            }
            String ssname = NameDescription.getSubsystemName(fqn);
            SpaceSystem ss = spaceSystems.get(ssname);
            if (ss == null) {
                if (!createSpaceSystem) {
                    throw new IllegalArgumentException("No SpaceSystem by name '" + ssname + "'");
                } else {
                    createAllSpaceSystems(ssname);
                }
            }
            ss = spaceSystems.get(ssname);
            ss.addParameterType(ptype);
            parameterTypes.put(fqn, ptype);

            parameterTypeAliases.add((NameDescription) ptype);
            XtceAliasSet aliases = ((NameDescription) ptype).getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Creates and returns a system parameter with the given qualified name. If the parameter already exists it is
     * returned.
     * 
     * 
     * @param parameterQualifiedNamed
     *            - the name of the parmaeter to be created. It must start with {@link #YAMCS_SPACESYSTEM_NAME}
     * @param ptype
     * @return the parameter created or already existing.
     * @throws IllegalArgumentException
     *             if the <code>parameterQualifiedNamed</code> does not start with {@link #YAMCS_SPACESYSTEM_NAME}
     */
    public SystemParameter createSystemParameter(String parameterQualifiedNamed, ParameterType ptype,
            String shortDescription) {
        rwLock.writeLock().lock();
        try {
            if (!parameterQualifiedNamed.startsWith(YAMCS_SPACESYSTEM_NAME)) {
                throw new IllegalArgumentException(
                        "The parameter qualified name must start with " + YAMCS_SPACESYSTEM_NAME);
            }

            SystemParameter p = (SystemParameter) parameters.get(parameterQualifiedNamed);
            if (p == null) {
                p = SystemParameter.getForFullyQualifiedName(parameterQualifiedNamed);
                p.setParameterType(ptype);
                addParameter(p, true);
            } else {
                if (p.getParameterType() != ptype) {
                    throw new IllegalArgumentException("A parameter with name " + parameterQualifiedNamed
                            + " already exists but has a different type: " + p.getParameterType()
                            + " The type in the request was: " + ptype);
                }
            }
            p.setShortDescription(shortDescription);
            return p;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Adds a parameter type to the MDB. The type has to have the qualified name set and start with
     * {@link #YAMCS_SPACESYSTEM_NAME}.
     * <p>
     * If a type with the same name already exists, it is returned instead. No check is performed that the existing type
     * and the new type are the same or even compatible.
     * 
     * @param ptype
     * @return
     */
    public ParameterType addSystemParameterType(ParameterType ptype) {
        String fqn = ptype.getQualifiedName();
        if (fqn == null) {
            throw new IllegalArgumentException("The type does not have a qualified name");
        }
        rwLock.writeLock().lock();
        try {
            if (!fqn.startsWith(YAMCS_SPACESYSTEM_NAME)) {
                throw new IllegalArgumentException(
                        "The qualified name of the type must start with " + YAMCS_SPACESYSTEM_NAME);
            }

            ParameterType ptype1 = parameterTypes.get(fqn);

            if (ptype1 == null) {
                addParameterType(ptype, true);
            } else {
                ptype = ptype1;
            }
            return ptype;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void createAllSpaceSystems(String ssname) {
        String[] a = ssname.split("/");
        String qn = "";
        for (String name : a) {
            if (name.isEmpty()) {
                continue;
            }
            qn = qn + "/" + name;
            if (getSpaceSystem(qn) == null) {
                SpaceSystem ss = new SpaceSystem(name);
                ss.setQualifiedName(qn);
                addSpaceSystem(ss);
            }
        }
    }

    /**
     * Adds a new command definition to the XTCE db.
     * <p>
     * Note that this method is used to create commands on the fly.<br>
     * The commands are not saved anywhere and they will not be available when this object is created with the
     * XtceDbFactory.
     */
    public void addMetaCommand(MetaCommand c) {
        rwLock.writeLock().lock();
        try {
            String ssname = c.getSubsystemName();
            SpaceSystem ss = spaceSystems.get(ssname);
            if (ss == null) {
                throw new IllegalArgumentException("No SpaceSystem by name '" + ssname + "'");
            }
            ss.addMetaCommand(c);
            commands.put(c.getQualifiedName(), c);

            commandAliases.add(c);
            XtceAliasSet aliases = c.getAliasSet();
            if (aliases != null) {
                namespaces.addAll(aliases.getNamespaces());
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Adds a new {@link SpaceSystem} to the XTCE database.
     *
     * It throws an IllegalArgumentException in the following circumstances:
     * <ul>
     * <li>if a SpaceSystem with this name already exists
     * <li>if {@link SpaceSystem#getParent() system.getParent()} does not return null
     * <li>if the parent SpaceSystem (identified based on the {@link SpaceSystem#getSubsystemName()}) does not exist
     * <li>if the space system is not empty
     * </ul>
     *
     * This method also sets the parent of the passed spacesystem to its parent object.
     *
     * Note that this method is used to create SpaceSystems on the fly. The SpaceSystems are not saved anywhere and they
     * will not be available when this object is created by the XtceDbFactory.
     * 
     * @param system
     *            - the space system to be added.
     *
     */
    public void addSpaceSystem(SpaceSystem system) {
        rwLock.writeLock().lock();
        try {
            if (system.getParent() != null) {
                throw new IllegalArgumentException(
                        "The parent of the space system has to be null (it will be set by this method");
            }
            if (!system.getParameters().isEmpty() || !system.getSequenceContainers().isEmpty()
                    || !system.getAlgorithms().isEmpty()
                    || !system.getMetaCommands().isEmpty() || !system.getSubSystems().isEmpty()) {
                throw new IllegalArgumentException(
                        "The space system must be empty (no parameters, containers, commands, algorithms, subsystems)");
            }

            String parentName = system.getSubsystemName();
            SpaceSystem parent = spaceSystems.get(parentName);
            if (parent == null) {
                throw new IllegalArgumentException("The parent subsystem '" + parentName + "' does not exist");
            }

            parent.addSpaceSystem(system);
            system.setParent(parent);

            spaceSystems.put(system.getQualifiedName(), system);
            spaceSystemAliases.add(system);
            XtceAliasSet aliases = system.getAliasSet();
            if (aliases != null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Checks if the named object refers to a system parameter:
     * <ul>
     * <li>either the namespace starts with {@link XtceDb#YAMCS_SPACESYSTEM_NAME}</li>
     * <li>or there is no namespace and the fully qualified name starts with {@link XtceDb#YAMCS_SPACESYSTEM_NAME}</li>
     * </ul>
     * 
     * @param id
     * @return
     */
    public static boolean isSystemParameter(NamedObjectId id) {
        boolean result;
        if (!id.hasNamespace()) {
            result = id.getName().startsWith(XtceDb.YAMCS_SPACESYSTEM_NAME);
        } else {
            result = id.getNamespace().startsWith(XtceDb.YAMCS_SPACESYSTEM_NAME);
        }
        return result;
    }

    /**
     * Checks if a fully qualified name is the name of a system parameter. That is if <code>fqn</code> starts with
     * {@link XtceDb#YAMCS_SPACESYSTEM_NAME}
     * 
     * @param fqn
     * @return
     */
    public static boolean isSystemParameter(String fqn) {
        return fqn.startsWith(XtceDb.YAMCS_SPACESYSTEM_NAME);
    }

    /**
     * Returns a collection of all the {@link SpaceSystem} objects in the XTCE database.
     * 
     * @return the collection of space systems.
     */
    public Collection<SpaceSystem> getSpaceSystems() {
        return spaceSystems.values();
    }

    /**
     * Retrieve the list of {@link IndirectParameterRefEntry} for a given alias namespace.
     * 
     * @param namespace
     *            - the namespace for which the indirect parameter reference entries should be retrieved. Can be null to
     *            return the entries without a namespace.
     * @return the list of indirect parameter reference entries whose alias namespace is equal to the given namespace.
     *         If no such entry exists, <code>null</code> is returned.
     */
    public Collection<IndirectParameterRefEntry> getIndirectParameterRefEntries(String namespace) {
        return indirectParameterRefEntries.get(namespace);
    }

    private void print(SpaceSystem ss, PrintStream out) {
        if (ss.getHeader() != null) {
            out.println("=========SpaceSystem " + ss.getQualifiedName() + " version: " + ss.getHeader().getVersion()
                    + " date: " + ss.getHeader().getDate() + "=========");
        } else {
            out.println("=========SpaceSystem " + ss.getQualifiedName() + " (no header information)=========");
        }

        Comparator<NameDescription> comparator = (o1, o2) -> o1.getName().compareTo(o2.getName());

        SequenceContainer[] sca = ss.getSequenceContainers().toArray(new SequenceContainer[0]);
        Arrays.sort(sca, comparator);
        for (SequenceContainer sc : sca) {
            sc.print(out);
        }

        Algorithm[] aa = ss.getAlgorithms().toArray(new Algorithm[0]);
        Arrays.sort(aa, comparator);
        for (Algorithm a : aa) {
            a.print(out);
        }

        MetaCommand[] mca = ss.getMetaCommands().toArray(new MetaCommand[0]);
        Arrays.sort(mca, comparator);
        for (MetaCommand mc : mca) {
            mc.print(out);
        }

        // print the list of system variables if any (because those will not be part of the sequence containers)
        List<SystemParameter> systemVariables = new ArrayList<>();
        for (Parameter p : ss.getParameters()) {
            if (p instanceof SystemParameter) {
                systemVariables.add((SystemParameter) p);
            }
        }
        if (!systemVariables.isEmpty()) {
            out.println("System Parameters: ");
            SystemParameter[] sva = systemVariables.toArray(new SystemParameter[0]);
            Arrays.sort(sva, comparator);
            for (SystemParameter sv : sva) {
                out.println("\t" + sv.getName());
            }
        }

        SpaceSystem[] ssa = ss.getSubSystems().toArray(new SpaceSystem[0]);
        Arrays.sort(ssa, comparator);
        for (SpaceSystem ss1 : ssa) {
            print(ss1, out);
        }
    }

    public void print(PrintStream out) {
        print(rootSystem, out);

        Set<Parameter> orphanedParameters = new HashSet<>();
        orphanedParameters.addAll(parameters.values());
        removeNonOrphaned(rootSystem, orphanedParameters);
        orphanedParameters.removeAll(parameter2ParameterEntryMap.keySet());

        if (!orphanedParameters.isEmpty()) {
            out.println("================ Orphaned parameters (not referenced in any container or algorithm):");
            for (Parameter p : orphanedParameters) {
                String namespaces = "";
                if (p.getAliasSet() != null) {
                    namespaces = ", aliases: " + p.getAliasSet();
                }
                out.println(p.getQualifiedName() + ", datasource: " + p.getDataSource() + namespaces + " type: "
                        + p.getParameterType());
            }
        }
    }

    private static void removeNonOrphaned(SpaceSystem ss, Set<Parameter> orphanedParameters) {
        for (Algorithm a : ss.getAlgorithms()) {
            for (InputParameter p : a.getInputSet()) {
                ParameterInstanceRef pref = p.getParameterInstance();
                if (pref != null) {
                    orphanedParameters.remove(pref.getParameter());
                }
            }
            for (OutputParameter p : a.getOutputSet()) {
                orphanedParameters.remove(p.getParameter());
            }
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            removeNonOrphaned(ss1, orphanedParameters);
        }
    }


}
