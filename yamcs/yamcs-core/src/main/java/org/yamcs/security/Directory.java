package org.yamcs.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.security.protobuf.AccountCollection;
import org.yamcs.security.protobuf.AccountRecord;
import org.yamcs.security.protobuf.GroupCollection;
import org.yamcs.security.protobuf.GroupRecord;
import org.yamcs.yarch.ProtobufDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Stores user, group and application information in the Yamcs database.
 */
public class Directory {

    private static final Log log = new Log(Directory.class);
    private static final String ACCOUNT_COLLECTION = "accounts";
    private static final String GROUP_COLLECTION = "groups";
    private static final PasswordHasher hasher = new PBKDF2PasswordHasher();

    // Reserve first few ids for potential future use
    // (also not to overlap with system and guest users which are not currently in the directory)
    private AtomicInteger accountIdSequence = new AtomicInteger(5);
    private AtomicInteger groupIdSequence = new AtomicInteger(5);

    private Map<String, User> users = new ConcurrentHashMap<>();
    private Map<String, ServiceAccount> serviceAccounts = new ConcurrentHashMap<>();
    private Map<String, Group> groups = new ConcurrentHashMap<>();
    private Map<String, Role> roles = new ConcurrentHashMap<>();

    private ProtobufDatabase protobufDatabase;

    public Directory() throws InitException {
        try {
            YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
            protobufDatabase = yarch.getProtobufDatabase();
            AccountCollection accountCollection = protobufDatabase.get(ACCOUNT_COLLECTION, AccountCollection.class);
            if (accountCollection != null) {
                accountIdSequence.set(accountCollection.getSeq());
                for (AccountRecord rec : accountCollection.getRecordsList()) {
                    switch (rec.getAccountTypeCase()) {
                    case USERDETAIL:
                        users.put(rec.getName(), new User(rec));
                        break;
                    case SERVICEDETAIL:
                        serviceAccounts.put(rec.getName(), new ServiceAccount(rec));
                        break;
                    case ACCOUNTTYPE_NOT_SET:
                        throw new IllegalStateException("Unexpected account type");
                    }
                }
            }
            GroupCollection groupCollection = protobufDatabase.get(GROUP_COLLECTION, GroupCollection.class);
            if (groupCollection != null) {
                groupIdSequence.set(groupCollection.getSeq());
                for (GroupRecord rec : groupCollection.getRecordsList()) {
                    groups.put(rec.getName(), new Group(rec));
                }
            }
        } catch (YarchException | IOException e) {
            throw new InitException(e);
        }

        loadRoles();
    }

    public synchronized void addUser(User user) throws IOException {
        String username = user.getName();
        if (users.containsKey(username) || serviceAccounts.containsKey(username)) {
            throw new IllegalArgumentException("Name '" + username + "' is already taken");
        }
        if (username.isEmpty() || username.contains(":")) {
            throw new IllegalArgumentException("Invalid username '" + username + "'");
        }
        int id = accountIdSequence.incrementAndGet();
        user.setId(id);
        for (Role role : roles.values()) {
            if (role.isDefaultRole()) {
                user.addRole(role.getName());
            }
        }
        log.info("Saving new user {}", user);
        updateUserProperties(user);
    }

    public synchronized void updateUserProperties(User user) throws IOException {

        // Recalculate effective privileges
        user.clearDirectoryPrivileges();
        for (String roleName : user.getRoles()) {
            Role role = getRole(roleName);
            if (role != null) {
                for (SystemPrivilege privilege : role.getSystemPrivileges()) {
                    user.addSystemPrivilege(privilege, false);
                }
                for (ObjectPrivilege privilege : role.getObjectPrivileges()) {
                    user.addObjectPrivilege(privilege, false);
                }
            }
        }

        users.put(user.getName(), user);
        persistChanges();
    }

    public synchronized void deleteUser(User user) throws IOException {
        String username = user.getName();
        log.info("Removing user {}", user);
        for (Group group : groups.values()) {
            group.removeMember(user.getId());
        }
        users.remove(username);
        persistChanges();
    }

    public synchronized void addGroup(Group group) throws IOException {
        String groupName = group.getName();
        if (groups.containsKey(groupName)) {
            throw new IllegalArgumentException("Group '" + groupName + "' already exists");
        }
        int id = groupIdSequence.incrementAndGet();
        group.setId(id);
        log.info("Saving new group {}", group);
        groups.put(group.getName(), group);
        persistChanges();
    }

    public synchronized void renameGroup(String from, String to) throws IOException {
        if (groups.containsKey(to)) {
            throw new IllegalArgumentException("Group '" + to + "' already exists");
        }
        Group group = groups.get(from);
        group.setName(to);
        groups.remove(from);
        groups.put(to, group);
        persistChanges();
    }

    public synchronized void updateGroupProperties(Group group) throws IOException {
        groups.put(group.getName(), group);
        persistChanges();
    }

    public synchronized void deleteGroup(Group group) throws IOException {
        groups.remove(group.getName());
        persistChanges();
    }

