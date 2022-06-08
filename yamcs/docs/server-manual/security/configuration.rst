Configuration
=============

The security system is configured in the file ``etc/security.yaml``. Example:

.. code-block:: yaml

    authModules:
      - class: org.yamcs.security.LdapAuthModule
        args:
           ...

This requires that all login attempts are validated against an external LDAP server.

These options are supported:

authModules (list of maps)
  List of AuthModules that participate in the login process. Each AuthModule may support custom configuration options which can be defined under the ``args`` key. If empty only the internal Yamcs directory is used as a source of users and roles.

blockUnknownUsers (boolean)
    Use this if you need fine control over who can access Yamcs. Successful login attempts from users that were not yet known by Yamcs will be blocked by default. A privileged user may unblock them. The typical use case is when Yamcs uses an external identity provider that allows more users than really should be allowed access to Yamcs.

    Default: false

enabled (boolean)
    Control whether authentication is enforced.
    
    Default: ``true`` if ``security.yaml`` is present, ``false`` otherwise.

guest (map)
    Overrides the user properties of the guest user. This user is used for all access when authentication is not being enforced.
