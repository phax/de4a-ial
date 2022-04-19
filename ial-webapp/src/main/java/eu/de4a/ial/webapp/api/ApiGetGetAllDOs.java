/*
 * Copyright (C) 2022 DE4A, www.de4a.eu
 * Author: philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.de4a.ial.webapp.api;

import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.helger.commons.annotation.Nonempty;
import com.helger.commons.collection.impl.CommonsHashMap;
import com.helger.commons.collection.impl.CommonsLinkedHashSet;
import com.helger.commons.collection.impl.ICommonsMap;
import com.helger.commons.collection.impl.ICommonsOrderedSet;
import com.helger.commons.mime.CMimeType;
import com.helger.commons.string.StringHelper;
import com.helger.commons.timing.StopWatch;
import com.helger.commons.url.SimpleURL;
import com.helger.http.AcceptMimeTypeList;
import com.helger.httpclient.HttpClientManager;
import com.helger.httpclient.HttpClientSettings;
import com.helger.httpclient.response.ResponseHandlerXml;
import com.helger.json.IJsonObject;
import com.helger.json.JsonObject;
import com.helger.json.serialize.JsonWriterSettings;
import com.helger.masterdata.nuts.INutsManager;
import com.helger.masterdata.nuts.NutsManager;
import com.helger.pd.searchapi.PDSearchAPIReader;
import com.helger.pd.searchapi.v1.ResultListType;
import com.helger.photon.api.IAPIDescriptor;
import com.helger.photon.api.IAPIExecutor;
import com.helger.photon.app.PhotonUnifiedResponse;
import com.helger.servlet.request.RequestHelper;
import com.helger.servlet.response.UnifiedResponse;
import com.helger.web.scope.IRequestWebScopeWithoutResponse;
import com.helger.xml.serialize.write.XMLWriterSettings;

import eu.de4a.ial.api.IALMarshaller;
import eu.de4a.ial.api.jaxb.ErrorType;
import eu.de4a.ial.api.jaxb.ResponseLookupRoutingInformationType;
import eu.de4a.ial.webapp.config.IALConfig;

/**
 * Provide the public query API
 *
 * @author Philip Helger
 */
public class ApiGetGetAllDOs implements IAPIExecutor
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ApiGetGetAllDOs.class);

  private final boolean m_bWithATUCode;

  public ApiGetGetAllDOs (final boolean bWithATUCode)
  {
    m_bWithATUCode = bWithATUCode;
  }

  @Nonnull
  private static ErrorType _createError (@Nonnull final String sCode, @Nonnull final String sMsg)
  {
    final ErrorType ret = new ErrorType ();
    ret.setCode (sCode);
    ret.setText (sMsg);
    return ret;
  }

  public final void invokeAPI (@Nonnull final IAPIDescriptor aAPIDescriptor,
                               @Nonnull @Nonempty final String sPath,
                               @Nonnull final Map <String, String> aPathVariables,
                               @Nonnull final IRequestWebScopeWithoutResponse aRequestScope,
                               @Nonnull final UnifiedResponse aUnifiedResponse) throws Exception
  {
    final PhotonUnifiedResponse aPUR = (PhotonUnifiedResponse) aUnifiedResponse;
    aPUR.setJsonWriterSettings (new JsonWriterSettings ().setIndentEnabled (true));
    aPUR.disableCaching ();

    final StopWatch aSW = StopWatch.createdStarted ();

    // Get and check parameters
    final String sCOTIDs = aPathVariables.get ("canonicalObjectTypeIDs");
    final ICommonsOrderedSet <String> aCOTIDs = new CommonsLinkedHashSet <> ();
    StringHelper.explode (',', sCOTIDs, x -> aCOTIDs.add (x.trim ()));
    final String sAtuCode = m_bWithATUCode ? aPathVariables.get ("atuCode") : null;

    if (LOGGER.isInfoEnabled ())
      LOGGER.info ("Querying for " + aCOTIDs + (m_bWithATUCode ? " in ATU code '" + sAtuCode + "'" : ""));

    if (aCOTIDs.isEmpty ())
      throw new IALBadRequestException ("No Canonical Object Type ID was passed", aRequestScope);

    final INutsManager aNutsMgr = NutsManager.INSTANCE_2021;
    if (m_bWithATUCode)
    {
      if (aNutsMgr.isIDValid (sAtuCode))
        LOGGER.info ("The provided ATU code '" + sAtuCode + "' is a NUTS code");
      else
        LOGGER.info ("The provided ATU code '" + sAtuCode + "' seems to be a LAU code");
    }

    // Perform Directory queries
    final ICommonsMap <String, ResultListType> aDirectoryResults = new CommonsHashMap <> ();
    final HttpClientSettings aHCS = new HttpClientSettings ();
    try (final HttpClientManager aHCM = HttpClientManager.create (aHCS))
    {
      for (final String sCOTID : aCOTIDs)
      {
        // Build base URL and fetch all records per HTTP request
        final SimpleURL aBaseURL = new SimpleURL (IALConfig.Directory.getBaseURL () + "/search/1.0/xml");
        // More than 1000 is not allowed
        aBaseURL.add ("rpc", 100);
        aBaseURL.add ("doctype", sCOTID);

        LOGGER.info ("Querying Directory for DocTypeID '" + sCOTID + "'");

        final HttpGet aGet = new HttpGet (aBaseURL.getAsStringWithEncodedParameters ());
        final Document aResponseXML = aHCM.execute (aGet, new ResponseHandlerXml (false));

        // Parse result
        final ResultListType aResultList = PDSearchAPIReader.resultListV1 ().read (aResponseXML);
        if (aResultList != null)
        {
          // Only remember results with matches
          if (aResultList.hasMatchEntries ())
            aDirectoryResults.put (sCOTID, aResultList);
        }
        else
          LOGGER.error ("Failed to parse Directory result as XML");
      }
    }

    LOGGER.info ("Collective Directory results: " + aDirectoryResults);

    final ResponseLookupRoutingInformationType aResponse = new ResponseLookupRoutingInformationType ();
    // TODO fill response
    aResponse.addError (_createError ("c1", "Test"));

    final AcceptMimeTypeList aAccept = RequestHelper.getAcceptMimeTypes (aRequestScope.getRequest ());
    if (aAccept.getQualityOfMimeType (CMimeType.APPLICATION_JSON) > aAccept.getQualityOfMimeType (CMimeType.APPLICATION_XML))
    {
      // As JSON
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Rendering response as JSON");

      final IJsonObject aJson = new JsonObject ();
      // TODO fill JSON
      aPUR.json (aJson);
    }
    else
    {
      // As XML
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Rendering response as XML");

      final byte [] aXML = IALMarshaller.idkResponseLookupRoutingInformationMarshaller ().getAsBytes (aResponse);
      if (aXML == null)
        throw new IALInternalErrorException ("Failed to serialize XML response");

      aPUR.setContent (aXML)
          .setCharset (XMLWriterSettings.DEFAULT_XML_CHARSET_OBJ)
          .setMimeType (CMimeType.APPLICATION_XML);
    }

    aSW.stop ();

    LOGGER.info ("Successfully finalized querying Directory after " + aSW.getMillis () + "ms");
  }
}
