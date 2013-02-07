package io.cloudsoft.brooklyn.stackato;

import java.util.List;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(StackatoNodeImpl.class)
public interface StackatoNode extends SoftwareProcess, StackatoConfigKeys {

    @SetFromFlag("roles")
    public static BasicConfigKey<List> STACKATO_NODE_ROLES = new BasicConfigKey<List>(List.class, "stackato.node.roles", "a list of strings to set as the cluster roles");

    public static BasicConfigKey<List> STACKATO_OPTIONS_FOR_BECOME = new BasicConfigKey<List>(List.class, "stackato.node.become.options", 
            "a list of arguments to pass to 'stackato-admin become'");

    @SetFromFlag("masterIp")
    public static BasicConfigKey<String> MASTER_IP_ADDRESS = new BasicConfigKey<String>(String.class, "stackato.master.hostname.override", 
            "optional, IP or hostname to use for master (auto-discovered if not specified)");

    public void addOptionForBecome(String first, Object ...others);

    public void addOptionForBecomeIfNotPresent(String first, Object ...others);

    public String getApiEndpoint();

    public void becomeDesiredStackatoRole();
    
    public void blockUntilReadyToLaunch();
}
