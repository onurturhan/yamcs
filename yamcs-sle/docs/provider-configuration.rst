Provider Configuration
======================

SLE Providers are configured in ``etc/sle.yaml``. Data link configuration in yamcs.instance.yaml refer to one of the providers defined in this file.

An example of provider configuration is here below:

.. code-block:: yaml

    CommonSettings: &CommonSettings
        hashAlgorithm: "SHA-1"
        authLevel: BIND
        versionNumber: 4
        myUsername: "user01"
        myPassword:  01020304ABCD
        initiatorId: "user01"
        responderPortId: "ABC"
        heartbeatInterval: 30
        heartbeatDeadFactor: 3

    Providers:
        GS1:
            <<: *CommonSettings
            peerUsername: "prov-user"
            peerPassword: AB0102030405060708090a0b0c0d0e0f
        
            cltu:
                host: 172.16.1.113
                port: 63800
                serviceInstance: "sagr=9999.spack=ABC-PERM.fsl-fg=1.cltu=cltu1"
    
            raf-onlt:
                host: 172.16.1.113
                port: 63900
                serviceInstance: "sagr=9999.spack=ABC-PERM.rsl-fg=1.raf=onlt1"
    
            raf-onlc:
                host: 172.16.1.113
                port: 63901
                responderPortId: "ABC901"
                serviceInstance: "sagr=9999.spack=ABC-PERM.rsl-fg=1.raf=onlc1"
                


The section ``Providers`` contains a map with each SLE provider (e.g. ground station). This contains a section with general settings followed by a specific cltu, raf-ontl, raf-onlc, rcf-ontl, rcf-onlc sections containing connection parameters for the CLTU service, RAF/RCF online timely, respectively RAF/RCF online complete services.

In the example above the ``CommonSettings`` block is included into the GS1 provider block using the yaml node achor and reference feature. This feature (pure yaml functionality, not specific to SLE) allows grouping common settings for multiple ground stations into one definition.


Common Options
--------------

These options are specified at the provider level (directly under ``GS1`` in the example above) as they apply to all services from this provider. All the options which do not have a default, are mandatory. Some options can be specified both at provider and at service level, the service level values overriding the provider level values.


versionNumber (integer)
    2, 3, 4 or 5. This is the version number passed in the BIND call. Note that there is no code to handle specifically any version but the SLE versions 2-5 SLE are belived to be compatible as far as the user (i.e. Yamcs) is concerned; the differences are mostly to do with new options being added in the newer versions. Those options are currently not accessible from the Yamcs SLE link. Default: ``5``

hashAlgorithm  (string)
    | One of ``SHA-1`` or ``SHA-256``.
    | The hashAlgorithm is effectively passed to the `MessageDigest.getInstance(hashAlgorithm) <https://docs.oracle.com/javase/8/docs/api/java/security/MessageDigest.html#getInstance-java.lang.String>`_ method in Java. Default: ``SHA-256``
    
authLevel (string)
    | One of: ``ALL``, ``BIND`` or ``NONE``.    
    | The SLE messages can be optionally authenticated by inserting a ``invoker-credentials`` token in the SLE message. This option specifies which messages sent by Yamcs are authenticated (i.e. contain this token). It also specifies which of the peer messages are expected to be authenticated. Default: ``BIND``
    | **Important:** the SLE security mechanism does not prevent man-in-the-middle attacks and other type of security attacks and therefore it is advisable to protect the transport at TCP level by other means (e.g. VPN).
    | For details: see Chapter 8 Security Aspects of the SLE Forward CLTU Transfer Service in the CCSDS specification.

authenticationDelay
    The authentication tokens received from the provider contain a timestamp which is compared with the local time to verify that the token has been recently generated. This option configures the maximum allowed delta in seconds. Default: 180 (3 minutes)

myUsername (string)
    The username  used to build the credentials token part of ISP1 authentication if the authLevel is ALL or BIND.
    
myPassword (hexadecimal string)
    Used together with the myUsername for the ISP1 authentication.

peerUsername (string)
    The username of the peer. It is used to verify the peer credential tokens. Depending on the ``authLevel``, all messages, the bind return or no message will be authenticated.

peerPassword (hexadecimal string)
    Used together with the ``peerUsername`` for verifying the peer suplied credential token.

initiatorId (string)
    This property species the value of the ``initiator-identifier`` parameter passed as part of the BIND SLE message. 

responderPortId (string)
    This property species the value of the ``responder-port-identifier`` parameter passed as part of the BIND SLE message.
 
heartbeatInterval (integer number of seconds)
    This property specifies the value proposed to the peer for the ISP1 heartbeat interval. The SLE provider will check this against its accepted range and it will close the connection if the value sent by Yamcs is not within the accepted range. Default: ``30`` (seconds)
    
heartbeatDeadFactor (integer)
    Defines the number of heartbeat intervals without a message from peer after which a TCP connection is considered dead and is closed. Default: ``3``

reconnectionIntervalSec (integer)
    How soon should reconnect in case the connection to the SLE provider is lost; If negative, do not reconnect.   Default: ``30`` (seconds). The setting is not applicable for the offline TM link, it is set internally to -1.
    
    
maxShutdownDelaySec (integer)
    When the SLE connection is stopped, a graceful shutdown is performed by sending STOP and UNBIND messages. This option defines the maximum time for the graceful shutdown to take place. If the shutdown is not finished in this time, the connection will be closed nevertheless. Default: ``5`` (seconds)
    
unbindReason (string)
    The reason to send in the UNBIND call. One of END, SUSPEND or OTHER. Default: ``SUSPEND``



Service-specific Options
------------------------

These options are specified as part of each SLE service (e.g. under ``cltu`` in the example above). The options which have the same names with those defined above in the provider section, will override those for the specific service.


host (string)
    The hostname or IP address to connect to.

port (integer)
    The port number to connect to.
        
serviceInstance (string)
    Used (after transformation to binary form) as ``service-instance-identifier`` in the SLE BIND call to identify the service requested to the provider. It is a series of ``sia=value`` separated by dots where sia is a service identifier attribute.
    
    Ask your SLE provider for the value of this parameter. 

tmlMaxLength (integer)
    The maximum length in bytes of the Transport Mapping Layer (TML) messages. These are the messages defined in the ISP1 standard for transporting SLE data. If a message larger than this length is received, the connection is closed.
    
    On the ESA SLE provider this is configured by the ``transfer-buffer-size`` parameter which sets the number of frames which can be transferred in one message. The tmlMaxLength should be set to accomodate that number of frames taking into account the frame size and some 70 bytes overhead per frame.
    Default: ``307200`` (300KB)
    
responderPortId (string)
    Overrides the setting set at the provider level, see above.
    
reconnectionIntervalSec (integer)
    Overrides the setting set at the provider level, see above.
    
maxShutdownDelaySec (integer)
    Overrides the setting set at the provider level, see above.
    
unbindReason (string)
    Overrides the setting set at the provider level, see above.
        
