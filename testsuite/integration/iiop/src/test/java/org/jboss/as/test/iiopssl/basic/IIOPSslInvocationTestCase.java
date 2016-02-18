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

package org.jboss.as.test.iiopssl.basic;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.test.shared.FileUtils;
import org.jboss.as.test.shared.PropertiesValueResolver;
import org.jboss.as.test.shared.integration.ejb.security.Util;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

/**
 * A simple IIOP invocation for one AS7 server to another
 */
@RunWith(Arquillian.class)
public class IIOPSslInvocationTestCase {


    @Deployment(name = "server", testable = false)
    @TargetsContainer("iiop-server")
    public static Archive<?> deployment() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "server.jar");
        jar.addClasses(IIOPSslStatelessBean.class, IIOPSslStatelessHome.class, IIOPSslStatelessRemote.class)
                .addAsManifestResource(IIOPSslInvocationTestCase.class.getPackage(), "jboss-ejb3.xml", "jboss-ejb3.xml");
        return jar;
    }

    @Deployment(name = "client", testable = true)
    @TargetsContainer("iiop-client")
    public static Archive<?> clientDeployment() {

        /*
         * The @EJB annotation doesn't allow to specify the address dynamically. So, istead of
         *       @EJB(lookup = "corbaname:iiop:localhost:3628#IIOPTransactionalStatelessBean")
         *       private IIOPTransactionalHome home;
         * we need to do this trick to get the ${node0} sys prop into ejb-jar.xml
         * and have it injected that way.
         */
        String ejbJar = FileUtils.readFile(IIOPSslInvocationTestCase.class, "ejb-jar.xml");

        final Properties properties = new Properties();
        properties.putAll(System.getProperties());
        if (properties.containsKey("node1")) {
            properties.put("node1", NetworkUtils.formatPossibleIpv6Address((String) properties.get("node1")));
        }

        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "client.jar");
        jar.addClasses(ClientEjb.class, IIOPSslStatelessHome.class, IIOPSslStatelessRemote.class, IIOPSslInvocationTestCase.class, Util.class)
                .addAsManifestResource(IIOPSslInvocationTestCase.class.getPackage(), "jboss-ejb3.xml", "jboss-ejb3.xml")
                .addAsManifestResource(new StringAsset(PropertiesValueResolver.replaceProperties(ejbJar, properties)), "ejb-jar.xml");
        return jar;
    }

    @Test
    @OperateOnDeployment("client")
    public void testSuccessfulInvocation() throws IOException, NamingException, LoginException {
        final ClientEjb ejb = client();
        Assert.assertEquals("hello", ejb.getRemoteMessage());
    }

    @Test
    @RunAsClient
    public void testManualLookupUsingSSLPort() throws Exception {
        // need to use jacorb client here because openjdk implementation doesn't support SSLIOP at the moment
        InitialContext ctx = prepareJacorbClientInitialContext();

        final Object ref = ctx.lookup("IIOPSslStatelessBean");

        final IIOPSslStatelessHome iiopSslStatlessHome = (IIOPSslStatelessHome) PortableRemoteObject.narrow(ref, IIOPSslStatelessHome.class);

        final String reply = iiopSslStatlessHome.create().hello();
        Assert.assertEquals("hello", reply);
    }

    private InitialContext prepareJacorbClientInitialContext() throws URISyntaxException, NamingException {
        setUpJacorbProperties();

        Properties p = new Properties();
        p.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.cosnaming.CNCtxFactory");
        p.put(Context.PROVIDER_URL, "corbaloc:ssliop:1.2@localhost:3629/JBoss/Naming/root");

        return new InitialContext(p);
    }

    private void setUpJacorbProperties() throws URISyntaxException {
        System.setProperty("com.sun.CORBA.ORBUseDynamicStub", "true");
        System.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
        System.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
        System.setProperty("jacorb.security.support_ssl","on");
        System.setProperty("jacorb.ssl.socket_factory","org.jacorb.security.ssl.sun_jsse.SSLSocketFactory");
        System.setProperty("jacorb.ssl.server_socket_factory","org.jacorb.security.ssl.sun_jsse.SSLServerSocketFactory");
        System.setProperty("jacorb.security.keystore_password","password");
        System.setProperty("jacorb.security.jsse.trustees_from_ks","on");
        System.setProperty("jacorb.security.jsse.log.verbosity","4");
        // need to add a keystore manually. it should be put into target/test-classes directory by iiop-build.xml
        final URL resource = Thread.currentThread().getContextClassLoader().getResource("iiop-test.keystore");
        System.setProperty("jacorb.security.keystore", new File(resource.toURI()).getAbsolutePath());
    }

    private ClientEjb client() throws NamingException {
        final InitialContext context = new InitialContext();
        return (ClientEjb) context.lookup("java:module/" + ClientEjb.class.getSimpleName());
    }
}
