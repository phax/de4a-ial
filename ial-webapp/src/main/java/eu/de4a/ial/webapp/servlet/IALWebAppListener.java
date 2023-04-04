package eu.de4a.ial.webapp.servlet;

import javax.annotation.Nonnull;
import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.debug.GlobalDebug;
import com.helger.commons.exception.InitializationException;
import com.helger.commons.string.StringHelper;
import com.helger.photon.api.APIDescriptor;
import com.helger.photon.api.APIPath;
import com.helger.photon.api.IAPIExceptionMapper;
import com.helger.photon.api.IAPIRegistry;
import com.helger.photon.audit.AuditHelper;
import com.helger.photon.audit.LoggingAuditor;
import com.helger.photon.core.servlet.WebAppListener;
import com.helger.photon.security.login.LoggedInUserManager;
import com.helger.xservlet.requesttrack.RequestTrackerSettings;

import eu.de4a.ial.webapp.api.ApiClearSmpClientCache;
import eu.de4a.ial.webapp.api.ApiGetGetAllDOs;
import eu.de4a.ial.webapp.api.IALRestExceptionMapper;
import eu.de4a.ial.webapp.config.IALConfig;

/**
 * Global startup etc. listener. Initializes everything.
 *
 * @author Philip Helger
 */
public class IALWebAppListener extends WebAppListener
{
  private static final Logger LOGGER = LoggerFactory.getLogger (IALWebAppListener.class);

  public IALWebAppListener ()
  {
    setHandleStatisticsOnEnd (false);
  }

  @Override
  protected String getDataPath (@Nonnull final ServletContext aSC)
  {
    String ret = IALConfig.WebApp.getDataPath ();
    if (ret == null)
    {
      // Fall back to servlet context path
      ret = super.getDataPath (aSC);
    }
    return ret;
  }

  @Override
  protected String getServletContextPath (final ServletContext aSC)
  {
    try
    {
      return super.getServletContextPath (aSC);
    }
    catch (final IllegalStateException ex)
    {
      // E.g. "Unpack WAR files" in Tomcat is disabled
      return getDataPath (aSC);
    }
  }

  @Override
  protected void initGlobalSettings ()
  {
    GlobalDebug.setDebugModeDirect (IALConfig.Global.isGlobalDebug ());
    GlobalDebug.setProductionModeDirect (IALConfig.Global.isGlobalProduction ());

    final String sDirectoryBaseURL = IALConfig.Directory.getBaseURL ();
    if (StringHelper.hasNoText (sDirectoryBaseURL))
      throw new InitializationException ("The Directory base URL configuration is missing");
    LOGGER.info ("Using '" + sDirectoryBaseURL + "' as the Directory base URL");

    RequestTrackerSettings.setLongRunningRequestsCheckEnabled (false);
    RequestTrackerSettings.setParallelRunningRequestsCheckEnabled (false);
  }

  @Override
  protected void afterContextInitialized (final ServletContext aSC)
  {
    // Don't write audit logs
    AuditHelper.setAuditor (new LoggingAuditor (LoggedInUserManager.getInstance ()));
  }

  @Override
  protected void initAPI (@Nonnull final IAPIRegistry aAPIRegistry)
  {
    final IAPIExceptionMapper aExceptionMapper = new IALRestExceptionMapper ();
    aAPIRegistry.registerAPI (new APIDescriptor (APIPath.get ("/provision/{canonicalObjectTypeIDs}"),
                                                 new ApiGetGetAllDOs (false)).setExceptionMapper (aExceptionMapper));
    aAPIRegistry.registerAPI (new APIDescriptor (APIPath.get ("/provision/{canonicalObjectTypeIDs}/{atuCode}"),
                                                 new ApiGetGetAllDOs (true)).setExceptionMapper (aExceptionMapper));
    aAPIRegistry.registerAPI (new APIDescriptor (APIPath.get ("/internal/clear-smpclient-cache"),
                                                 new ApiClearSmpClientCache ()).setExceptionMapper (aExceptionMapper));
  }

  @Override
  protected void beforeContextDestroyed (final ServletContext aSC)
  {}
}
