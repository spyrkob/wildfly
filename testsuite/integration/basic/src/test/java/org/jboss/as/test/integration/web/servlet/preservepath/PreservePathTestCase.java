/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.web.servlet.preservepath;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.UrlAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunAsClient
@RunWith(Arquillian.class)
public class PreservePathTestCase {

   @ArquillianResource
   private URL url;

   @ArquillianResource
   private ManagementClient managementClient;

   @Before
   public void setUp() throws Exception {

      ModelNode setPreservePathOp = createOpNode("subsystem=undertow/servlet-container=default", UNDEFINE_ATTRIBUTE_OPERATION);

      setPreservePathOp.get("name").set("preserve-path-on-forward");

      CoreUtils.applyUpdate(setPreservePathOp, managementClient.getControllerClient());

      ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
   }

   @After
   public void tearDown() throws Exception {
      ModelNode setPreservePathOp = createOpNode("subsystem=undertow/servlet-container=default", UNDEFINE_ATTRIBUTE_OPERATION);
      setPreservePathOp.get("name").set("preserve-path-on-forward");
      CoreUtils.applyUpdate(setPreservePathOp, managementClient.getControllerClient());
      ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
   }

   @Deployment
   public static WebArchive deployment() {
      WebArchive war = ShrinkWrap.create(WebArchive.class);
      war.addClass(ForwardingServlet.class);
      war.addClass(PreservePathFilter.class);
      war.addClass(HttpServletResponseMock.class);
      war.add(new UrlAsset(PreservePathTestCase.class.getResource("preserve-path.jsp")), "preserve-path.jsp");
      war.addAsWebInfResource(PreservePathTestCase.class.getPackage(), "web.xml", "web.xml");

      return war;
   }

   @Test
   public void testPreservePath() throws Exception {
      runTestPreservePath(true);
   }

   @Test
   public void testDontPreservePath() throws Exception {
      runTestPreservePath(false);
   }

   @Test
   public void testDefaultPreservePath() throws Exception {
      runTestPreservePath(null);
   }

   private void runTestPreservePath(Boolean preservePathOnForward) throws Exception {
      setPreservePathOnForward(preservePathOnForward);

      HttpClient httpclient = HttpClientBuilder.create().build();
      HttpGet httpget = new HttpGet(url.toString() + "/test");
      HttpResponse response = httpclient.execute(httpget);

      final String expectedServletPath;
      final String expectedRequestURL;

      if (preservePathOnForward != null && preservePathOnForward) {
         expectedServletPath = "/test";
         // Note that in the true case, there is an extra slash before test. This might be removed in the future
         expectedRequestURL = url + "/test";
      } else{
         expectedServletPath = "/preserve-path.jsp";
         // Note that in the true case, there is an extra slash before test. This might be removed in the future
         expectedRequestURL = url + "preserve-path.jsp";
      }

      final String expectedRequestURI = new URL(expectedRequestURL).getPath();

      Assert.assertEquals(expectedServletPath, response.getHeaders("servletPath")[0].getValue());
      Assert.assertEquals(expectedRequestURL, response.getHeaders("requestUrl")[0].getValue());
      Assert.assertEquals(expectedRequestURI, response.getHeaders("requestUri")[0].getValue());
   }

   private void setPreservePathOnForward(Boolean preservePathOnForward) throws Exception {
      String opName = preservePathOnForward == null ? UNDEFINE_ATTRIBUTE_OPERATION : WRITE_ATTRIBUTE_OPERATION;
      ModelNode setPreservePathOp = createOpNode("subsystem=undertow/servlet-container=default", opName);

      setPreservePathOp.get("name").set("preserve-path-on-forward");
      if (preservePathOnForward != null) {
         setPreservePathOp.get("value").set(preservePathOnForward);
      }

      CoreUtils.applyUpdate(setPreservePathOp, managementClient.getControllerClient());

      ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
   }
}