    /**
     * Creates a new service account. The service account is assumed to represent one application only, for which
     * automatically generated credentials are returned. These may be used to identify as that application, for example
     * to generate access tokens.
     */
    public synchronized ApplicationCredentials addServiceAccount(ServiceAccount service) throws IOException {
        String serviceName = service.getName();
        if (users.containsKey(serviceName) || serviceAccounts.containsKey(serviceName)) {
            throw new IllegalArgumentException("Name '" + serviceName + "' is already taken");
        }
        int id = accountIdSequence.incrementAndGet();
        service.setId(id);

        String applicationId = UUID.randomUUID().toString();
        String applicationSecret = CryptoUtils.generateRandomPassword(10);
        String applicationHash = hasher.createHash(applicationSecret.toCharArray());
        service.setApplicationId(applicationId);
        service.setApplicationHash(applicationHash);

        log.info("Saving new service account {}", service);
        serviceAccounts.put(service.getName(), service);
        persistChanges();

        return new ApplicationCredentials(applicationId, applicationSecret /* not the hash */);
    }

    public synchronized void deleteServiceAccount(String name) throws IOException {
        serviceAccounts.remove(name);
        persistChanges();
    }

    public synchronized void updateApplicationProperties(ServiceAccount service) throws IOException {
        serviceAccounts.put(service.getName(), service);
        persistChanges();
    }

    @SuppressWarnings("unchecked")
    private void loadRoles() {
        if (YConfiguration.isDefined("roles")) {
            YConfiguration yconf = YConfiguration.getConfiguration("roles");
            Map<String, Object> roleConfig = yconf.getRoot();
            for (String roleName : roleConfig.keySet()) {
                Role role = new Role(roleName);
                if (!YConfiguration.isNull(roleConfig, roleName)) {
                    Map<String, Object> roleDef = YConfiguration.getMap(roleConfig, roleName);
                    roleDef.forEach((typeString, objects) -> {
                        if (typeString.equals("System")) {
                            for (String name : (List<String>) objects) {
                                role.addSystemPrivilege(new SystemPrivilege(name));
                            }
                        } else if (typeString.equals("default")) {
                            role.setDefaultRole((Boolean) objects);
                        } else {
                            ObjectPrivilegeType type = new ObjectPrivilegeType(typeString);
                            for (String object : (List<String>) objects) {
                                role.addObjectPrivilege(new ObjectPrivilege(type, object));
                            }
                        }
                    });
                }
                roles.put(role.getName(), role);
            }
        }
    }

    private synchronized void persistChanges() throws IOException {
        AccountCollection.Builder accountsb = AccountCollection.newBuilder();
        accountsb.setSeq(accountIdSequence.get());
        for (User user : users.values()) {
            accountsb.addRecords(user.toRecord());
        }
        for (ServiceAccount service : serviceAccounts.values()) {
            accountsb.addRecords(service.toRecord());
        }
        GroupCollection.Builder groupsb = GroupCollection.newBuilder();
        groupsb.setSeq(groupIdSequence.get());
        for (Group group : groups.values()) {
            groupsb.addRecords(group.toRecord());
        }
        protobufDatabase.save(ACCOUNT_COLLECTION, accountsb.build());
        protobufDatabase.save(GROUP_COLLECTION, groupsb.build());
    }

    /**
     * Validates the provided password against the stored password hash of a user.
     * 
     * @return true if the password is correct, false otherwise
     */
    public boolean validateUserPassword(String username, char[] password) {
        User user = users.get(username);
        if (user != null && user.getHash() != null) {
            return hasher.validatePassword(password, user.getHash());
        } else {
            return false;
        }
    }

    /**
     * Validates the provided password against the stored password hash of an application.
     * <p>
     * Currently this is only functional for service accounts (which map to one and only one application), but some day
     * we may also want to support user applications.
     */
    public boolean validateApplicationPassword(String applicationId, char[] password) {
        Account account = getAccountForApplication(applicationId);
        if (account == null) {
            throw new IllegalArgumentException("No such application");
        }

        if (account instanceof ServiceAccount) {
            return hasher.validatePassword(password, ((ServiceAccount) account).getApplicationHash());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public void changePassword(User user, char[] password) throws IOException {
        if (user.isExternallyManaged()) {
            throw new IllegalArgumentException("The identity of this user is not managed by Yamcs");
        }
        if (!validateUserPassword(user.getName(), password)) {
            String hash = hasher.createHash(password);
            user.setHash(hash);
            persistChanges();
        }
    }

    public Account getAccount(String name) {
        User user = getUser(name);
        return user != null ? user : getServiceAccount(name);
    }

    public User getUser(int id) {
        return users.values().stream()
                .filter(u -> u.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public User getUser(String username) {
        return users.get(username);
    }

    public List<User> getUsers() {
        return new ArrayList<>(users.values());
    }

    public Account getAccountForApplication(String applicationId) {
        for (ServiceAccount service : serviceAccounts.values()) {
            if (service.getApplicationId().equals(applicationId)) {
                return service;
            }
        }
        return null;
    }

    public Group getGroup(String name) {
        return groups.get(name);
    }

    public List<Group> getGroups() {
        return new ArrayList<>(groups.values());
    }

    public List<Group> getGroups(User user) {
        return groups.values().stream()
                .filter(g -> g.hasMember(user.getId()))
                .collect(Collectors.toList());
    }

    public ServiceAccount getServiceAccount(String name) {
        return serviceAccounts.get(name);
    }

    public List<ServiceAccount> getServiceAccounts() {
        return new ArrayList<>(serviceAccounts.values());
    }

    public List<Role> getRoles() {
        return new ArrayList<>(roles.values());
    }

    public Role getRole(String name) {
        return roles.get(name);
    }
}
