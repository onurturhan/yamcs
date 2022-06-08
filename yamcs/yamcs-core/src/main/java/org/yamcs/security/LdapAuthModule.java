package org.yamcs.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class LdapAuthModule implements AuthModule {

    private Log log = new Log(LdapAuthModule.class);

    private boolean tls;
    private String providerUrl;
    private Hashtable<String, String> yamcsEnv;

    private String userBase;
    private String nameAttribute;
    private String[] displayNameAttributes;
    private String[] emailAttributes;
    private String[] searchAttributes;

    private Cache<String, LdapUserInfo> infoCache = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    @Override
    public Spec getSpec() {
        Spec attributesSpec = new Spec();
        attributesSpec.addOption("name", OptionType.STRING)
                .withDefault("uid");
        attributesSpec.addOption("email", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withDefault(Arrays.asList("mail", "email", "userPrincipalName"));
        attributesSpec.addOption("displayName", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withDefault("cn");

        Spec spec = new Spec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER);
        spec.addOption("user", OptionType.STRING);
        spec.addOption("password", OptionType.STRING).withSecret(true);
        spec.addOption("tls", OptionType.BOOLEAN);
        spec.addOption("userBase", OptionType.STRING).withRequired(true);
        spec.addOption("attributes", OptionType.MAP).withSpec(attributesSpec)
                .withApplySpecDefaults(true);
        spec.requireTogether("user", "password");
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        String host = args.getString("host");

        tls = args.getBoolean("tls", false);
        if (tls) {
            int port = args.getInt("port", 636);
            providerUrl = String.format("ldaps://%s:%s", host, port);
        } else {
            int port = args.getInt("port", 389);
            providerUrl = String.format("ldap://%s:%s", host, port);
        }

        userBase = args.getString("userBase");

        YConfiguration attributesArgs = args.getConfig("attributes");
        nameAttribute = attributesArgs.getString("name");

        displayNameAttributes = attributesArgs.getList("displayName").toArray(new String[0]);
        emailAttributes = attributesArgs.getList("email").toArray(new String[0]);

        List<String> concat = new ArrayList<>();
        concat.add(nameAttribute);
        concat.addAll(attributesArgs.getList("displayName"));
        concat.addAll(attributesArgs.getList("email"));
        searchAttributes = concat.toArray(new String[0]);

        yamcsEnv = new Hashtable<>();
        yamcsEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        yamcsEnv.put(Context.PROVIDER_URL, providerUrl);
        yamcsEnv.put("com.sun.jndi.ldap.connect.pool", "true");
        yamcsEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
        if (args.containsKey("user")) {
            yamcsEnv.put(Context.SECURITY_PRINCIPAL, args.getString("user"));
        }
        if (args.containsKey("password")) {
            yamcsEnv.put(Context.SECURITY_CREDENTIALS, args.getString("password"));
        }
        if (tls) {
            yamcsEnv.put(Context.SECURITY_PROTOCOL, "ssl");
        }
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            String username = ((UsernamePasswordToken) token).getPrincipal();
            char[] password = ((UsernamePasswordToken) token).getPassword();

            LdapUserInfo info;
            try {
                info = searchUserInfo(username);
            } catch (NamingException e) {
                log.warn("Failed to search LDAP for user {}", username, e);
                return null;
            }

            if (info == null) {
                return null;
            }

            bindUser(info.dn, password);
            AuthenticationInfo authenticationInfo = new AuthenticationInfo(this, info.uid);
            authenticationInfo.addExternalIdentity(getClass().getName(), info.dn);
            authenticationInfo.setDisplayName(info.cn);
            authenticationInfo.setEmail(info.email);
            return authenticationInfo;
        } else {
            return null;
        }
    }

    @Override
    public void authenticationSucceeded(AuthenticationInfo authenticationInfo) {
        AuthModule authenticator = authenticationInfo.getAuthenticator();
        if (authenticator instanceof KerberosAuthModule || authenticator instanceof SpnegoAuthModule) {
            // Note to future self: If we ever want to support multiple LDAP and
            // kerberos modules, then it may become useful to compare the user dn
            // with the kerberos realm before querying LDAP.
            String username = authenticationInfo.getUsername();
            try {
                LdapUserInfo info = searchUserInfo(username);
                if(info == null) {
                    log.warn("User {} not found in LDAP", username);
                } else {
                    authenticationInfo.addExternalIdentity(getClass().getName(), info.dn);
                    authenticationInfo.setDisplayName(info.cn);
                    authenticationInfo.setEmail(info.email);
                }
            } catch (NamingException e) {
                log.warn("Failed to search LDAP for user {}", username, e);
            }
        }
    }

    private LdapUserInfo searchUserInfo(String username) throws NamingException {
        LdapUserInfo info = infoCache.getIfPresent(username);
        if (info != null) {
            return info;
        }

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(yamcsEnv);
            SearchControls controls = new SearchControls();
            controls.setReturningAttributes(searchAttributes);
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            String filter = nameAttribute + "=" + username;
            SearchResult result = getSingleResult(ctx, userBase, filter, controls);
            if (result == null) {
                return null;
            }
            info = new LdapUserInfo();
            // Use the uid from LDAP, just to prevent case sensitivity issues.
            info.uid = (String) result.getAttributes().get(nameAttribute).get();
            info.dn = result.getNameInNamespace();
            info.cn = findAttribute(result, displayNameAttributes);
            info.email = findAttribute(result, emailAttributes);

            infoCache.put(username, info);
            return info;
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private void bindUser(String dn, char[] password) throws AuthenticationException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, providerUrl);
        env.put("com.sun.jndi.ldap.connect.pool", "true");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, dn);
        env.put(Context.SECURITY_CREDENTIALS, new String(password));
        if (tls) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }
        try {
            DirContext ctx = new InitialDirContext(env);
            ctx.close();
        } catch (javax.naming.AuthenticationException e) {
            log.warn("Bind failed for dn '{}'", dn, e);
            throw new AuthenticationException("Invalid password");
        } catch (NamingException e) {
            throw new AuthenticationException(e);
        }
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) {
        return new AuthorizationInfo();
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return true;
    }

    private SearchResult getSingleResult(DirContext ctx, String searchBase, String filter,
            SearchControls controls)
            throws NamingException {
        NamingEnumeration<SearchResult> answer = ctx.search(searchBase, filter, controls);
        if (answer.hasMore()) {
            return answer.next();
        }
        return null;
    }

    private String findAttribute(SearchResult result, String[] possibleNames) throws NamingException {
        for (String attrId : possibleNames) {
            Attribute attr = result.getAttributes().get(attrId);
            if (attr != null) {
                return (String) attr.get();
            }
        }
        return null;
    }

    private static final class LdapUserInfo {
        String uid;
        String dn;
        String cn;
        String email;
    }
}
