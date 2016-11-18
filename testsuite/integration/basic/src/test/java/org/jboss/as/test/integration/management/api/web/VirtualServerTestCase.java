/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.management.api.web;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.logging.Logger;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.management.base.ContainerResourceMgmtTestBase;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.management.util.SimpleServlet;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(Arquillian.class)
@RunAsClient
//todo this test could probably be done in manual mode test with wildfly runner, also could be merged into VirtualHostTestCase
public class VirtualServerTestCase extends ContainerResourceMgmtTestBase {

    @ArquillianResource
    URL url;

    private static String defaultHost;
    private static String virtualHost;
    private static final Logger log = Logger.getLogger(VirtualServerTestCase.class.getName());

    @Deployment(order=1)
    public static Archive<?> getDeployment() {
        JavaArchive ja = ShrinkWrap.create(JavaArchive.class, "dummy.jar");
        ja.addClass(VirtualServerTestCase.class);
        return ja;
    }

    @Deployment(managed=false, name="vsdeployment", order=2)
    public static Archive<?> getVDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "vsDeployment.war");
        war.addClass(SimpleServlet.class);
        war.addAsWebResource(new StringAsset("Virtual Server Deployment"), "index.html");
        war.addAsWebResource(new StringAsset("Rewrite Test"), "/rewritten/index.html");
        war.addAsWebInfResource(new StringAsset("<jboss-web><virtual-host>test</virtual-host></jboss-web>"), "jboss-web.xml");
        return war;
    }

    @After
    public void tearDown() throws Exception {
        if (virtualServerExists()) {
            removeVirtualServer(true);
        }
    }

    @Test
    public void testDefaultVirtualServer() throws IOException, MgmtOperationException {

        // get default VS
        ModelNode result = executeOperation(createOpNode("subsystem=undertow/server=default-server/host=default-host", "read-resource"));

        // check VS
        assertTrue(result.get("alias").isDefined());
        assertTrue(result.get("default-web-module").asString().equals("ROOT.war"));
    }

    @Test
    public void addRemoveVirtualServer(@ArquillianResource Deployer deployer) throws Exception {

        if (! resolveHosts()) {
            log.trace("Unable to resolve alternate server host name.");
            return;
        }
        addVirtualServer();

        // deploy to virtual server
        deployer.deploy("vsdeployment");

        // check the deployment is available on and only on virtual server
        URL vURL = new URL(url.getProtocol(), virtualHost, url.getPort(), "/vsDeployment/index.html");
        String response = HttpRequest.get(vURL.toString(), 10, TimeUnit.SECONDS);
        assertTrue("Invalid response: " + response, response.indexOf("Virtual Server Deployment") >=0);

        URL dURL = new URL(url.getProtocol(), defaultHost, url.getPort(), "/vsDeployment/index.html");
        boolean failed = false;
        try {
            response = HttpRequest.get(dURL.toString(), 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            failed = true;
        }
        assertTrue("Deployment also on default server. " , failed);

        // undeploy form virtual server
        deployer.undeploy("vsdeployment");
    }

    @Test
    public void testRuntimeRemoveHostWithFilterRef(@ArquillianResource Deployer deployer) throws Exception {
        if (! resolveHosts()) {
            log.info("Unable to resolve alternate server host name.");
            return;
        }
        addVirtualServer();
        ModelNode addOp = createOpNode("subsystem=undertow/server=default-server/host=test/filter-ref=x-powered-by-header", "add");
        executeOperation(addOp);
        removeVirtualServer(true);
        // vsdeployment should fail because it depends on virtual host that doesn't exist any more
        assertDeploymentFails(deployer, "vsdeployment");
    }

    @Test
    public void testRemoveHostWithFilterRefWithReload(@ArquillianResource Deployer deployer) throws Exception {
        if (! resolveHosts()) {
            log.info("Unable to resolve alternate server host name.");
            return;
        }
        addVirtualServer();
        ModelNode addOp = createOpNode("subsystem=undertow/server=default-server/host=test/filter-ref=x-powered-by-header", "add");
        executeOperation(addOp);
        removeVirtualServer(false);
        ServerReload.executeReloadAndWaitForCompletion(this.getModelControllerClient());
        // vsdeployment should fail because it depends on virtual host that doesn't exist any more
        assertDeploymentFails(deployer, "vsdeployment");
    }

    @Test
    public void testRemoveHostWithSso(@ArquillianResource Deployer deployer) throws Exception {
        if (! resolveHosts()) {
            log.info("Unable to resolve alternate server host name.");
            return;
        }

        addVirtualServer();
        ModelNode addOp = createOpNode("subsystem=undertow/server=default-server/host=test/setting=single-sign-on", "add");
        executeOperation(addOp);

        removeVirtualServer(true);
        // vsdeployment should fail because it depends on virtual host that doesn't exist any more
        assertDeploymentFails(deployer, "vsdeployment");

        addVirtualServer();
        executeOperation(addOp);
    }

    private void assertDeploymentFails(@ArquillianResource Deployer deployer, String deploymentName) {
        try {
            deployer.deploy(deploymentName);
            fail("Deployment should have failed");
        } catch (Exception e) {
            if (e instanceof DeploymentException) {
                // deployment failed as expected - ignore
            } else {
                throw e;
            }
        } finally {
            // need to undeploy failed deployment to clear state in Arquillian
            deployer.undeploy(deploymentName);
        }
    }

    private void addVirtualServer() throws IOException, MgmtOperationException {
        ModelNode addOp = createOpNode("subsystem=undertow/server=default-server/host=test", "add");
        addOp.get("alias").add(virtualHost);
        addOp.get("default-web-module").set("some-test.war");

        ModelNode rewrite = new ModelNode();
        rewrite.get("condition").setEmptyList();
        rewrite.get("pattern").set("toberewritten");
        rewrite.get("substitution").set("rewritten");
        rewrite.get("flags").set("nocase");
        //TODO add support for rewrites!
        //addOp.get("rewrite").add(rewrite);

        executeOperation(addOp);
    }

    private void removeVirtualServer(boolean runtime) throws IOException, MgmtOperationException {
        ModelNode removeOp = createOpNode("subsystem=undertow/server=default-server/host=test", "remove");
        if (runtime) {
            removeOp.get("operation-headers").get("allow-resource-service-restart").set("true");
        }
        executeOperation(removeOp);
    }

    private boolean virtualServerExists() throws IOException, MgmtOperationException {
        ModelNode query = createOpNode("subsystem=undertow/server=default-server", "query");
        final ModelNode response = executeOperation(query, false);
        final List<ModelNode> modelNodes = response.get("result").get("host").asList();
        for (ModelNode modelNode : modelNodes) {
            if (modelNode.has("test")) {
                return true;
            }
        }
        return false;
    }

    private boolean resolveHosts() {
        if (url.getHost().equals("localhost") || (url.getHost().equals("127.0.0.1"))) {
            defaultHost = "localhost";
            virtualHost = "127.0.0.1";
            return true;
        }

        return false;
    }

}
