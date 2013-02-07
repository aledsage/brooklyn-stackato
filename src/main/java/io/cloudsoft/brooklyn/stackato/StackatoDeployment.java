package io.cloudsoft.brooklyn.stackato;

import brooklyn.entity.Entity;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(StackatoDeploymentImpl.class)
public interface StackatoDeployment extends Entity, Startable, StackatoConfigKeys {

    @SetFromFlag("initialNumDeas")
    public static final BasicConfigKey<Integer> INITIAL_SIZE = new BasicConfigKey<Integer>(DynamicCluster.INITIAL_SIZE, 2);
    
    public static final AttributeSensor<String> MASTER_PUBLIC_IP = new BasicAttributeSensor<String>(String.class, "stackato.master.hostname.canonical", "Public IP of master (supplied by cloud provider)");
    public static final AttributeSensor<String> MASTER_INTERNAL_IP = new BasicAttributeSensor<String>(String.class, "stackato.master.ip.internal");
    public static final AttributeSensor<Boolean> MASTER_UP = new BasicAttributeSensor<Boolean>(Boolean.class, "stackato.master.up", "announces that the master is up");
    
    public static final AttributeSensor<String> STACKATO_ENDPOINT = new BasicAttributeSensor<String>(String.class, "stackato.endpoint", "Hostname to use as endpoint (e.g. api.my1.example.com)");
    
    public static final AttributeSensor<String> STACKATO_MGMT_CONSOLE_URL = new BasicAttributeSensor<String>(String.class, "stackato.mgmt.url", "URL for management console");
    
    public void addMaster();

    public void addDeaCluster();
    
    // for now Geoscaling is the only one supported; this could be abstracted however, all we need is editSubdomainRecord
    public void useDnsClient(GeoscalingWebClient dnsClient);
}
