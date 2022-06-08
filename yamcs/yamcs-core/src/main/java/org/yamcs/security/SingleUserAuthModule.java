package org.yamcs.security;

import java.util.Arrays;
import java.util.List;

import org.yamcs.Experimental;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

/**
 * An AuthModule that enforces a login of one fixed user account
 */
@Experimental
public class SingleUserAuthModule implements AuthModule {

    private AuthenticationInfo authenticationInfo;
    private AuthorizationInfo authorizationInfo;

    private PasswordHasher passwordHasher;
    private String expectedHash;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("username", OptionType.STRING).withRequired(true);
        spec.addOption("password", OptionType.STRING).withRequired(true).withSecret(true);
        spec.addOption("name", OptionType.STRING);
        spec.addOption("email", OptionType.STRING);
        spec.addOption("superuser", OptionType.BOOLEAN);
        spec.addOption("privileges", OptionType.ANY);
        spec.addOption("hasher", OptionType.STRING);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        String username = args.getString("username");
        authenticationInfo = new AuthenticationInfo(this, username);

        expectedHash = args.getString("password");

        String name = args.getString("name", username);
        authenticationInfo.setDisplayName(name);

        String email = args.getString("email", null);
        authenticationInfo.setEmail(email);

        authorizationInfo = new AuthorizationInfo();
        if (args.getBoolean("superuser")) {
            authorizationInfo.grantSuperuser();
        }
        if (args.containsKey("privileges")) {
            YConfiguration privilegeConfigs = args.getConfig("privileges");
            for (String privilegeName : privilegeConfigs.getKeys()) {
                List<String> objects = privilegeConfigs.getList(privilegeName);
                if (privilegeName.equals("System")) {
                    for (String object : objects) {
                        authorizationInfo.addSystemPrivilege(new SystemPrivilege(object));
                    }
                } else {
                    ObjectPrivilegeType type = new ObjectPrivilegeType(privilegeName);
                    for (String object : objects) {
                        authorizationInfo.addObjectPrivilege(new ObjectPrivilege(type, object));
                    }
                }
            }
        }

        if (args.containsKey("hasher")) {
            String className = args.getString("hasher");
            passwordHasher = YObjectLoader.loadObject(className);
        }
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            String username = ((UsernamePasswordToken) token).getPrincipal();
            char[] password = ((UsernamePasswordToken) token).getPassword();

            if (!username.equals(authenticationInfo.getUsername())) {
                return null;
            }

            if (passwordHasher != null) {
                if (!passwordHasher.validatePassword(password, expectedHash)) {
                    throw new AuthenticationException("Password does not match");
                }
            } else {
                if (!Arrays.equals(expectedHash.toCharArray(), password)) {
                    throw new AuthenticationException("Password does not match");
                }
            }
            return authenticationInfo;
        } else {
            return null;
        }
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        String incomingUsername = authenticationInfo.getUsername();
        if (incomingUsername.equals(this.authenticationInfo.getUsername())) {
            return authorizationInfo;
        } else {
            return new AuthorizationInfo();
        }
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return this.authenticationInfo.equals(authenticationInfo);
    }
}
