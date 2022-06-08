package org.yamcs;

/**
 * Exception thrown if there is an error in configuration.
 *
 * If the config problem is related to a file, the confPath is the something like
 *  filename.yaml: key1-&gt;subkey2-&gt;subkey3...
 *
 * @author nm
 *
 */
public class ConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    String confpath;
    
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(Exception e) {
        super(e);
    }

    public ConfigurationException(String confpath, String message) {
        super(message);
        this.confpath=confpath;
    }

    public ConfigurationException(String message, Throwable t) {
        super(message, t);
    }

    public ConfigurationException(String confpath, String message, Throwable t) {
        super(message, t);
        this.confpath=confpath;
    }

    public ConfigurationException(YConfiguration config, String message) {
        super(message);
    }

    @Override
    public String toString() {
        String message = getLocalizedMessage();
        if(confpath!=null) {
            return "Configuration error in '"+confpath+"': "+message;
        } else {
            return message;
        }
    }
}
