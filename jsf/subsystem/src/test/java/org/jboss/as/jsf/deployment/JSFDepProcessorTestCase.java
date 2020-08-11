/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.jsf.deployment;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@PowerMockIgnore("jdk.internal.reflect.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({JsfVersionMarker.class, JSFModuleIdFactory.class})
public class JSFDepProcessorTestCase {
    private static AttachmentKey<String> VERSION_KEY = AttachmentKey.create(String.class);
    public static final String NONE = "NONE";
    JSFDependencyProcessor dependencyProcessor;
    DeploymentPhaseContext phaseContext;
    JSFModuleIdFactory moduleIdFactory;
    DeploymentUnit deploymentUnit;
    ModuleSpecification moduleSpecification;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(JsfVersionMarker.class);
        PowerMockito.mockStatic(JSFModuleIdFactory.class);
        phaseContext = mock(DeploymentPhaseContext.class);
        moduleIdFactory = mock(JSFModuleIdFactory.class);
        deploymentUnit = mock(DeploymentUnit.class);
        moduleSpecification = mock(ModuleSpecification.class);
        dependencyProcessor = new JSFDependencyProcessor();

    }

    @Test(expected = DeploymentUnitProcessingException.class)
    public void deployTest() throws DeploymentUnitProcessingException {
        when(phaseContext.getDeploymentUnit()).thenReturn(deploymentUnit);
        when(deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION)).thenReturn(moduleSpecification);
        when(JsfVersionMarker.getVersion(deploymentUnit)).thenReturn(NONE);
        when(deploymentUnit.getAttachment(VERSION_KEY)).thenReturn(NONE);
        when(JsfVersionMarker.isJsfDisabled(deploymentUnit)).thenReturn(true);
        when(moduleIdFactory.getApiModId("xyz.0.0")).thenReturn(null);
        when(moduleIdFactory.isValidJSFSlot("xyz.0.0")).thenReturn(false);
        when(JSFModuleIdFactory.getInstance()).thenReturn(moduleIdFactory);
        when(JSFModuleIdFactory.getInstance().getDefaultSlot()).thenReturn("xyz.0.0");
        dependencyProcessor.deploy(phaseContext);
    }
}
