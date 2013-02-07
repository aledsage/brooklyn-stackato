package io.cloudsoft.brooklyn.stackato;

import java.util.Arrays;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;

public class StackatoDeaNodeImpl extends StackatoNodeImpl implements StackatoDeaNode {

    public StackatoDeaNodeImpl() {
    }
    
    public StackatoDeaNodeImpl(Entity parent) {
        this(new MutableMap(), parent);
    }
    
    public StackatoDeaNodeImpl(Map flags, Entity parent) {
        super(flags, parent);
    }
    
    @Override
    public void postConstruct() {
    	super.postConstruct();
    	
        setConfig(STACKATO_NODE_ROLES, Arrays.asList("dea"));
    }
    
    public void blockUntilReadyToLaunch() {
        // DEA nodes must block until the master comes up
    	try {
	        DependentConfiguration.waitForTask(
	                DependentConfiguration.attributeWhenReady(getStackatoDeployment(), StackatoDeployment.MASTER_UP), this);
    	} catch (InterruptedException e) {
    		throw Exceptions.propagate(e);
    	}
    }

}
