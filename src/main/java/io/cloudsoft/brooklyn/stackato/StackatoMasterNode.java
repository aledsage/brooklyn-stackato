package io.cloudsoft.brooklyn.stackato;

import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;

@ImplementedBy(StackatoMasterNodeImpl.class)
public interface StackatoMasterNode extends StackatoNode {

    public static final AttributeSensor<String> STACKATO_ENDPOINT = StackatoDeployment.STACKATO_ENDPOINT;
    public static final AttributeSensor<String> MASTER_INTERNAL_IP = StackatoDeployment.MASTER_INTERNAL_IP;
    public static final AttributeSensor<Boolean> MASTER_UP = StackatoDeployment.MASTER_UP;
    
    public void becomeDesiredStackatoRole();
    
    public void addLicensedUser();
}
