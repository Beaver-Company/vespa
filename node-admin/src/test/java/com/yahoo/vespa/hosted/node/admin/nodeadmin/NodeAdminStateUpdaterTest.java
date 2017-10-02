// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Basic test of NodeAdminStateUpdater
 *
 * @author freva
 */
public class NodeAdminStateUpdaterTest {
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final Orchestrator orchestrator = mock(Orchestrator.class);
    private final StorageMaintainer storageMaintainer = mock(StorageMaintainer.class);
    private final NodeAdmin nodeAdmin = mock(NodeAdmin.class);
    private final String parentHostname = "basehost1.test.yahoo.com";
    private final ManualClock clock = new ManualClock();
    private final Duration convergeStateInterval = Duration.ofSeconds(30);

    private final NodeAdminStateUpdater refresher = spy(new NodeAdminStateUpdater(
            nodeRepository, orchestrator, storageMaintainer, nodeAdmin, parentHostname, clock, convergeStateInterval, null));


    @Test
    public void testStateConvergence() throws IOException {
        List<ContainerNodeSpec> containersToRun = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            containersToRun.add(
                    new ContainerNodeSpec.Builder()
                            .hostname("host" + i + ".test.yahoo.com")
                            .nodeState(i % 3 == 0 ? Node.State.active : Node.State.ready)
                            .nodeType("tenant")
                            .nodeFlavor("docker")
                            .minCpuCores(1)
                            .minMainMemoryAvailableGb(1)
                            .minDiskAvailableGb(1)
                            .build());
        }
        List<String> activeHostnames = Arrays.asList(
                "host0.test.yahoo.com", "host3.test.yahoo.com", "host6.test.yahoo.com", "host9.test.yahoo.com");
        List<String> suspendHostnames = new ArrayList<>(activeHostnames);
        suspendHostnames.add(parentHostname);

        when(nodeRepository.getContainersToRun(eq(parentHostname))).thenReturn(containersToRun);

        // Initially everything is frozen to force convergence
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED));
        when(nodeAdmin.setFrozen(eq(false))).thenReturn(true);
        doNothing().when(orchestrator).resume(parentHostname);
        tickAfter(0); // The first tick should unfreeze
        verify(orchestrator, times(1)).resume(parentHostname); // Resume host
        verify(orchestrator, times(1)).resume(parentHostname);

        // Everything is running and we want to continue running, therefore we have converged
        assertTrue(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED));
        tickAfter(35);
        tickAfter(35);
        assertTrue(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED));
        verify(refresher, never()).signalWorkToBeDone(); // No attempt in changing state
        verify(orchestrator, times(1)).resume(parentHostname); // Already resumed

        // Lets try to suspend node admin only, immediately we get false back, and need to wait until next
        // tick before any change can happen
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN));
        verify(refresher, times(1)).signalWorkToBeDone();
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN)); // Still no change
        verify(refresher, times(1)).signalWorkToBeDone(); // We already notified of work, dont need to do it again

        when(nodeAdmin.setFrozen(eq(true))).thenReturn(false);
        when(nodeAdmin.subsystemFreezeDuration()).thenReturn(Duration.ofSeconds(1));
        tickAfter(0);
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN));
        verify(refresher, times(1)).signalWorkToBeDone(); // No change in desired state

        // First orchestration failure happens within the freeze convergence timeout,
        // and so should not call setFrozen(false)
        verify(nodeAdmin, times(1)).setFrozen(eq(false));
        when(nodeAdmin.setFrozen(eq(true))).thenReturn(true);
        when(nodeAdmin.subsystemFreezeDuration()).thenReturn(Duration.ofSeconds(1));
        doThrow(new RuntimeException("Cannot allow to suspend because some reason"))
                .when(orchestrator).suspend(eq(parentHostname));
        tickAfter(35);
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN));
        verify(refresher, times(1)).signalWorkToBeDone();
        verify(nodeAdmin, times(1)).setFrozen(eq(false));

        // The second orchestration failure happens after the freeze convergence timeout,
        // and so SHOULD call setFrozen(false)
        when(nodeAdmin.setFrozen(eq(true))).thenReturn(true);
        when(nodeAdmin.subsystemFreezeDuration()).thenReturn(NodeAdminStateUpdater.FREEZE_CONVERGENCE_TIMEOUT.plusMinutes(1));
        doThrow(new RuntimeException("Cannot allow to suspend because some reason")).doNothing()
                .when(orchestrator).suspend(eq(parentHostname));
        tickAfter(35);
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN));
        verify(refresher, times(1)).signalWorkToBeDone();
        verify(nodeAdmin, times(2)).setFrozen(eq(false)); // +1, since freeze convergence have timed out

        tickAfter(35);
        assertTrue(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN));
        verify(nodeAdmin, times(2)).setFrozen(eq(false));

        // At this point orchestrator will say its OK to suspend, but something goes wrong when we try to stop services
        verify(orchestrator, times(0)).suspend(eq(parentHostname), eq(suspendHostnames));
        doThrow(new RuntimeException("Failed to stop services")).doNothing().when(nodeAdmin).stopNodeAgentServices(eq(activeHostnames));
        when(nodeAdmin.subsystemFreezeDuration()).thenReturn(Duration.ofSeconds(1));
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED));
        tickAfter(0); // Change in wanted state, no need to wait
        verify(orchestrator, times(1)).suspend(eq(parentHostname), eq(suspendHostnames));
        verify(refresher, times(2)).signalWorkToBeDone(); // No change in desired state
        // Make sure we dont roll back if we fail to stop services - we will try to stop again next tick
        verify(nodeAdmin, times(2)).setFrozen(eq(false));

        // Finally we are successful in transitioning to frozen
        tickAfter(35);
        assertTrue(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED));

        // We are in desired state, no changes will happen
        reset(nodeAdmin);
        tickAfter(35);
        tickAfter(35);
        assertTrue(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED));
        verify(refresher, times(2)).signalWorkToBeDone(); // No change in desired state
        verifyNoMoreInteractions(nodeAdmin);
        
        // Lets try going back to resumed
        when(nodeAdmin.setFrozen(eq(false))).thenReturn(false).thenReturn(true); // NodeAgents not converged to yet
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED));
        tickAfter(35);
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED));

        doThrow(new OrchestratorException("Cannot allow to suspend " + parentHostname)).doNothing()
                .when(orchestrator).resume(parentHostname);
        tickAfter(35);
        assertFalse(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED));
        tickAfter(35);
        assertTrue(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED));
    }

    private void tickAfter(int seconds) {
        clock.advance(Duration.ofSeconds(seconds));
        refresher.tick();
    }
}
