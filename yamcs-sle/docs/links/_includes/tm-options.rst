service (string)
    One of: ``RAF`` or ``RCF``.
    
    Depending on this setting and the ``deliveryMode``, one of the entries raf-ontl, raf-onlc, rcf-ontl, rcf-onlc from sle.yaml will be used.
      
    If the RCF service is used, the request sent with the SLE START includes the triplet (TransferFrameVersionNumber, SpacecraftId, VirtualChannelId).
    
    The values for the TransferFrameVersionNumber and SpacecraftId parameters are normally derived from the frame processing configuration but can be overriden by the options below. Overriding them will most likely result in an invalid configuration.
    
    The value of the VirtualChannelId is by default -1 meaning all VCs are requested but can be restricted to only one VC with the option below.
      
    Default: ``RAF``

rcfTfVersion (integer)
    If ``service`` is set to ``RCF``, this overrides the Transfer Frame Version Number which is otherwise derived from the ``frameType`` parameter part of the frame processing configuration.

rcfSpacecraftId (integer)
    If ``service`` is set to ``RCF``, this overrides the Spacecraft Id which is otherwise the one specified ``spacecraftId`` parameter part of the frame processing configuration.

rcfVcId
    If ``service`` is RCF, this specifies the Virtual Channel requested via RCF. By default it is -1 meaning all Virtual Channels for the defined spacecraft. There is validation that this virtual channel is defined in the ``virtualChannels`` parameter part of the frame processing configuration.

frameQuality
    If ``service`` is RAF, this specifies the frame quality requested in the SLE START invocation. Valid values are ``goodFramesOnly``, ``erredFramesOnly`` or  ``allFrames``. RCF does not support this option, it only delivers good frames.
    Default: ``goodFramesOnly``
