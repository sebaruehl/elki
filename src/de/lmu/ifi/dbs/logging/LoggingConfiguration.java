package de.lmu.ifi.dbs.logging;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Facility for configuration of logging.
 *
 * @author Arthur Zimek (<a href="mailto:zimek@dbs.ifi.lmu.de">zimek@dbs.ifi.lmu.de</a>)
 */
public class LoggingConfiguration
{
    /**
     * Whether the LoggingConfiguration is still changeable.
     */
    private static boolean configurationChangeable = true;
    
    /**
     * General debug flag.
     */
    public static final boolean DEBUG = false;
    
    /**
     * Configuration code for command line interface.
     */
    public static final int CLI = 0;

    /**
     * General logger level.
     */
    private Level loggerLevel;

    /**
     * The debug filter (can be maintained).
     */
    private DebugFilter debugFilter;

    /**
     * Provides a logging configuration with
     * {@link #debugFilter debugFilter}
     * set to {@link Level#ALL ALL}.
     * Per default, the general {@link #loggerLevel loggerLevel}
     * is set to {@link Level#ALL ALL}.
     */
    public LoggingConfiguration()
    {
        loggerLevel = Level.ALL;
        debugFilter = new DebugFilter(Level.ALL);
    }

    /**
     * Configures the specified logger
     * according to the specified configuration code.
     *
     * @param logger        the logger to configure
     * @param configuration the configuration code
     */
    public void configure(Logger logger, int configuration)
    {
        switch(configuration)
        {
        case CLI:
            configure(logger, consoleHandlers());
            break;
        default:
            throw new IllegalArgumentException("unknown configuration code " + configuration);
        }
    }

    /**
     * Configures the given logger.
     * Removes all handlers currently associated
     * with the logger and associates the given handlers
     * instead. Finally, sets the level of the logger to the
     * currently set {@link #loggerLevel loggerLevel}.
     *
     * @param logger  the logger to configure
     * @param handler the handlers to associate with the logger
     */
    public void configure(Logger logger, Handler[] handler)
    {
        Handler[] oldHandler = logger.getHandlers();
        for(Handler h : oldHandler)
        {
            logger.removeHandler(h);
        }
        for(Handler h : handler)
        {
            logger.addHandler(h);
        }
        logger.setLevel(loggerLevel);
    }

    /**
     * Sets the {@link #loggerLevel loggerLevel}
     * to the specified level.
     *
     * @param level the new {@link #loggerLevel loggerLevel}
     */
    public void setLoggerLevel(Level level)
    {
        this.loggerLevel = level;
    }

    /**
     * Sets the level of {@link #debugFilter debugFilter}.
     *
     * @param level the new level of {@link #debugFilter debugFilter}
     */
    public void setDebugLevel(Level level)
    {
        debugFilter.setLevel(level);
    }

    /**
     * Provides the standard handlers for
     * command line interface
     * configuration.
     * <ul>
     * <li>Debugging:
     * Debug messages are printed immediately to <code>System.err</code>.
     * </li>
     * <li>Verbose messages for regular user information
     * are printed immediately to <code>System.out</code>.
     * </li>
     * <li>Warning messages for user information
     * are printed immediately to <code>System.err</code>.
     * </li>
     * <li>Exception messages are printed immediately to <code>System.err</code>.
     * </li>
     * </ul>
     *
     * @return an array of four CLI handlers
     */
    protected Handler[] consoleHandlers()
    {
        // TODO: perhaps more suitable formatters?
        Handler debugHandler = new ImmediateFlushHandler(new MaskingOutputStream(System.err), new SimpleFormatter(), debugFilter);
        Handler verboseHandler = new ImmediateFlushHandler(new MaskingOutputStream(System.out), new MessageFormatter(), new InfoFilter());
        Handler warningHandler = new ImmediateFlushHandler(new MaskingOutputStream(System.err), new SimpleFormatter(), new WarningFilter());
        Handler exceptionHandler = new ImmediateFlushHandler(new MaskingOutputStream(System.err), new ExceptionFormatter(), new ExceptionFilter());
        Handler[] consoleHandlers = { debugHandler, verboseHandler, warningHandler, exceptionHandler };
        return consoleHandlers;
    }
    
    /**
     * Configures the root logger according to the
     * specified configuration code.
     * 
     * The configuration will only be set, if
     * {@link #configurationChangeable configurationChangeable}
     * is true.
     * After this method has been called, the logging configuration
     * cannot be changed again by a method of this class.
     *
     * @param configuration the configuration code
     */
    public static void configureRootFinally(int configuration)
    {
        if(configurationChangeable)
        {
            LoggingConfiguration loggingConfiguration = new LoggingConfiguration();
            loggingConfiguration.configure(Logger.getLogger(""), configuration);
        }
        else
        {
            Logger.getLogger(LoggingConfiguration.class.getName()).warning("logger configuration cannot be changed");
        }
        configurationChangeable = false;
    }

    /**
     * Configures the root logger according to the
     * specified configuration code.
     * 
     * The configuration will only be set, if
     * {@link #configurationChangeable configurationChangeable}
     * is true.
     *
     * @param configuration the configuration code
     */
    public static void configureRoot(int configuration)
    {
        if(configurationChangeable)
        {
            LoggingConfiguration loggingConfiguration = new LoggingConfiguration();
            loggingConfiguration.configure(Logger.getLogger(""), configuration);
        }
        else
        {
            Logger.getLogger(LoggingConfiguration.class.getName()).warning("logger configuration cannot be changed");
        }
    }
    
    /**
     * Returns whether the LoggingConfiguration is still changeable.
     * 
     * 
     * @return a boolean indicating whether
     *  the LoggingConfiguration is still changeable
     */
    public static boolean isChangeable()
    {
        return configurationChangeable;
    }
}
